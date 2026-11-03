/**
 * CSRF 令牌内存持有器；登录/探测会话后由认证仓库写入，页面刷新即失效，不落地存储。
 */
let csrfToken: string | null = null

export function setCsrfToken(token: string | null): void {
  csrfToken = token
}

export function getCsrfToken(): string | null {
  return csrfToken
}

export function clearCsrfToken(): void {
  csrfToken = null
}
