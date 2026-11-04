# Tasks

## 1. 前置依赖：确保 common 模块可用

- [x] 1.1 确认 `agent-squad-common` 模块已创建，且包含 `ApiResult<T>` 与 `ApiCode`。
- [x] 1.2 确认 `agent-squad-server` 与 `agent-squad-gateway` 已依赖 `agent-squad-common`。

## 2. 后端统一响应包装

- [x] 2.1 逐个改造 `agent-squad-server` Controller 方法，正常返回值显式包装为 `ApiResult.success(data)`；不使用 `ResponseBodyAdvice`。
- [x] 2.2 保持 SSE 与文件下载端点返回原始结构，并在约定中明确例外；无内容端点改为返回 `ApiResult<Void>`。
- [x] 2.3 改造 `ApiExceptionHandler`，将 `unauthenticated`、`businessError`、`validationError`、`unexpected` 等分支统一返回 `ApiResult<Void>`，业务码取自 `ApiCode`，并保留对应 HTTP 状态码。
- [x] 2.4 同步改造 `agent-squad-gateway` 的 `GatewayExceptionHandler` 与 `AuthController`、`AdminUserController`，使网关成功与错误响应也使用 `ApiResult<T>`。
- [x] 2.5 新增或调整 HTTP 测试，验证成功响应包含 `code`、`data`、`msg`；错误响应包含 `code` 与 `msg`。

## 3. 契约与前端同步

- [x] 3.1 更新 `api/openapi.json`，所有业务响应 schema 改为 `ApiResult<T>` 包装结构，错误响应同步包装，并新增 `ApiCode` 整数枚举约束。
- [x] 3.2 更新 `web/workbench/src/shared/api/types.ts`，新增 `ApiResult<T>`、`ApiCode` 类型，并按接口返回载荷调整调用方类型。
- [x] 3.3 在 `web/workbench/src/shared/api/http.ts` 统一解包 `data`，根据 `code` 判断业务失败并使用 `msg` 提示。
- [x] 3.4 全局检查前端响应读取逻辑，业务代码只接收解包后的载荷。

## 4. 测试与文档

- [x] 4.1 调整 `agent-squad-server` 的 HTTP 集成测试断言，通过统一响应读取 `data`。
- [x] 4.2 调整 `agent-squad-gateway` 的 HTTP 集成测试断言，通过统一响应读取 `data`。
- [x] 4.3 运行前端构建与类型检查，确保无 TypeScript 错误。
- [x] 4.4 在 `AGENTS.md` 补充“HTTP 响应约定”：所有业务 JSON 接口统一返回 `ApiResult<T>`。

## Workflow follow-up

- [ ] 全部任务完成后提交代码并归档本变更。
