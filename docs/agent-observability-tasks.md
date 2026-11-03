# 全链路日志与 Agent 测评：开发任务清单

> 这是基于 main 的实施计划，属于本分支的设计交付，不是已完成任务。编号用于一次提交完成一个完整阶段，不建议每改一个类就提交一次。

## 第一阶段：把请求和日志关联起来

- [ ] 给 Gateway、Server 接入统一的 OpenTelemetry Trace；保留原来的 X-Request-Id。
- [ ] Gateway 的 WebFlux/Reactor 和 Server 的 Servlet/MDC 分别做好跨线程传递和清理，不能串用户编号。
- [ ] 封装公共追踪工具，统一步骤名称、允许记录的字段、失败类别；不把第三方 SDK 写进所有 Service。
- [ ] 日志结构化：记录操作、结果、耗时、traceId、scopeId、commandId 等必要编号；不输出完整参数、返回值和异常长字符串。
- [ ] 引入可关闭的 OTLP 输出和可选开发期 Collector；网络断开/输出队列满时不影响业务。
- [ ] 测试：伪造 trace header、网关转发、异步线程、长日志/大集合、观测服务不可用。

**检查结果：**同一请求在网关/服务端能连续查看，各阶段耗时有据可查，不出现因日志而造成的内存暴涨。

## 第二阶段：打通 Agent 真实执行过程

- [ ] 评估 AgentScope Harness 2.1.0-BETA1 真实 onAgent/onModelCall/onActing 阶段，明确模型真实 token 和工具状态能否取得。
- [ ] 在消息/目标保存时记住与后台命令的追踪关联；先审查需要新增的 sq_observability_link 表，生产迁移需另行审核。
- [ ] 为 CommandClaimService、CommandDeliveryService 记录等待时间、第一次投递、重试、原生回执和恢复原因。
- [ ] 记录 Master 和 Worker 的 Run/Turn、模型调用、工具执行时间，以及预算预留/实际消耗的区别。
- [ ] 计划生成、Worker 分派/完成、依赖等待、工具审批、外部写操作 UNKNOWN 各有可查步骤。
- [ ] 读取 AgentScope 执行事件时记录事件序号、更新结果、延迟、重复或中间缺号；无事件正常轮询不刷日志。
- [ ] 记录最终答案是否发布、被哪条执行抢先发布，以及 SSE 是否通知到页面。
- [ ] 测试重复提交、失败重试、进程重启、Master + 两个 Worker、取消、审批等待、UNKNOWN 不重复外部写入。

**检查结果：**给一个 executionId 或 taskId，能看见模型、工具、Worker、事件处理和答案发布的完整过程。

## 第三阶段：让日志能帮助优化

- [ ] 展示一次执行的时间线：每一步耗时、谁调用谁、在哪一步失败、重试几次。
- [ ] 建立按模型/工具/失败类型/步骤的趋势指标；避免将用户 ID 和执行 ID 用作 Prometheus 标签。
- [ ] 建立模型 token、成本、耗时、排队耗时、任务成功/失败/恢复的报表；未知数据明确显示未知。
- [ ] 配置采样、最大日志/Span 大小、有界输出队列以及导出失败监控，并压测运行开销。

**检查结果：**能回答“哪个环节慢、哪个模型贵、哪类工具最容易失败”。

## 第四阶段：增加 Agent 测评（预留，不与日志改造混做）

- [ ] 建立版本化测试题库、测试用例、实验、评分和人工反馈的存储结构；按用户隔离。
- [ ] 保存代码、模型、提示词模板、Skill、Tool Schema、价格等版本信息。
- [ ] 从已保存业务事实中提取安全、成功、执行过程指标，不能依赖采样后的 Trace 数量。
- [ ] 首批规则测评：审批前不得外部写入、同一操作不得重复产生副作用、同一条用户消息只发一个最终答案、Worker 依赖顺序正确。
- [ ] 增加离线隔离运行，外部写工具必须模拟，不能用生产连接直接“回放”。
- [ ] 用同一批用例对比改动前后结果、耗时和成本；LLM 评分须有人工作为校准基准，不能单看主观分数。

**检查结果：**换模型或调整 Master/Worker 流程后，能用固定用例量化是否变好。

## 涉及代码

- Gateway：GatewayRequestIdFilter、GatewayAuthenticationFilter、GatewayRoutingConfiguration。
- Web：RequestCorrelationFilter、各 Controller 和异常处理。
- 异步命令：MessageAcceptanceWriter、CommandClaimService、CommandDeliveryService。
- AgentScope：MasterAgent、WorkerFactory、ExecutionContextMiddleware、ModelBudgetMiddleware、NativeProjectionPoller/Service。
- 流程与工具：PlanService、WorkerDispatchService、OperationExecutor、SandboxExecutionService、InteractionService、ResponseExecutionService、TaskFinalizerService。
- 配置和测试：两服务 pom.xml、application*.yml、必要时独立的 Collector 配置、回归测试。

保留现有 Java 分层、@Resource 注入、@Slf4j 日志、中文注释和 MyBatis XML 约定；不自行操作真实数据库。术语统一参考 [术语说明](terminology-guide.md)。
