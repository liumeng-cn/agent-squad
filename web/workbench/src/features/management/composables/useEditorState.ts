import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { confirmManagement } from '../components/confirmManagement'

/**
 * 管理页的异步操作状态：统一维护忙碌、错误和成功提示，避免每个按钮重复 try/catch。
 */
export function useAsyncAction() {
  const busy = ref(false)
  const error = ref('')
  const notice = ref('')

  /** 执行一次操作；失败时记录错误并返回 undefined，成功时可附带提示。 */
  async function run<T>(action: () => Promise<T>, success?: string): Promise<T | undefined> {
    busy.value = true
    error.value = ''
    notice.value = ''
    try {
      const result = await action()
      if (success) {
        notice.value = success
      }
      return result
    } catch (reason) {
      error.value = errorMessage(reason)
      return undefined
    } finally {
      busy.value = false
    }
  }

  return { busy, error, notice, run }
}

/**
 * 未保存修改保护：离开页面、切换到同一编辑页的其他资源或关闭浏览器标签前提示确认。
 */
export function useUnsavedGuard(dirty: Ref<boolean>) {
  async function confirmLeave(): Promise<boolean> {
    if (!dirty.value) {
      return true
    }
    return confirmManagement('当前修改尚未保存，离开后将丢失。确认离开？', { confirmText: '离开', danger: true })
  }

  function beforeUnload(event: BeforeUnloadEvent) {
    event.preventDefault()
  }

  watch(dirty, value => {
    if (value) {
      window.addEventListener('beforeunload', beforeUnload)
    } else {
      window.removeEventListener('beforeunload', beforeUnload)
    }
  })

  onBeforeRouteLeave(() => confirmLeave())
  onBeforeRouteUpdate((to, from) => (to.path === from.path ? true : confirmLeave()))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

  return { confirmLeave }
}

/** 记录表单基线，用 JSON 快照判断是否有未保存修改。 */
export function useSnapshot<T>(source: Ref<T>) {
  const baseline = ref('')

  function markClean() {
    baseline.value = JSON.stringify(source.value)
  }

  function changed(): boolean {
    return JSON.stringify(source.value) !== baseline.value
  }

  return { baseline, markClean, changed }
}
