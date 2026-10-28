# 对话体验改造实施证据

## 2026-10-28：1.1 消息与执行基础

完成 1.1，整体 1/16。此处是数据及传输基础，不代表消息受理、普通回复或新页面已经可用；其余任务保持未勾选。

### 实现与边界

- V7 新增消息、独立回复执行、统一 Scope、BudgetAccount、会话事件和消息附件关联，补充会话顺序/标题/最近活动字段及旧对象的 Scope 引用；V1–V6 不修改。
- V8 Java 迁移在平台事务中回填旧 Task 的原始用户消息与最新公开答案、Scope/账户和旧运行对象引用；不把内部工具事件或完成候选当正文，不重跑模型或创建 Command，不虚构历史 ResponseExecution。
- 稳定业务 ID 与来源键保证重放不重复；原始 Session、Command ID、幂等 submit key、原生 Turn/Run、水位和 Reservation 金额保持不变。账户额度由旧 Task 固定，SETTLED/RESERVED/UNKNOWN 仍是原结算事实。
- Scope 查询检查用户、会话状态/代际、Scope 和真实 owner 的逻辑删除；数据库复合外键阻止跨用户引用。
- OpenAPI 新增消息与执行 DTO schema，明确标为 foundation-dto；同步数量清单、模块表归属与需求追踪。未添加尚不可执行的新 HTTP 路径。
- 尚未切换运行归属：新旧服务不能据此无任务调用模型；旧入口新建的 Task 目前仍走旧受理链路。1.2–1.4 接入后才进入普通回复验收，实际开发数据库和当前页面本轮未升级/切换。

### 已执行验证

| 验证 | 结果 | 实际覆盖 |
| --- | --- | --- |
| H2 V6 → V8 | 通过 | 旧任务/公开答案/附件归属、顺序、scope/account 引用、预算守恒、稳定 SDK 身份、回填重放、跨用户/删除/代际拒绝 |
| H2 错误数据回滚 | 通过 | 历史 JSON 不是受支持对象时整批 V8 回滚，不遗留账户、Scope、消息或事件，UNKNOWN 原事实保留 |
| MySQL 8.4 V6 → V8 | 通过 | 专用隔离容器、全新测试库执行相同迁移与归属/预算/重放验收；未访问实际 agent_squad 开发库 |
| 后端 clean test | 40 项，0 失败/错误，1 跳过 | 39 项实际通过，包括专用 MySQL 迁移和 D3 派发、复用、恢复、取消、交互、预算回归；跳过未设置 SQUAD_DB_URL 的 MySqlGoalTaskIntegrationTest |
| 静态契约检查 | 通过 | 39 个操作、67 个 schema、24 张业务表、需求追踪、正反例及页面映射；不代表这些规划接口全已实现 |
| 新 Java 文件 IDEA 格式化 | 16/16 成功 | 仅本切片新文件，未全项目格式化 |
| OpenSpec strict / git diff --check | 通过 | 新变更规范与文本差异 |

命令：

```bash
SQUAD_MIGRATION_TEST_URL='<专用测试库>' SQUAD_MIGRATION_TEST_USER='<专用测试用户>' SQUAD_MIGRATION_TEST_PASSWORD='<专用测试密码>' \
  mvn -o -Dmaven.repo.local=/private/tmp/agentsquad-m2 -f server/pom.xml -B -ntp clean test
PYTHONPATH=/private/tmp/agentsquad-design-deps /usr/bin/python3 tools/check_design.py
openspec validate conversation-first-experience --strict
git diff --check
```

测试使用 AgentScope 2.1.0-BETA1、固定的本地模拟模型服务，不是实际供应商模型功能验收。编译产物 major version 65（Java 21），本机执行 JVM 是 Oracle Java 27；真实 Java 21 运行仍待 D6 补证。Flyway 对 MySQL 8.4 发出版本兼容提示，本次实际迁移实验通过，不等于完整数据库兼容/发布验证。

本地日志：`/private/tmp/agentsquad-conversation-full-test.log`、`/private/tmp/agentsquad-conversation-foundation-verified.log`。专用迁移容器实验后清理，实际开发 MySQL 保留。

