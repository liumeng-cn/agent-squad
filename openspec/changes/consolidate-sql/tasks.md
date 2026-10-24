# Tasks

## 1. 合并迁移为单一 baseline

- [x] 1.1 汇总 V1–V13 全部表/索引/约束/中文注释，生成 `V1__baseline.sql`（含 V4 实际框架表 agentscope_*，删除死表 sq_native_session_object）。验证：baseline 表清单等于 V1–V13 合并清单减去 sq_native_session_object，缺表/缺索引即失败；全仓 grep `sq_native_session_object` 无 Java 引用。
- [x] 1.2 为 7 张缺审计字段的业务表补齐统一审计口径：`sq_conversation`/`sq_goal_task`（补 5 字段）、`sq_runtime_binding`/`sq_command`/`sq_secret_vault`/`sq_mock_ticket`（补 6 字段）、`sq_task_event`（补 7 字段），字段注释与 `sq_connection` 口径一致。验证：12 张业务表审计字段口径逐列一致。
- [x] 1.3 删除 V2–V13 迁移文件，仅保留 `V1__baseline.sql`。验证：`db/migration/` 下只剩 `V1__baseline.sql`。

## 2. 同步设计快照

- [x] 2.1 对齐 12 张已建业务表（审计字段齐全），并更新 `api/design-manifest.json`（modules 表清单收敛 + plannedBusinessTableCount=12）。验证：`python3 tools/check_design.py` 通过，业务表与 baseline 一致。
- [x] 2.2 确认实际框架表（agentscope_*）已并入 baseline，`data/framework-schema.sql`（as_ 前缀参考）保持不动。验证：baseline 含 agentscope_store/agentscope_sessions/agentscope_snapshots，framework-schema.sql 无业务表重复。

## 3. 重建开发库并验证

- [ ] 3.1 drop 并重建开发库 `agent_squad`（agent-squad-mysql 容器），启动服务让 Flyway 应用 `V1__baseline.sql`。验证：Flyway 成功、无校验和错误，`sq_operation`/`sq_connection`/`sq_mock_ticket`/`sq_secret_vault` 存在且含新增审计字段。
- [ ] 3.2 运行 `mvn -f server/pom.xml -B -ntp test` 确认 H2 测试库由 baseline 重建通过。验证：测试通过（除按设计跳过的 MySQL 集成测试）。

## Workflow follow-up

- 归档本 change 前按项目评审要求复核。
- 复核归档结果。
