# Tasks：全链路日志追踪与 Agent 观测基础

> 本变更只覆盖设计文档的第一、二阶段。第三阶段指标趋势和第四阶段 Agent 测评另开变更，不与本变更混做。

## 1. 第一阶段：请求关联与安全日志

- [x] **OBS.1.1 接入 OpenTelemetry 追踪**：为 Gateway 与 Server 增加 Micrometer Tracing + OTel bridge + OTLP 依赖和配置；保留现有 Actuator/Prometheus。**涉及**：两个模块 `pom.xml`、`application*.yml`。**验证**：两个服务启动后能导出 Span；关闭 OTLP 时不影响启动。
- [x] **OBS.1.2 传播 W3C 与 requestId**：在 Gateway 过滤器和路由配置中传播 `traceparent`/`tracestate` 与 `X-Request-Id`；Reactor 异步链使用上下文传播。**涉及**：`GatewayRequestIdFilter`、`GatewayAuthenticationFilter`、`GatewayRoutingConfiguration`。**验证**：伪造/缺失追踪头、正常网关转发均产生正确关联。
- [x] **OBS.1.3 Server 端上下文与清理**：`RequestCorrelationFilter` 写入 traceId/requestId 到 MDC，请求结束和异常时清理；异步回调显式恢复上下文。**涉及**：`RequestCorrelationFilter`、异步命令线程。**验证**：线程池复用和异常场景不串号。
- [x] **OBS.1.4 公共追踪工具**：统一步骤名、字段白名单、错误分类、耗时和 4 KiB 大小上限；业务 Service 不直接依赖第三方 SDK。**涉及**：新增公共追踪组件。**验证**：单条日志超限被截断并标记，敏感字段不出现。
- [x] **OBS.1.5 关键路径结构化日志**：消息受理、命令投递、异常处理先改为结构化日志；只记录允许字段和摘要。**涉及**：`MessageAcceptanceWriter`、`CommandClaimService`、`CommandDeliveryService`、Controller 异常处理。**验证**：正常、失败、重试日志字段完整且不含完整对象。
- [x] **OBS.1.6 观测故障隔离**：OTLP 使用有界队列和异步导出；导出失败只记录，不阻塞业务。**验证**：Collector 不可用、队列满时接口仍成功，业务状态不变。
- [x] **OBS.1.7 第一阶段测试**：伪造 trace header、网关转发、跨线程/MDC 清理、长日志/大集合、观测服务不可用。**输出**：自动化测试及命令。**验收**：同一请求在网关/服务端连续可查，无串号、无内存暴涨。

## 2. 第二阶段：Agent 真实执行过程

- [ ] **OBS.2.1 验证 AgentScope 能力**：在 `2.1.0-BETA1` 隔离环境验证 `onAgent/onModelCall/onActing`、真实 token、工具状态和事件生命周期；记录可得与不可得字段。**输出**：验证记录与字段映射。**验收**：不基于稳定版文档假设 BETA 行为。
- [ ] **OBS.2.2 评估关联记录**：先用现有 scopeId/commandId/executionId 验证跨命令、跨重启关联；确认不足后评审 `sq_observability_link` 表结构、Mapper XML、`V1__baseline.sql` 和生产迁移。**验证**：没有充分理由不新增表；新增时先评审后实现。
- [ ] **OBS.2.3 命令投递 Span**：为 `CommandClaimService`、`CommandDeliveryService` 记录等待时间、尝试次数、原生回执和恢复原因。**验证**：可按 commandId 查看每次尝试。
- [ ] **OBS.2.4 Agent 运行与模型/工具 Span**：为 Master/Worker、`ModelBudgetMiddleware`、`ProfiledModelBudgetMiddleware`、工具执行记录 run/turn、模型、token、预算预留/结算、工具名和结果类型。**验证**：Master + 多 Worker 可区分，Worker 成功不等于目标成功。
- [ ] **OBS.2.5 计划与交互追踪**：为 `PlanService`、Worker 分派/依赖等待、审批/输入等待和外部写操作 `UNKNOWN` 记录步骤和原因。**验证**：等待、取消、`UNKNOWN` 不误判为失败或成功。
- [ ] **OBS.2.6 事件投影追踪**：为 `NativeProjectionPoller`、`NativeProjectionService` 记录已处理序号、延迟、重复/缺号；无事件正常轮询不刷日志。**验证**：重复事件不重复创建业务结果。
- [ ] **OBS.2.7 最终发布与 SSE 追踪**：为 `ResponseExecutionService`、`TaskFinalizerService`、`ConversationEventWriter`、`ConversationEventStreamer`、`TaskEventStreamer` 记录发布资格、发布结果和通知结果。**验证**：页面断开不改变后台结果，重复事件不产生第二份终稿。
- [ ] **OBS.2.8 第二阶段测试**：重复提交、失败重试、进程重启、Master + 两个 Worker、取消、审批等待、`UNKNOWN` 不重复外部写入。**验收**：给一个 `executionId` 或 `taskId`，能看见模型、工具、Worker、事件处理和答案发布的完整过程。

## 3. 后续变更（不在本次范围）

- [ ] 第三阶段：执行时间线看板、按模型/工具/失败类型/步骤的趋势指标、成本与耗时报表、采样与有界队列压测。另开变更。
- [ ] 第四阶段：版本化测评数据集、实验、评分、人工反馈、离线隔离回放和规则测评。另开变更。

## 4. 交付检查

- [ ] 运行 `mvn -f server/pom.xml -B -ntp test` 与 `npm --prefix web/workbench run build`，记录通过、失败、跳过和未验证项。
- [ ] 运行 `openspec validate add-agent-observability --strict`。
- [ ] 确认没有记录 Prompt、Cookie、密钥、完整请求响应或用户隐私。
- [ ] 确认观测平台故障不影响消息、命令、审计和工具安全。
