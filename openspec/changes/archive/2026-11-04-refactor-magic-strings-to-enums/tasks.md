# Tasks

## 1. 前置依赖：确保 common 模块可用

- [x] 1.1 确认 `agent-squad-common` 模块已创建，且包含 `AccountRole`、`AccountStatus` 枚举。
- [x] 1.2 确认 `agent-squad-gateway` 与 `agent-squad-server` 已依赖 `agent-squad-common`。

## 2. 识别并枚举化目标字段

- [x] 2.1 扫描 `agent-squad-gateway` 与 `agent-squad-server` 的 DTO/request/response/row 类，列出所有角色、状态、类型等有限取值字段，输出字段清单。
- [x] 2.2 核对字段清单与 `V1__baseline.sql`、Mapper XML 中的实际存储值，确保枚举名称与数据库值一致。
- [x] 2.3 确认 `WorkerDefinitionStatus` 已存在且包含 `ENABLED`、`DISABLED`、`REVOKED`；若缺失或不全则补充。

## 3. 后端类型改造

- [x] 3.1 修改 `agent-squad-gateway` 的 `AdminUserView`、`AdminUserCreateRequest`、`AdminUserUpdateRequest`、`UserAccountRow` 等类，将 `role`、`status` 字段类型从 `String` 改为对应枚举。
- [x] 3.2 修改 `agent-squad-server` 中涉及角色/状态的 DTO、请求体、响应体，将 `String` 改为对应枚举。
- [x] 3.3 修改 Service 层中对这些字段的比较与赋值逻辑，使用枚举方法替代字符串比较。
- [x] 3.4 确认 MyBatis Mapper 接口参数类型与枚举兼容；XML 中字面量暂时保留，后续逐步收敛。
- [x] 3.5 运行 `mvn -pl server/agent-squad-gateway,server/agent-squad-server,server/agent-squad-common test` 验证编译与既有测试通过。

## 4. 契约与前端同步

- [x] 4.1 更新 `api/openapi.json`，将 `AccountRole`、`AccountStatus`、`WorkerDefinitionStatus` 等字段的 schema 从 `string` 改为 `enum`。
- [x] 4.2 更新 `web/workbench/src/shared/api/types.ts`，将对应字段从 `string` 改为枚举联合类型。
- [x] 4.3 在前端搜索并修复因类型变更导致的编译错误（如硬编码比较）。

## 5. 规范补充与验收

- [x] 5.1 在 `AGENTS.md` 增加条目：多状态变量、角色、类型等有限取值优先使用枚举；业务代码禁止直接比较或拼接魔法字符串。
- [x] 5.2 新增或调整 HTTP 测试，验证传入非法枚举值时接口返回 400。
- [x] 5.3 运行完整 Maven 构建与前端 `npm run type-check`（或等效命令）验证无类型错误。

## Workflow follow-up

- 全部任务完成后提交代码并归档本变更。
