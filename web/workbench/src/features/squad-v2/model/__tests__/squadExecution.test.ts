import { describe, expect, it } from 'vitest'
import type { SquadAgentRef, SquadMsg } from '../../../../shared/api/squadTypes'
import {
  applySquadEvent,
  createSquadExecutionState,
  getSquadAnswerText,
  getSquadTimeline,
} from '../squadExecution'

const master: SquadAgentRef = {
  agentId: 'master',
  agentInstanceId: 'master-1',
  agentName: '主协调员',
  agentType: 'MASTER',
  parentAgentInstanceId: null,
  rootAgentInstanceId: 'master-1',
  depth: 0,
}

function subagent(instanceId: string): SquadAgentRef {
  return {
    agentId: 'worker',
    agentInstanceId: instanceId,
    agentName: '资料整理员',
    agentType: 'SUBAGENT',
    parentAgentInstanceId: master.agentInstanceId,
    rootAgentInstanceId: master.agentInstanceId,
    depth: 1,
  }
}

function squadEvent(
  eventType: string,
  sequence: number,
  agent: SquadAgentRef | null = master,
  payload: Record<string, unknown> = {},
  eventId = 'event-' + sequence,
): SquadMsg {
  return {
    protocolVersion: '1',
    eventId,
    sequence,
    eventType,
    category: 'MESSAGE',
    conversationId: 'conversation-1',
    turnId: 'turn-1',
    agent,
    nativeExecution: null,
    nativeSource: null,
    relation: null,
    payload,
    timestamp: '2026-11-12T09:00:00Z',
  }
}

describe('Squad 执行事件模型', () => {
  it('同一个 Agent 定义的多个实例保持独立', () => {
    const state = createSquadExecutionState()
    applySquadEvent(state, squadEvent('AGENT_START', 1, subagent('worker-1')))
    applySquadEvent(state, squadEvent('AGENT_START', 2, subagent('worker-2')))

    expect(state.agents.size).toBe(2)
    expect(state.agents.get('worker-1')?.status).toBe('RUNNING')
    expect(state.agents.get('worker-2')?.status).toBe('RUNNING')
  })

  it('按 blockId 合并增量，并在结束事件后关闭内容块', () => {
    const state = createSquadExecutionState()
    applySquadEvent(state, squadEvent('TEXT_BLOCK_START', 1, master, { blockId: 'answer-1' }))
    applySquadEvent(state, squadEvent('TEXT_BLOCK_DELTA', 2, master, { blockId: 'answer-1', delta: 'Hello' }))
    applySquadEvent(state, squadEvent('TEXT_BLOCK_DELTA', 3, master, { blockId: 'answer-1', delta: ' world' }))
    applySquadEvent(state, squadEvent('TEXT_BLOCK_END', 4, master, { blockId: 'answer-1' }))

    expect(state.answerBlocks).toHaveLength(1)
    expect(state.answerBlocks[0]?.text).toBe('Hello world')
    expect(state.answerBlocks[0]?.completed).toBe(true)
    expect(getSquadAnswerText(state)).toBe('Hello world')
  })

  it('事件未携带 blockId 时，增量仍归属到最近开始的内容块', () => {
    const state = createSquadExecutionState()
    applySquadEvent(state, squadEvent('TEXT_BLOCK_START', 1, master))
    applySquadEvent(state, squadEvent('TEXT_BLOCK_DELTA', 2, master, { delta: '第一段' }))
    applySquadEvent(state, squadEvent('TEXT_BLOCK_DELTA', 3, master, { delta: '继续' }))
    applySquadEvent(state, squadEvent('TEXT_BLOCK_END', 4, master))

    expect(state.answerBlocks).toHaveLength(1)
    expect(state.answerBlocks[0]?.text).toBe('第一段继续')
    expect(state.answerBlocks[0]?.completed).toBe(true)
  })

  it('AGENT_RESULT 覆盖流式预览，避免最终结果被提前输出的文本吞掉', () => {
    const state = createSquadExecutionState()
    applySquadEvent(state, squadEvent('TEXT_BLOCK_DELTA', 1, master, {
      blockId: 'answer-1',
      delta: '流式预览',
    }))
    applySquadEvent(state, squadEvent('AGENT_RESULT', 2, master, {
      result: { content: [{ type: 'text', text: '最终正式结果' }] },
    }))

    expect(getSquadAnswerText(state)).toBe('最终正式结果')
    expect(state.answerBlocks[0]?.text).toBe('流式预览')
  })

  it('使用 toolCallId 关联工具调用与结果', () => {
    const state = createSquadExecutionState()
    const worker = subagent('worker-1')
    applySquadEvent(state, squadEvent('TOOL_CALL_START', 1, worker, {
      toolCallId: 'call-1',
      toolCallName: '资料检索',
      input: { query: 'AgentScope' },
    }))
    applySquadEvent(state, squadEvent('TOOL_RESULT_TEXT_DELTA', 2, worker, {
      toolCallId: 'call-1',
      delta: '找到三条资料',
    }))
    applySquadEvent(state, squadEvent('TOOL_RESULT_END', 3, worker, {
      toolCallId: 'call-1',
    }))

    const tools = getSquadTimeline(state).filter(item => item.kind === 'tool')
    expect(tools).toHaveLength(1)
    expect(tools[0]?.kind === 'tool' && tools[0].toolName).toBe('资料检索')
    expect(tools[0]?.kind === 'tool' && tools[0].outputText).toBe('找到三条资料')
    expect(tools[0]?.kind === 'tool' && tools[0].status).toBe('COMPLETED')
  })

  it('重复 eventId 不会重复追加事件', () => {
    const state = createSquadExecutionState()
    const event = squadEvent('HINT_BLOCK', 1, master, { hint: '处理中' }, 'same-id')

    expect(applySquadEvent(state, event)).toBe(true)
    expect(applySquadEvent(state, event)).toBe(false)
    expect(getSquadTimeline(state)).toHaveLength(1)
  })

  it('未知事件仍然保留原始事件，不中断执行模型', () => {
    const state = createSquadExecutionState()
    applySquadEvent(state, squadEvent('FUTURE_EVENT_V2', 1, null, { detail: 'new event' }))

    const timeline = getSquadTimeline(state)
    expect(timeline).toHaveLength(1)
    expect(timeline[0]?.kind === 'event' && timeline[0].event.eventType).toBe('FUTURE_EVENT_V2')
  })

  it('AGENT_END 只结束对应 Agent，不依赖 SSE 连接结束推断状态', () => {
    const state = createSquadExecutionState()
    const worker = subagent('worker-1')
    applySquadEvent(state, squadEvent('AGENT_START', 1, master))
    applySquadEvent(state, squadEvent('AGENT_START', 2, worker))
    applySquadEvent(state, squadEvent('AGENT_END', 3, worker))

    expect(state.agents.get('master-1')?.status).toBe('RUNNING')
    expect(state.agents.get('worker-1')?.status).toBe('ENDED')
  })
})
