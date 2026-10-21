/**
 * SSE 断线重建退避：1/2/4/8/15 秒，之后固定 15 秒。
 */
const BACKOFF_STEPS_MS = [1000, 2000, 4000, 8000, 15000]

export function backoffDelayMs(attempt: number): number {
  return BACKOFF_STEPS_MS[Math.min(Math.max(attempt, 0), BACKOFF_STEPS_MS.length - 1)]
}
