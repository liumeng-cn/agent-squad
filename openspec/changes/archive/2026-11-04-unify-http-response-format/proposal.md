# Proposal

## Why

当前部分接口直接返回业务 DTO，错误响应则使用 `Map<String, String>`（如 `{"message": "..."}`），前端需要分别处理成功/失败两种结构。统一使用泛型包装类承载业务状态码、数据内容与提示消息，可以简化前端拦截器逻辑、统一日志与监控字段，并降低新增接口时的约定成本。

## What Changes

- 在 `agent-squad-common` 中定义统一响应包装类 `ApiResult<T>`，字段为 `code`（int，业务状态码）、`data`（泛型载荷）、`msg`（可读提示），并定义 `ApiCode` 枚举承载码值与默认提示。
- 逐个改造 Controller 方法，显式返回 `ApiResult.success(data)`；不使用 `ResponseBodyAdvice`，避免隐式包装遗漏与重复包装。
- 改造 `ApiExceptionHandler`，将异常响应统一包装为 `ApiResult<T>`，并在必要时保留原 HTTP 状态码（如 400/401/403/404/409/500）。
- 同步改造 `agent-squad-gateway` 的 `GatewayExceptionHandler` 与控制器返回类型，使网关与业务服务对外响应结构一致。
- 更新 `api/openapi.json` 与 `web/workbench/src/shared/api/types.ts`，所有响应 schema 统一引用 `ApiResult<T>`。
- 前端 API 层增加统一拦截，自动读取 `code`/`message`/`data`。

## Capabilities

### New Capabilities
- `http-response-format`：所有接口统一返回 `ApiResult<T>` 结构；业务成功时 `code` 为约定成功码 0，`data` 为实际载荷；业务失败或系统异常时返回对应 `code` 与 `message`。

### Modified Capabilities
- 无现有能力变更。

## Impact

- 所有 Controller 返回值与异常处理类。
- 全部 HTTP 集成测试断言需要调整响应体结构。
- 前端请求/响应拦截器、类型定义、错误提示逻辑。
- OpenAPI 与 API 文档所有响应 schema。
