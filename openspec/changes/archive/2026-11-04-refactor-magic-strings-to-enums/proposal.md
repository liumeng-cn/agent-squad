# Proposal

## Why

当前 DTO、请求体与 MyBatis Mapper 中大量角色、状态字段使用 `String` 表示，导致非法值、拼写错误难以在编译期发现，也让 OpenAPI/前端类型只能声明为无约束字符串。将项目内必要魔法字符串改为 Java 枚举，并在 Agent 文档中补充枚举优先规范，可以强化类型安全、减少运行时校验，并改善前后端契约。

## What Changes

- 梳理项目中具有有限取值集合的角色、状态、类型字段，优先将跨模块字段（如 `AccountRole`、`AccountStatus`）下沉到 `agent-squad-common`；其余业务状态枚举保留在原模块。
- 将相关 DTO、请求体、响应体、Service 参数与返回值中的 `String` 替换为对应枚举类型。
- 为 MyBatis Mapper XML 补充枚举类型处理器或统一使用 `EnumTypeHandler`，确保数据库存储值保持不变（仍为大写字符串）。
- 更新 `api/openapi.json` 与 `web/workbench/src/shared/api/types.ts`，将对应字段声明为枚举联合类型。
- 在 `AGENTS.md` 增加规范条目：多状态变量、角色、类型等有限取值优先使用枚举，禁止在业务代码中直接比较或拼接魔法字符串。

## Capabilities

### New Capabilities
- `api-contract-enums`：HTTP 接口的有限取值字段使用枚举约束，非法值应在请求反序列化或参数校验阶段返回 400。

### Modified Capabilities
- 无现有能力变更。

## Impact

- 后端 Java 类型：DTO、Service、Mapper 接口与 XML 需要同步调整。
- 前端 TypeScript 类型：`types.ts` 与使用这些字段的组件需同步。
- OpenAPI schema：相关字段从 `string` 变为 `enum`。
- 数据库：枚举仍映射为原字符串值，无需迁移数据。
