/**
 * 生成调用方稳定的幂等键；一次发送尝试内保持不变，成功或放弃后丢弃。
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}
