# Proposal

## Why

项目仍处于开发阶段，数据库结构由 13 个递增 Flyway 迁移（V1–V13）承载，设计快照 `data/schema.sql` 与实际迁移已出现分叉；其中两张 D2 表（`sq_secret_vault`、`sq_mock_ticket`）缺失统一审计字段，且 `sq_native_session_object` 为已无代码引用的死表。在进入 D3 之前，把增量迁移收敛为单一 baseline，可消除历史分叉并让设计快照与实际结构一致。

## What Changes

- 将 `server/src/main/resources/db/migration/` 下 V1–V13 合并为单一 `V1__baseline.sql`，保留全部业务表、框架表、索引与约束（含框架存储 V4 内容）。
- 为 `sq_secret_vault` 与 `sq_mock_ticket` 补齐统一审计字段：`creator_id`、`creator_name`、`last_updater_id`、`last_updater_name`、`last_update_time`、`del_flag`。
- 删除死表 `sq_native_session_object`（V2 创建，代码 0 引用）。
- 同步 `data/schema.sql`，使其与单一 baseline 一致。
- 重建开发库 `agent-squad-mysql`（drop + Flyway 重跑）验证单一 baseline。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

（无——本变更为纯数据/仓库整理，不改变 R01–R12 任何可观察行为，已在 `.openspec.yaml` 声明 `skip_specs: true`。）

## Impact

- `server/src/main/resources/db/migration/`：V1–V13 → 单一 `V1__baseline.sql`。
- `data/schema.sql`：与 baseline 对齐。
- 开发库 `agent-squad-mysql`：需 drop 重建（开发数据可弃，已确认）。
- H2 测试库：由 Flyway 单一 baseline 重建，无需额外改动。
- 不影响 Java/Vue 代码与 OpenAPI 契约。
