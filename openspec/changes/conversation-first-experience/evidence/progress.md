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

## 2026-10-28：1.3 正式消息持久受理

完成 1.3，整体 3/16。增加两个正式 HTTP 消息入口，仍未宣称普通回复 SDK 生命周期或新页面已经完成。

### 实现与语义

- 首发在一个独立平台事务创建会话、用户消息、回复执行、Scope/Account、SDK Binding、MESSAGE_SUBMIT Command、消息附件关联与两条会话事件；无任何 GoalTask、WorkerJob 或模型预留。
- 已有会话先按归属锁会话，分配单调 messageSeq；已有在途 GoalTask 不阻断消息，也不被普通消息暗中终结或改写。
- 首发的命令唯一键裁决并发；失败事务完整回滚后，外层从新读视图补查原收据。相同输入重送、服务重启后重送保留原 conversation/message/execution/command 身份；同键换输入返回 409。
- Artifact 引用验证同用户、有效期与就绪元数据，规范化 UUID 大小写、拒绝重复/无效引用；失败不遗留空会话、Scope 或执行，但保留之前独立成功的上传。
- 传输层拒绝注入 Task/用户/Scope 等未知字段，身份来自实际 Cookie+CSRF。首条消息生成安全短标题 fallback，不阻塞受理；完整标题管理/侧栏仍属于 4.3。
- MESSAGE_SUBMIT 暂不由旧 Task 投递器领取；普通回复的 SDK 接入在 1.4。新路径的运行状态明确标为仅持久受理，界面仍使用旧入口，未切换实际开发库或用户页面。

### 验证

- MessageAcceptanceHttpTest：4 项 H2 实际 HTTP 边界验证，包含首发并发/重送、Cookie/CSRF/身份字段、附件不存在/越权/过期整体回滚、在途任务追加消息，以及应用重启后原收据重送。
- MySqlMessageAcceptanceTest：3 项 MySQL 8.4 实际 HTTP 验证，覆盖并发首发、附件事务和在途消息；专用隔离库，未访问实际开发数据。
- 后端 clean test：54 项，0 失败/错误，1 跳过，耗时约 77 秒；53 项实际通过，包含 7 项真实 MySQL 测试，跳过未配置 SQUAD_DB_URL 的原 MySqlGoalTaskIntegrationTest。
- 静态契约检查：41 个操作、67 个 schema、26 张业务表，正反例/追踪/页面映射通过；不把契约数量视为运行完成度。OpenSpec strict 与差异检查通过。
- 本切片 8 个新增 Java 文件经 IDEA 格式化，再编译测试；已有文件仅局部修改。

日志：`/private/tmp/agentsquad-message-acceptance-test.log`、`/private/tmp/agentsquad-message-full-verified.log`。测试中的新消息均保持 QUEUED、nativeTurnId 为空、没有创建 GoalTask；这是持久受理事实，不是模型回答验收。

## 2026-10-28：1.4 固定消息前缀子切片

1.4 尚未完成，整体仍为 3/16；本切片只建立普通回复派发前的持久材料边界，没有开启 MESSAGE_SUBMIT 领取或宣称已生成模型回答。

- V11 为 ResponseExecution 增加冻结前缀正文及摘要。首次在会话→回复所有者锁下选择截至来源消息的已完成公开消息，按角色、序号、revision 与附件引用保存；不读取未来消息、工具日志或 Worker 日志。
- 重投、应用重启复用已冻结内容；每次读取仍验证会话代际、实际所有者和附件权限/有效期。Task/Worker 绑定不能使用整个会话前缀入口。
- 初次冻结明确限制 200000 字符，先统计再读取，超限回滚且不静默截断；此处没有自动摘要或无预算模型调用。
- MySQL 可重复读下，来源/冻结点和实际消息使用锁后当前读，避免等待会话锁后读取旧快照而错误重建材料。
- ResponseContextIntegrationTest 3 项：前文引用与未来隔离、重启后稳定性、越权与失效代际、冻结后附件过期、超限不保存残缺材料。MySqlResponseContextTest 1 项：两路并发冻结返回同一内容，来源之后的消息不进入前缀；无 Task 和模型预留。
- 最终后端 clean test：58 项，0 失败/错误，1 跳过，57 项实际通过，其中 8 项使用专用 MySQL 8.4；唯一跳过仍为未配置 SQUAD_DB_URL 的原 MySqlGoalTaskIntegrationTest。V6 → V11 实际升级通过，未迁移实际开发库。
- 8 个新 Java 文件经 IDEA 格式化后编译测试。静态设计检查及差异检查通过；无需改变用户传输契约。实际 JDK 27/编译目标 Java 21，仍未验证真实 Java 21 运行。

