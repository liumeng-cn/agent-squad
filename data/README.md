# 数据设计 v0.2

[业务 DDL](schema.sql) 为 MySQL 8.4 设计快照，已替换原 27 表草稿；未执行，不是应用迁移。原生 AgentScope 表不复制成 sq_step/sq_checkpoint/sq_run。

## 业务表归属

| 模块 | 表 | 唯一职责 |
| --- | --- | --- |
| M01 | sq_account / sq_auth_session / sq_conversation | 身份、Cookie 会话、对话归属与删除代数 |
| M02 | sq_goal_task / sq_runtime_binding / sq_command | 用户目标生命周期、发现目录与 guard、桥接命令及回执 |
| M03 | sq_plan | 不可变协作 DAG 与汇合标准 |
| M04 | sq_worker_job / sq_worker_definition | 持久局部目标与版本化能力；不保存模型循环 |
| M05 | sq_context_snapshot / sq_memory | 固定授权输入、显式长期记忆 |
| M06 | sq_skill_version / sq_skill_grant | 不可变技能文本与个人可见范围 |
| M07 | sq_connection / sq_operation / sq_interaction | 凭据引用、外部事实、输入与审批 |
| M08 | sq_artifact / sq_task_event / sq_projection_receipt / sq_budget_reservation / sq_audit | 文件、公开日志、原生投影幂等、预算和审计 |

框架内部会话/journal/inbox/Workspace 由选定标签的 JDBC/共享存储适配管理。平台只保存绑定及导出水位，不随意改原生 head/epoch/checkpoint。固定标签的基础结构已提取为[框架存储映射](framework-storage.md)与[三方结构参考](framework-schema.sql)；尚未与实际制品编译和执行验证，不能当作已完成迁移。

## ID、约束与 JSON

统一存储主键 BIGINT，自有业务 ID 为 CHAR(36)，公共审计列及表/列中文注释。owner 参与关键复合唯一键/FK，RESTRICT 禁止无意级联删除。del_flag 不参与业务幂等键；删除后同一命令/业务键仍不得重复产生副作用。

复杂 JSON 对应契约：input_json→TaskCreate；profile_json→BudgetProfile；definition_json→PlanPublish 或 WorkerDefinitionCreate（按表）；snapshot_json→ContextSnapshot；package_json→SkillImport；result_candidate_json/result_json→JobResult；completion_proposal_json→CompletionProposal；payload_json→CommandPayload 或事件目录；response_json→InteractionAnswer。运行配置摘要对应受控发布配置，不由模型随意构造。

字段级枚举/状态转移由领域服务校验；数据库只提供基本类型、唯一键、关键归属关系和删除标志 CHECK，不能把 DDL 通过当作状态机正确。

部分跨实体关系没有物理 FK：Conversation.activeTaskId 的循环引用；Command 的多态目标；Artifact 的预上传可空目标；Audit 中删除后仍需保留的来源。全部必须由同 owner、同 Task/Plan 的应用校验负责，清理前验证运行引用。没有 FK 不能视为没有约束。

框架原生 ID 不保证 UUID，采用有长度上限的不透明 VARCHAR。JSON 内嵌 artifactId 等引用仍需逐项授权检查，外键不会检查 JSON。

## 事务与锁序

1. 通用顺序 Conversation → GoalTask → Plan/Job → Operation/Interaction → Command/Event；只锁本次需要的集合，节点按稳定 ID 排序。
2. 受理：锁 Conversation，唯一幂等 Command，Task/Binding/Command/Event 与 activeTask 同事务。
3. 发布：锁 Task，校验 expectedPlanVersion，写 Plan/Jobs/Command/Event；ContextSnapshot 提前准备，提交时再次验证归属与版本。
4. 结果：检查原生持久完成证据，锁 Task/Job，写结果/依赖就绪/新信号 Command/事件；NativeReceipt 同事务去重。
5. 执行：先持久 PREPARED，再独立 gate 提交 IN_FLIGHT，再网络；结果与后续命令另一个事务。不声称外部副作用与数据库原子。
6. 预算：锁 Task 后汇总有效 Reservation 并比较冻结上限，插入稳定 usageKey；结算使用版本与可信计量，同键幂等。
7. 删除：写代数/取消意图后异步清理，不在单个长事务删除全体文件或原生日志。

MyBatis XML 应显式呈现锁/CAS 条件。生产隔离级别建议 READ COMMITTED；快照读取采用一致水位投影或 REPEATABLE READ 的短只读事务，避免不同水位拼装。死锁可有界重试纯数据库事务；含外部发送的步骤不能套通用重试。

## 发布与清理

Flyway 统一迁移，业务表和框架表可用独立前缀/库但需相同备份边界；业务账号不授予 DDL，禁用框架自动建表。schema.sql 是评审快照，实施时生成首个基线迁移，后续增量与快照同步。

公开事件清理要保留可用快照和基准游标；运行中对象、UNKNOWN 证据、审批原文和幂等键按其生命周期保留。归档后恢复不能把旧命令重新视为新请求。框架表、对象存储与业务表备份不一致时先保守核对，不直接恢复写入。

Account.roles_json为服务端角色来源；业务权限变化递增auth_version。框架ID长度适配和页面所需只读字段见开发准备，不在业务表保存Secret明文或UI缓存状态。
