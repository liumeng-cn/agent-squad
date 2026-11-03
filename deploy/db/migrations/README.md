# 生产/共享库变更边界

- `V1__initial_44_tables.sql` 是多用户实施前 44 表的固定快照，用于**全新空库**基线。
- `V2__multiuser_identity.sql` 是 C1 新增四张身份表；已有存量库由 DBA 先校验 V1 表结构再执行 V2，**不要盲目重新执行 V1**。
- 应用运行时使用 `spring.sql.init.mode=never`；开发/隔离测试环境的全量 `V1__baseline.sql` 与 V1+V2 逻辑结构应保持一致。
- 本目录为可审查的 SQL 文件，不代表已集成 Flyway CLI 或已执行真实迁移。
- 不允许自动删除/重建既有数据库；部署前必须备份、审查、人工批准和验证账号数据映射。
- `V3__runtime_admission.sql` 新增两张原生运行容量表；启用 `SQUAD_ADMISSION_ENABLED=true` 前必须由 DBA 在隔离环境核验 V3 迁移和计数一致性。
- 历史已发布的 V1/V2 不变；后续 schema 变更继续采用新的不可变增量 SQL。
- 生产配置默认关闭运行占位功能；启用前应先处理在途 SDK 原生运行，避免没有被计入新配额的老任务产生并发超配。

- `V4__admission_run_identity.sql` 在 V3 后新增 `run_id` 和 `claim_cursor` 字段，历史版本不得重写。

- `V5__final_delivery.sql` 新增同源用户请求最终交付表，V1→V5与开发baseline保持结构一致。
- V5不自动推断历史交付归属。发布前必须排空旧RESPONSE/带origin的在途Task，或审计原消息、执行、Task及已发布消息后受控回填；缺少交付位的旧请求会明确阻塞，不能带着在途缺位请求直接启用新版发布链。无origin的历史独立submit Task保留原有Task消息身份语义。
- 受控回填必须保证同一代际一个所有者、已发布事实绑定实际messageId，不能自动从两份历史正文中任选一份掩盖异常。历史数据审计与回填是独立发布门槛，未执行真实生产库操作。

## 本地完整重建

`../local-rebuild.sql` 从最终 `V1__baseline.sql` 整理，包含51张表的删除/建表及admin、demo的BCrypt测试账号。该脚本只供用户手动重建本地验证库，不属于生产迁移链；不能在生产/共享库执行，也不由应用自动运行。
