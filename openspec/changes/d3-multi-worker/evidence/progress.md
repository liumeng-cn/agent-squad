# D3 本地验收记录

记录时间：2026-10-28。运行底座、HTTP 契约与页面走查采用不同证据，页面夹具不作为模型执行证据。环境和实际 SDK JAR/迁移摘要见 `environment.json`；测试结果见 `test-results.json`，平台命令与原生持久事件的脱敏摘录见 `runtime-excerpts.log`。

## 运行与故障窗口

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| 单目标完整链路/A17 | Master 发现新能力、prepare_context、publish_plan、Worker 提交、Master 汇合与最终回答；整个对话只存在一个 GoalTask；Master 6 次和 Worker 2 次本地模型请求 | AutonomousWritingFlowIntegrationTest |
| 新 Profile/A17 | 新 Definition 引用 review Profile，真实 SDK 执行、结果终态、两次按 review-v1 结算；Master 最终完成目标 | WorkerNativeExecutionIntegrationTest |
| 计划准入/A04 | DAG 环、依赖、版本、节点数与并行宽度；上下文缺失、用户越权、Schema 不匹配、总预算不足时拒绝，不创建可执行节点 | PlanPublishIntegrationTest |
| 发布后的受理窗口/A09 | 计划已写但工具回包未确认时以相同 logicalKey 重放，返回相同 Plan/Jobs；准备阶段应用退出后第二实例从已持久 SUBMIT 执行 | PlanPublishIntegrationTest、WorkerNativeExecutionIntegrationTest |
| 两实例与 writer/A05 | 同 Job 两个 Spring 实例仅一个有效业务 guard；旧 guard 不能提交；SDK JournalSessionLog 拒绝被接管的旧 writer | WorkerGuardIntegrationTest、NativeWriterFencingTest |
| 分支崩溃/A06 | 第一个分支已成功；第二个分支写入候选、在后续模型请求阻塞时 seal 旧 writer 并使 guard 过期。候选阶段仍为 RUNNING；第二实例恢复原 Session/Turn，成功分支不重跑。崩溃分支一个 SUBMIT、一个 RESUME；共 5 次 Worker 请求，包含被故意阻塞的旧请求 | WorkerCrashRecoveryIntegrationTest |
| 串行实际执行 | 两个节点依赖执行，后置节点模型输入确有前置结果；两个 Worker 各两次请求，Master 两次；6 个 MODEL Reservation 全结算，只创建一个 MASTER_SIGNAL | MultiWorkerExecutionIntegrationTest（parallel=false） |
| 并行实际执行 | 两个根节点的首个模型请求通过双计数闩锁确认实际重叠；6 次模型请求结算，只创建一个 MASTER_SIGNAL | MultiWorkerExecutionIntegrationTest（parallel=true） |
| 不可恢复分支 | 第二个节点原生回合结束但没有合法候选，进入 RECOVERY_BLOCKED；Master 收到失败状态，提交 PARTIAL，目标明确说明缺口；5 次模型请求，只汇合一次 | MultiWorkerExecutionIntegrationTest.reportsPartialAfterUnrecoverableBranch |
| 有界恢复 | 节点和运行 Profile 的恢复次数取较小值；同一次接管不重复排队；耗尽后阻塞并通知 Master | WorkerGuardIntegrationTest |
| 替代计划/A10 | 运行中的旧 Job 设置取消屏障，迟到候选不能写成功或激活新依赖；旧的已发生操作记录保留 | WorkerGuardIntegrationTest |
| 保守复用/A09 | 同一目标、相同输入摘要/不可变能力及权限代际、无外部证据且依赖来源一致时复用串行节点，记录 reusedFromJobId；目标或证据变化则重新派发 | PlanPublishIntegrationTest.shouldReuseMatchingSerialResults |

