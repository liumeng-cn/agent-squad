# Design

## Context

项目中已存在部分枚举（如 `GoalTaskStatus`、`RuntimeCommandStatus`、`WorkerDefinitionStatus`），但跨模块字段（账号角色、账号状态）以及部分 DTO 仍使用 `String`。MyBatis Mapper XML 中也存在大量状态字面量。参见 `proposal.md` 与 `specs/api-contract-enums/spec.md`。

## Goals / Non-Goals

**Goals：**
- 将账号角色、账号状态以及 Worker 定义状态等有限取值字段改为枚举类型。
- 保证数据库存储值与 JSON 序列化值不变。
- 更新 OpenAPI 与前端类型，使契约与后端一致。
- 在 `AGENTS.md` 补充枚举优先规范。

**Non-Goals：**
- 不一次性将 Mapper XML 中所有状态字面量替换为枚举引用（涉及面过大，后续迭代逐步收敛）。
- 不引入通用枚举框架或代码生成器。
- 不改写仅内部使用的临时字符串常量（如日志标签）。

## Decisions

### 跨模块枚举先下沉到 `agent-squad-common`
- **理由**：`AccountRole` 与 `AccountStatus` 同时被 gateway 与 server（以及未来可能的前端类型生成）引用，放在 common 可避免重复定义。
- **替代方案**：在 gateway 与 server 分别定义。不采纳，会导致类型不兼容与重复代码。

### 模块内枚举保留在原模块
- **理由**：`GoalTaskStatus` 等业务状态仅在 server 中使用，迁移到 common 会扩大 common 职责并引入不必要的模块耦合。
- **替代方案**：全部枚举集中到 common。不采纳，common 应保持最小化。

### 使用 Java 标准枚举 + MyBatis 默认类型处理器
- **理由**：MyBatis 对 Java 枚举默认使用 `EnumTypeHandler`（按 `name()` 存储），与现有数据库字符串值一致，无需额外配置。
- **替代方案**：自定义 `TypeHandler` 以支持大小写不敏感或数据库值与枚举名不一致。暂不采纳，当前名称已完全匹配。

### DTO 中的 `String` 字段直接替换为枚举类型
- **理由**：Spring 在反序列化时会自动将 JSON 字符串映射为枚举，非法值会触发 `HttpMessageNotReadableException`，复用现有的 400 处理路径。
- **替代方案**：DTO 仍用 `String`，在 Service 层手动校验。不采纳，无法利用编译期类型安全。

### OpenAPI 与 types.ts 通过脚本或手工同步
- **理由**：当前项目没有自动生成 OpenAPI/TS 的流水线，手工同步虽然繁琐但可控。
- **替代方案**：引入 openapi-generator。暂不采纳，超出本次变更范围。

## Risks / Trade-offs

- **[Risk]** 枚举名称与数据库历史数据不完全一致 → 实施前逐一核对 `V1__baseline.sql` 与 Mapper XML 中对应字段的取值。
- **[Risk]** 前端已按字符串硬编码某些状态值 → 同步更新 `types.ts` 并全局搜索相关比较逻辑。
- **[Risk]** 枚举改动影响 MyBatis 动态 SQL 中的 `test` 比较 → 将 `status = '...'` 字面量保留，先完成 Java 层改造；Mapper XML 的收敛作为后续任务。
- **[Trade-off]** 枚举化后部分 JSON 序列化框架配置可能需要显式声明；当前使用 Spring Boot 默认 Jackson，无需额外配置。

## Migration Plan

1. 在 `agent-squad-common` 中创建 `AccountRole`、`AccountStatus`。
2. 在 `agent-squad-server` 中确认 `WorkerDefinitionStatus` 已存在；若缺失则创建。
3. 修改 gateway 的 `AdminUserView`、`AdminUserCreateRequest`、`AdminUserUpdateRequest`、`UserAccountRow` 等，将角色/状态字段改为枚举。
4. 修改 server 中引用角色/状态的 DTO、Service 与 Mapper 接口。
5. 更新 `api/openapi.json` 中对应字段的 schema 为 enum。
6. 更新 `web/workbench/src/shared/api/types.ts`。
7. 运行相关 HTTP 测试验证非法值返回 400。
