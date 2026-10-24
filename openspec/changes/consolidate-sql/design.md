# Design

## Context

数据库结构由 13 个递增 Flyway 迁移（V1–V13）承载，设计快照 `data/schema.sql` 与实际迁移已分叉。统一审计字段口径为 7 字段（creator_id/creator_name/create_time/last_updater_id/last_updater_name/last_update_time/del_flag），`sq_secret_vault` 与 `sq_mock_ticket` 只保留 create_time、缺失其余 6 字段；`sq_native_session_object`（V2 创建）已无代码引用。另需区分：V4 是实际应用的框架表（`agentscope_*`），而 `data/framework-schema.sql` 是 SDK 固定标签的 `as_` 前缀结构参考（标注「未执行」），二者不同。详见 proposal.md - Why。

## Goals / Non-Goals

**Goals:**
- 单一 `V1__baseline.sql` 成为数据库结构的唯一权威来源。
- `data/schema.sql` 与 baseline 结构一致。
- 开发库可由 Flyway 干净重建。

**Non-Goals:**
- 不改动任何 R01–R12 行为或 OpenAPI 契约。
- 不新增/修改业务表（除补齐审计字段与删死表）。
- 不处理生产库迁移（当前无生产库）。
- 不改动 `data/framework-schema.sql` 这一 SDK 结构参考。

## Decisions

### 单一 baseline（合并 V1–V13）
- 选择：把 V1–V13 合并为 `V1__baseline.sql`，删除其余迁移文件。
- 理由：开发阶段无生产迁移历史需保留；13 个递增迁移已与设计快照分叉，合并后单一权威、消除漂移。
- 替代：保留 V1–V13 + 新增 V14 补审计字段（更贴合生产迁移纪律，但保留死表与分叉，审计缺口需额外迁移）。未采用。

### 审计字段口径（彻底收敛后扩为 7 张表）
- 选择：采用既有 7 字段统一口径，为所有缺审计字段的已建业务表补齐：`sq_conversation`/`sq_goal_task`（补 5）、`sq_runtime_binding`/`sq_command`/`sq_secret_vault`/`sq_mock_ticket`（补 6）、`sq_task_event`（补 7）。
- 理由：设计快照已对全部业务表声明审计字段，但实际迁移仅 D2+ 表实现；彻底收敛要求 as-built 与设计口径一致，符合「公共审计字段统一」约定。
- 替代：只补 2 张（口径不统一，未采用）。

### 死表清理
- 选择：删除 `sq_native_session_object`（代码 0 引用，V2 遗留）。
- 理由：新代码不再读写，保留会造成误导。
- 替代：保留（占空间、误导）。未采用。

### schema.sql 同步与框架表归属
- 选择：`data/schema.sql` 与 `V1__baseline.sql` 的业务表逐表对齐；V4 实际框架表（`agentscope_store`/`agentscope_sessions`/`agentscope_snapshots`）并入 baseline；`data/framework-schema.sql`（`as_` 前缀 SDK 参考）保持不动。
- 理由：baseline 是应用实际应用的权威结构（含实际框架表）；framework-schema.sql 是 SDK 固定标签参考快照，不参与 Flyway，不应并入。

### 开发库重建方式
- 选择：drop 重建 `agent_squad` 库 + Flyway 应用 baseline（开发数据可弃，用户已确认）。
- 替代：`baseline-on-migrate` 保留旧数据（开发阶段无价值，且历史结构有缺口）。未采用。

## Risks / Trade-offs

- [合并时漏表/漏索引] → 任务 1.1 以 V1–V13 合并清单为基准 diff 校验，缺项即失败。
- [删死表误删被引用表] → 已 grep 确认 0 引用；实施时再次全仓 grep `sq_native_session_object` 复核。
- [H2 测试库与 MySQL 方言差异] → 沿用 V1–V13 已拆分的单条 ADD COLUMN/约束风格，baseline 保持 H2/MySQL 双兼容。
- [schema.sql 与 baseline 漂移] → 任务 2.1 逐表对齐并用 `tools/check_design.py` 校验。

## Migration Plan

1. 停止本机 Java 服务（若有运行实例）。
2. drop 并重建 `agent_squad` 库。
3. 启动服务，Flyway 应用 `V1__baseline.sql`。
4. 验证：`mvn -f server/pom.xml -B -ntp test`（H2）+ MySQL 端到端受理链路。
- 回滚：开发库可弃，回退本次 commit 并重建即可，无需数据迁移回滚。