日志：`/private/tmp/agentsquad-response-context-verified.log`。后续继续接入独立回复 SDK Binding、完成候选、原生投影、取消与原 Turn 恢复；这些未完成项不勾选 1.4。


## 2026-10-28：1.4 普通回复SDK生命周期

完成 1.4，整体 4/16。此处是后端正式消息链路，不是新界面或真实模型供应商验收。

### 实现与边界

- MESSAGE_SUBMIT 开始由原持久 Bridge 领取，固定 commandId/Binding/Session；首次 SDK 提交前冻结公开会话前缀。Master 的完成工具按 Scope 区分普通回复和 GoalTask，不把普通消息改名为隐藏任务。
- 普通回复的原生 run/start、run/end 与持久候选、绑定/Turn/摘要共同裁决生命周期；只有原生成功后才原子写助手 Message 和会话事件。工具回执、内部日志及 Worker 正文不作为最终答案。
- 缺失/失效候选保留一次有预算纠正；writer 失效只以 RESUME 继续原 Session/Turn，保存恢复次数且最多两次。查询 SDK 时读取来源 Turn 的最新续行状态，避免把已续行完成的 Turn 永远看成原 run 中断。
- 停止回答有独立 HTTP 入口，校验 Cookie/CSRF、归属、幂等及 expectedVersion，不取消独立 Task。无活跃运行才就地取消；存在 UNKNOWN/RECONCILING/IN_FLIGHT 操作时保留 RECONCILING，不提前取消、不清账、不另发业务写入。
- 删除会话同时保存未终结回复的停止意图，清理等待所有回复安全收尾；失效代际的完成候选不能重新公开正文或追加会话事件。
- 所有者锁后，候选、操作、交互及控制幂等使用当前读，避免 MySQL 可重复读的旧快照裁决完成。旧 Task 的完成、派发、汇合与恢复链路保持回归。

### 验证

- ResponseNativeExecutionIntegrationTest 7 项：提交前重启（A02）后完成并连续追问；SDK 完成但平台收据丢失（A03）；收据已存而投影丢失（A12）；停止未派发回复；旧 writer 封存后新实例同 Session/Turn 恢复；失效代际不发布旧候选；未知写入阻止完成、纠正和提前取消。
- SDK 用例真实运行固定 AgentScope Harness/Bridge/JDBC journal，模型由本地 OpenAI 协议桩提供。普通问答没有 GoalTask；两轮问答共 4 次真实 HTTP 模型请求且模型费用正常结算；收据/投影恢复不增加原 2 次推理。
- 封存 writer 时已有候选，旧模型请求尚未结束；第二实例沿用原 Turn，MESSAGE_SUBMIT 仍只有一个、RESUME 一个、最终助手消息一个。该受控故障窗口不是操作系统强杀/生产恢复证明。
- ResponseControlHttpTest 2 项：未授权/缺 CSRF/版本冲突/跨用户拒绝、同键重送、回答取消不影响后台 Task、删除等回复收尾且不产生迟到公开内容。
- 全量回归曾发现手动投递测试的 due_at 毫秒取整竞争：调用时命令可能尚差不足 1ms 到期，正式轮询下一轮可正常领取。测试明确置为到期命令，保留原取消断言，不靠放宽结果或增加等待掩盖。
- 最终后端 clean test：67 项，0 失败/错误，1 跳过；66 项实际通过，包含 8 项专用 MySQL 8.4 验证。唯一跳过是未配置 SQUAD_DB_URL 的原 MySqlGoalTaskIntegrationTest。D3 派发、复用、恢复与取消全部回归通过，V6 → V11 实际升级通过。
- 9 个新增 Java 文件经 IDEA 格式化后编译测试；已有 Java 仅局部修改。OpenAPI/ui-manifest/追踪新增回答取消入口，共 42 操作、67 schema、26 张业务表；没有改前端代码或声称页面切换。

