# 生产/共享库变更边界

- `V1__initial_44_tables.sql` 是多用户实施前 44 表的固定快照，用于**全新空库**基线。
- `V2__multiuser_identity.sql` 是 C1 新增四张身份表；已有存量库由 DBA 先校验 V1 表结构再执行 V2，**不要盲目重新执行 V1**。
- 应用运行时使用 `spring.sql.init.mode=never`；开发/隔离测试环境的全量 `V1__baseline.sql` 与 V1+V2 逻辑结构应保持一致。
- 本目录为可审查的 SQL 文件，不代表已集成 Flyway CLI 或已执行真实迁移。
- 不允许自动删除/重建既有数据库；部署前必须备份、审查、人工批准和验证账号数据映射。
- `V3__runtime_admission.sql` 新增两张原生运行容量表；启用 `SQUAD_ADMISSION_ENABLED=true` 前必须由 DBA 在隔离环境核验 V3 迁移和计数一致性。
- 历史已发布的 V1/V2 不变；后续 schema 变更继续采用新的不可变增量 SQL。
- 生产配置默认关闭运行占位功能；启用前应先处理在途 SDK 原生运行，避免没有被计入新配额的老任务产生并发超配。
