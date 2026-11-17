/**
 * 后端公开 DTO 的 TypeScript 镜像；字段名与 api/openapi.json 保持一致。
 */

export type ApiCode = 0 | 400 | 401 | 403 | 404 | 405 | 409 | 410 | 415 | 422 | 429 | 500 | 503

export interface ApiResult<T> {
  code: ApiCode
  data: T
  msg: string
}