上述 D3 SDK 实验的外部业务操作次数均为 0。模型响应由 loopback HTTP 桩提供，运行的是正式 HarnessAgent、持久 Command/Binding/guard、原生日志与事务投影链路。A10 中保存的副作用是本地模拟账本证据。未知收费与 Master/Worker/工具共享预算另由 BudgetReservationConcurrencyTest 和 ModelBudgetMiddlewareTest 覆盖。

## 页面、接口与最小产物

`TaskDetailsHttpTest` 使用真实 Controller/Service/MyBatis/Flyway，覆盖上传幂等、类型限制、CSRF、同用户文件归属、不可见文件统一 404、内容摘要损坏拒绝、无效引用导致目标受理整体回滚、Job 列表归属、操作详情脱敏、OPERATOR 角色和显式目标授权同时生效、核对旧版本拒绝、UNRESOLVED 保持 UNKNOWN、追问 Schema 与重试幂等，以及 answer.replaced 替换旧正文（B05/B06/B08/B09/B10）。

`TaskPageReviewServer` 是专门的页面状态夹具。它用真实本地 API 提供“一个成功节点 + 一个等待输入节点”、文件和未知操作；不运行模型。浏览器走查确认：等待态未显示为完成；一个目标下显示两个节点；结构化追问提交后进入交互历史；无需再次点击“恢复执行”；仅服务端 canReconcile 为 true 才展示核对表单。提交 UNRESOLVED 后显示“核对结论已记录”，仍保留 UNKNOWN，并显示已保存的证据数量。截图见 `task-detail.png`。

浏览器下载事件捕获曾超时，未把该工具结果记作下载成功。下载接口字节、归属与摘要校验已由 HTTP 与 MySQL 测试验证。

## 汇总与复现

- 从干净目录执行 `clean test` 的完整离线测试：37 项，0 失败，1 项 MySQL 环境测试默认跳过。
- 单独创建临时 MySQL 8.4 容器，运行 MySqlGoalTaskIntegrationTest：1 项通过、0 跳过；六个迁移执行成功，产物上传/关联/读回字节通过。测试容器已停止并移除；没有清理已有实际数据库。见 `mysql-excerpts.log`。
- 前端 `npm run build` 通过；OpenSpec 严格校验通过。
- 编译目标为 Java 21（class major=65），本次测试运行时实际为 JDK 27；Java 21 运行时复验尚未完成，官方运行时下载遇到网络 TLS 错误。该限制保留到集成发布门槛，不将本次结果描述为 Java 21 运行验证。
- 未调用真实模型、真实连接器或生产系统；D4 的 Skill/记忆、D5 的沙箱 stage/commit/清理、D6 的完整集成发布门槛仍未完成。

复现运行测试：

```sh
mvn -f server/pom.xml test
mvn -f server/pom.xml -Dtest=AutonomousWritingFlowIntegrationTest,MultiWorkerExecutionIntegrationTest,WorkerCrashRecoveryIntegrationTest,NativeWriterFencingTest test
cd web/console && npm run build
```

MySQL 测试需显式设置项目专用 SQUAD_DB_URL、SQUAD_DB_USER、SQUAD_DB_PASSWORD；未配置时跳过。实际执行使用临时 Maven 缓存 `/private/tmp/agentsquad-m2` 及 offline 模式，缓存路径不是项目配置。页面夹具仅用于视觉检查，不连接真实模型，入口和临时账号在 TaskPageReviewServer 的测试源码中。

## 用户表达调整

统一助手提示词要求直接交付正文或成果，对内部材料核验、去重、统一口吻；Worker 仅提供实质材料与缺口，不面向用户汇报。默认任务页移除执行标识、节点列表、原始结果与内部轮次通知；保留实际状态、追问、操作确认、文件与交互历史。操作状态使用用户可理解的文字。前端构建通过，AutonomousWritingFlowIntegrationTest 与 MultiWorkerExecutionIntegrationTest 共 4 项原生回归通过。此轮未调用真实模型，不将模型桩回归作为真实回答口吻的验收。