日志：`/private/tmp/agentsquad-response-runtime-verified.log`。实际 JDK 27/编译目标 Java 21，未完成真实 Java 21 运行验证；未访问真实模型供应商，未升级实际开发库。

下一项为 2.1 受控创建目标并继承来源账户，随后 2.2/2.3 有序领取与目标修订。提交前故障的结构化分类、完整有界退避及允许动作继续属于 3.2/3.3；会话快照/SSE 和新页面属于 3.1/4.x，本切片不将这些标为已完成。


## 2026-10-28：2.1 Master受控建立独立目标

完成 2.1，整体 5/16。普通消息仍不自动建任务，新增 create_goal_task SDK 工具，由 Master 决定何时建立独立目标。

- 服务端从 SDK 用户/Session 查找来源 Response Scope 和消息；sourceMessageId 必须匹配当前受权消息。模型不能指定用户、会话、绑定、账户；Task Scope 不能再建目标。
- 会话和来源回复锁下，以原 Scope+logicalKey 和规范化请求摘要去重；同输入重放返回原 Task/Command，异输入冲突，单活目标约束保留。来源回复或原目标终结后重放不刷新额度、不重新激活目标。
- 原子创建目标来源关系、Task Scope、独立 Task Master Binding、SUBMIT 命令、任务受理事件与会话 task.created；引用来源 BudgetAccount，不创建第二账户。用户原文与模型整理的目标分开保存，Worker局部材料不继承整段会话；来源附件重新验证并关联。
- GoalTaskCreationIntegrationTest 4 项：所有权/来源伪造/单活/幂等与共享预算、并发工具重放、外层事务完整回滚、附件过期回滚及有效继承。预算用例在来源已有 UNKNOWN 占用60/上限100的情况下，任务只能再占40；回复终结不使额度归零或重置。
- MySqlGoalTaskCreationTest 2 项：独立 MySQL 8.4 测试库验证相同所有权/共享账本规则和并发重放；不访问实际开发库。
- GoalTaskNativeCreationIntegrationTest 1 项：从正式 Message 服务受理用户消息开始，真实 SDK 调用 create_goal_task，独立 Task Master 发布提纲→成稿两节点DAG，两个Worker完成、汇合并发布Task结果；回复和目标各自成功，Task仅一个、预算账户仅一个，14次本地HTTP模型请求均已结算。
- 原生链路使用固定SDK和本地模型协议桩，验证真实工具/调度/持久语义，不推断真实供应商的规划质量。Task结果的统一会话投影/新页面尚属3.1/4.x。
- 7个新Java文件经IDEA格式化后编译；最终后端clean test：74项，0失败/错误，1跳过，73项实际通过（含10项MySQL验证）。跳过未配置SQUAD_DB_URL的原MySqlGoalTaskIntegrationTest；D3和普通回复既有用例回归通过。运行JDK27、编译目标21，真实Java21运行证据仍待D6。
- 沿用V7已有来源列和Scope/Command唯一键，不新建重复存储或修改历史迁移。新增GoalTaskCreate工具契约与正反例，静态42操作/68schema/26业务表，OpenSpec strict与差异检查通过。

日志：`/private/tmp/agentsquad-goal-create-full.log`。本切片未升级实际开发库、未切换用户页面。继续2.2消息有序领取与独立目标进度事实，再推进2.3修订指令。
