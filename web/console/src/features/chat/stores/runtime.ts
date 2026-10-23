import { defineStore } from 'pinia'
import { createTaskApi } from '../../../shared/api/conversationsApi'
import { ApiError, errorMessage } from '../../../shared/api/http'
import { cancelTaskApi, getTaskSnapshotApi, resumeTaskApi } from '../../../shared/api/tasksApi'
import type { TaskEvent, TaskSnapshot } from '../../../shared/api/types'
import { backoffDelayMs } from '../../../shared/lib/backoff'
import { newIdempotencyKey } from '../../../shared/lib/idempotency'
import { canResume, canStop, isTerminal } from '../../../shared/lib/taskStatus'
import { TaskEventStream } from '../../../shared/sse/TaskEventStream'

/**
 * 当前目标运行视图：快照为准、事件增量触发刷新、SSE 断线按快照游标重建。
 * 终态一律以服务端状态裁决，本地不维护状态机。
 */
let stream: TaskEventStream | null = null
let reconnectTimer: number | null = null
let reconnectAttempt = 0
let disposed = false

function seqOf(cursor: string): number {
  const separator = cursor.lastIndexOf(':')
  return separator < 0 ? 0 : Number(cursor.slice(separator + 1))
}

interface RuntimeState {
  taskId: string | null
  snapshot: TaskSnapshot | null
  events: TaskEvent[]
  maxSeq: number
  /** 详情页回放模式：从 0 重收事件，追平快照游标后停止订阅。 */
  replay: boolean
  streaming: boolean
  submitting: boolean
  stopping: boolean
  resuming: boolean
  submitError: string | null
  streamError: string | null
  /** 202 后快照尚未返回前的本地目标回显。 */
  goalEcho: string | null
  /** 发送尝试的幂等键：失败重试沿用，输入变更后丢弃。 */
  inflightKey: string | null
}

export interface ObserveOptions {
  /** 详情页恢复时从头回放公开事件；工作台默认按快照游标续订。 */
  replayFromStart?: boolean
}

