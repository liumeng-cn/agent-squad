# Design

## Context

当前 `agent-squad-server` 的 Controller 大多直接返回 DTO 或使用 `@ResponseStatus` 声明状态码；错误响应由 `ApiExceptionHandler` 返回 `Map<String, String>`。Gateway 也有独立的 `GatewayExceptionHandler` 与 `ErrorView`。参见 `proposal.md` 与 `specs/http-response-format/spec.md`。

## Goals / Non-Goals

**Goals：**
- 所有成功与失败响应均使用 `ApiResult<T>` 包装。
- 保持 HTTP 状态码语义（200 成功，400/401/403/404/409/500 对应错误）。
- 前端通过 `code` 判断业务状态，通过 `message` 展示错误。

**Non-Goals：**
- 不改造 AgentScope SDK 内部通信格式。
- 不修改 actuator/health/prometheus 等基础设施端点的原始返回。
- 不引入新的错误码体系，先使用“成功码 + HTTP 状态码”两级结构。

## Decisions

### `ApiResult<T>` 定义在 `agent-squad-common`
- **理由**：gateway 与 server 都需要返回相同结构，common 模块是最自然的载体。
- **替代方案**：分别在两个模块定义同名类。不采纳，会导致类型重复与维护成本。

### 逐个 Controller 方法显式包装正常响应
- **理由**：用户明确要求不使用 `ResponseBodyAdvice`；显式返回 `ApiResult.success(...)` 让契约在方法签名上可见，避免隐式包装导致的跳过、重复包装和类型擦除问题。
- **替代方案**：Spring `ResponseBodyAdvice` 自动包装。不采纳，隐式行为难排查，且用户已否决。

### 异常处理继续使用 `@ExceptionHandler`，但返回 `ApiResult<T>`
- **理由**：异常处理逻辑已经成熟，只需调整返回类型与日志字段。
- **替代方案**：在 Advice 中捕获异常并包装。不采纳，`ResponseBodyAdvice` 不处理异常，需要与 ExceptionHandler 配合。

### `ApiResult` 字段设计
- 字段：`code`（int，业务码）、`data`（T）、`msg`（String，可读提示）。
- 成功码统一为 `0`；错误码与 HTTP 状态码一致，并由 `ApiCode` 枚举集中定义码值与默认提示。
- **理由**：int 便于前端直接比较与日志聚合；枚举避免魔法数字散落，并可在需要时覆盖为业务更具体的提示。
- **替代方案**：使用字符串业务码（如 `USER_NOT_FOUND`）。不采纳，前端与 OpenAPI 约束更复杂，且与 HTTP 状态码重复。

### 不包装基础设施端点
- **理由**：`/actuator/*`、`/health` 等通常由监控系统直接解析，统一包装会破坏监控语义。
- **替代方案**：全部包装。不采纳，会导致 Prometheus 等 scraping 失败。

## Risks / Trade-offs

- **[Risk]** 新增 Controller 方法可能忘记包装 → 在代码评审与 AGENTS.md 约定中检查返回类型必须为 `ApiResult<T>`（SSE、下载除外）。
- **[Risk]** 前端已有大量直接读取 `response.data` 的代码 → 统一改造前端 API 层拦截器，业务代码逐步迁移。
- **[Risk]** 测试断言大量依赖原始响应体 → 批量调整集成测试，新增针对 `ApiResult` 结构的断言。
- **[Trade-off]** 响应体多了一层包装，传输量增加。优点是一致性；若后续发现性能敏感接口，可针对特定端点配置不包装。

## Migration Plan

1. 在 `agent-squad-common` 创建 `ApiResult<T>` 与 `ApiCode`。
2. 逐个改造 `agent-squad-server` Controller 方法，显式返回 `ApiResult.success(...)`。
3. 改造 `ApiExceptionHandler`，将各异常分支返回 `ApiResult<T>` 并设置对应 HTTP 状态码。
4. 同步改造 `agent-squad-gateway` 的 `GatewayExceptionHandler` 与控制器返回类型。
5. 更新 `api/openapi.json`，将所有响应 schema 改为 `ApiResult<T>`。
6. 更新 `web/workbench/src/shared/api/types.ts` 与请求拦截器。
7. 运行全部 HTTP 集成测试验证响应结构。