### 下一切片

1.2 将真实运行链路改为 Scope/Account 归属，使无 GoalTask 的回复也具有预算、授权、交互、附件和 guard；完成后再开放消息受理和普通回复执行。对话界面切换在 4.1–4.4，不能提前将这次迁移称为体验验收完成。

## 2026-10-28：1.2 共享执行归属

完成 1.2，整体 2/16；1.3 消息 HTTP 受理、1.4 普通回复 SDK 生命周期与新的对话界面尚未交付。

### 实现

- 旧 Task 的正式受理现在创建 Scope/Account，Master/Worker Binding、Command、Operation、Interaction、Reservation 和局部上下文记录 Scope。普通回复绑定不要求 taskId；账户取代父 Task 行作为计费权威。
- 无 Task 的执行可以使用同一模型预算中间件、受控操作账本、审批/INPUT 和附件归属。共享账户不会按 Scope 发新额度，用户总账以持久锁串行约束累计上限；UNKNOWN 保守占额。
- 预算占用、幂等预留查询采用锁后当前读；MySQL 测试先在两个事务建立读快照，再争用总预算，只有一笔 60/100 预留通过。
- 统一会话→所有者→用户预算→账户→运行对象锁序；原生投影、核对也先锁会话与所有者。审批外部 I/O 不跨操作行锁，最后交互答复后排队执行而非直接宣称 RUNNING。
- 独立派发事务重验归属、停止与代际和未知操作屏障，提交 IN_FLIGHT 后才调用连接器。WRITE 异常保留 UNKNOWN，同会话新预检和旧 PREPARED 派发均被阻断；取消不吞掉已发生的结算。
- V9 新增用户预算互斥行和 Scope 产物关联，允许普通回复的 taskId 为空；V10 固定操作计费键，保留旧 Reservation 键，新回合重复工具 callId 不复用前一回合费用。
- 公共任务/交互/操作/文件查询重验 Scope，删除资源与失效代际禁读；内部保留任务终态和审计事实。核对继续要求 OPERATOR 加具体 Scope 授权，ADMIN 不隐含权限。

### 验证

- 后端 `clean test`：47 项，0 失败/错误，1 跳过，耗时约 76 秒。46 项实际通过，包含 4 项专用 MySQL 验证；仅未配置 SQUAD_DB_URL 的原 MySqlGoalTaskIntegrationTest 跳过。
- MySqlScopeRuntimeOwnershipTest 三个实际 MySQL 8.4 用例：无任务预算/审批/追问/附件与取消/未知屏障、建立旧快照后的并发用户预算、共享账户与未知计费；MySqlConversationExecutionMigrationTest 使用另一全新隔离库做 V6 → 最新迁移。
- H2 增加相同无 Task 边界覆盖、工具跨 Turn 的独立计费键和旧工具预留在升级后按原键结算。D3 的派发、结果复用、崩溃恢复、候选纠正与 STOP 回归通过。
- 修正旧测试中绕过受理服务的 Task 数据，让其具备真实 Scope；原完成/恢复/取消断言保留。删除后的用户查询改为拒绝，内部 Mapper 仍验证 CANCELLED 与审计事件存在。
- 前端构建通过；仅调整可空 taskId 的类型，没有宣称新界面已可验收。
- 静态设计检查：39 个操作、67 个 schema、26 张业务表；OpenSpec strict、git diff --check 通过。新 Java 文件 6 个经 IDEA 格式化；预算投影仅改名不做整文件格式化。

日志：`/private/tmp/agentsquad-scope-full-verified.log`、`/private/tmp/agentsquad-scope-mysql-current-read.log`、`/private/tmp/agentsquad-scope-web-build.log`。SDK 固定版本、JDK 27 执行/Java 21 编译目标的限制与上一切片相同。

无 Task 能力实验由测试建立受权执行数据并通过正式治理服务和 SDK 模型事件验证，不是新消息 API/原生回复端到端实验，也没有向真实模型供应商发请求。实际开发库与当前用户页面未切换。