export const useRuntimeStore = defineStore('runtime', {
  state: (): RuntimeState => ({
    taskId: null,
    snapshot: null,
    events: [],
    maxSeq: 0,
    replay: false,
    streaming: false,
    submitting: false,
    stopping: false,
    resuming: false,
    submitError: null,
    streamError: null,
    goalEcho: null,
    inflightKey: null,
  }),

  getters: {
    task: (state) => state.snapshot?.task ?? null,
    status: (state) => state.snapshot?.task.status ?? null,
    terminal: (state) =>
      state.snapshot !== null && isTerminal(state.snapshot.task.status),
    answer: (state) => state.snapshot?.answer ?? null,
    goalMessage: (state) =>
      state.snapshot?.goalMessage || state.goalEcho || '',
    stoppable: (state) =>
      state.snapshot !== null && canStop(state.snapshot.task.status),
    resumable: (state) =>
      state.snapshot !== null && canResume(state.snapshot.task.status),
  },

  actions: {
    /** 订阅一个目标：清掉旧订阅，读快照，未终态则建立 SSE。 */
    async observe(taskId: string, options: ObserveOptions = {}): Promise<void> {
      const replay = options.replayFromStart === true
      if (this.taskId === taskId && this.snapshot !== null) {
        if (!replay || this.replay) {
          return
        }
        this.replay = true
        this.events = []
        this.maxSeq = 0
        if (this.terminal && seqOf(this.snapshot.cursor) === 0) {
          this.replay = false
          return
        }
        this.startStream(0)
        return
      }
      this.dispose()
      disposed = false
      this.taskId = taskId
      this.replay = replay
      try {
        await this.refreshSnapshot()
      } catch {
        this.streamError = '任务快照读取失败'
        return
      }
      if (this.replay) {
        if (this.terminal && this.maxSeq >= seqOf(this.snapshot?.cursor ?? ':0')) {
          this.replay = false
          return
        }
        this.startStream(0)
        return
      }
      this.maxSeq = Math.max(this.maxSeq, seqOf(this.snapshot?.cursor ?? ':0'))
      this.startStream()
    },

    /** 发送目标；同一次尝试超时/失败重试沿用原幂等键，输入变更后换新键。 */
    async send(conversationId: string, message: string): Promise<boolean> {
      if (this.submitting) {
        return false
      }
      const trimmed = message.trim()
      if (!trimmed) {
        return false
      }
      if (this.inflightKey === null) {
        this.inflightKey = newIdempotencyKey()
      }
      this.submitting = true
      this.submitError = null
      try {
        const receipt = await createTaskApi(conversationId, trimmed, this.inflightKey)
        this.inflightKey = null
        this.goalEcho = trimmed
        await this.observe(receipt.taskId, { replayFromStart: true })
        return true
      } catch (error) {
        if (error instanceof ApiError && error.status === 409) {
          this.submitError = error.message
          this.inflightKey = null
        } else {
          this.submitError = `${errorMessage(error)}（重试将沿用本次幂等键，不会重复创建目标）`
        }
        return false
      } finally {
        this.submitting = false
      }
    },

    /** 放弃沿用的幂等键；用户在失败后改了输入时应调用。 */
    dropInflightKey(): void {
      this.inflightKey = null
    },

    /** 停止当前目标；202 只代表受理，终态由快照/事件推进。 */
    async stop(): Promise<void> {
      const task = this.task
      if (!task || this.stopping || !canStop(task.status)) {
        return
      }
      this.stopping = true
      this.streamError = null
      try {
        await cancelTaskApi(task.taskId, task.version, newIdempotencyKey())
        await this.refreshSnapshot()
      } catch (error) {
        if (error instanceof ApiError && error.status === 409) {
          await this.refreshSnapshot().catch(() => undefined)
        } else {
          this.streamError = errorMessage(error)
        }
      } finally {
        this.stopping = false
      }
    },

    /** 恢复受限/待交互的目标；202 只代表受理，运行推进由快照与事件呈现。 */
    async resume(): Promise<void> {
      const task = this.task
      if (!task || this.resuming || !canResume(task.status)) {
        return
      }
      this.resuming = true
      this.streamError = null
      try {
        await resumeTaskApi(task.taskId, task.version, newIdempotencyKey())
        await this.refreshSnapshot()
      } catch (error) {
        if (error instanceof ApiError && error.status === 409) {
          await this.refreshSnapshot().catch(() => undefined)
        } else {
          this.streamError = errorMessage(error)
        }
      } finally {
        this.resuming = false
      }
    },

    /** 释放订阅与计时器；切会话/离开页面时调用，不取消任务本身。 */
    dispose(): void {
      disposed = true
      this.closeStream()
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer)
        reconnectTimer = null
      }
      this.taskId = null
      this.snapshot = null
      this.events = []
      this.maxSeq = 0
      this.replay = false
      this.streaming = false
      this.submitting = false
      this.stopping = false
      this.resuming = false
      this.submitError = null
      this.streamError = null
      this.goalEcho = null
      this.inflightKey = null
    },

    applyEvent(event: TaskEvent): void {
      if (this.taskId === null || event.sequence <= this.maxSeq) {
        return
      }
      this.maxSeq = event.sequence
      this.events = [...this.events, event].slice(-100)
      void this.refreshSnapshot()
    },

    async refreshSnapshot(): Promise<void> {
      if (this.taskId === null) {
        return
      }
      let snapshot: TaskSnapshot
      try {
        snapshot = await getTaskSnapshotApi(this.taskId)
      } catch (error) {
        if (error instanceof ApiError && (error.status === 401 || error.status === 404)) {
          this.streamError = error.status === 404 ? '任务不存在或已被删除' : '登录已失效'
          this.closeStream()
          this.streaming = false
        }
        throw error
      }
      this.snapshot = snapshot
      const cursorSeq = seqOf(snapshot.cursor)
      if (!this.replay) {
        this.maxSeq = Math.max(this.maxSeq, cursorSeq)
      }
      if (snapshot.goalMessage) {
        this.goalEcho = null
      }
      if (isTerminal(snapshot.task.status)) {
        if (!this.replay || this.maxSeq >= cursorSeq) {
          this.closeStream()
          this.streaming = false
          this.replay = false
        }
      }
    },

    startStream(afterSeq?: number): void {
      if (this.taskId === null || disposed || (this.terminal && !this.replay)) {
        this.streaming = false
        return
      }
      reconnectAttempt = 0
      this.closeStream()
      stream = new TaskEventStream(this.taskId, {
        onEvent: (event) => this.applyEvent(event),
        onError: () => this.scheduleReconnect(),
      })
      this.streaming = true
      stream.start(afterSeq ?? this.maxSeq)
    },

    scheduleReconnect(): void {
      if (disposed || reconnectTimer !== null) {
        return
      }
      this.streaming = false
      const delay = backoffDelayMs(reconnectAttempt)
      reconnectAttempt += 1
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = null
        void this.reconnect()
      }, delay)
    },

    /** 断线后先以普通快照请求判定登录/删除/游标，再按新游标重建 SSE。 */
    async reconnect(): Promise<void> {
      if (disposed || this.taskId === null) {
        return
      }
      try {
        await this.refreshSnapshot()
      } catch {
        this.scheduleReconnect()
        return
      }
      if (this.terminal && !this.replay || this.taskId === null) {
        return
      }
      this.startStream()
    },

    closeStream(): void {
      stream?.close()
      stream = null
    },
  },
})
