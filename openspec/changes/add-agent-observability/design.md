# 设计：全链路日志追踪与 Agent 观测基础

## Context

Gateway 与 Server 已有 `X-Request-Id`，业务侧已有 scopeId、executionId、taskId、commandId、jobId、bindingId/runId/turnId。缺口不是“再增加一个编号”，而是缺少统一 Trace、跨线程上下文和安全结构化日志，导致一次请求在网关、命令投递、Agent 执行、事件投影和页面通知之间无法连续查看。

AgentScope Harness 固定在 `2.1.0-BETA1`。该版本的 `onAgent/onModelCall/onActing` 生命周期、真实 token 和工具状态必须先在隔离环境验证，不能先假定字段存在再写死日志。

## Goals / Non-Goals

**Goals：**

- 同一请求在 Gateway 与 Server 使用同一 `traceId`，保留 `requestId`。
- 后台命令、Agent 运行、模型调用、工具执行、事件投影和最终发布形成可查询的 Span 层级。
- 结构化日志只记录允许字段、摘要和耗时，单条日志有大小上限。
- 观测链路故障不影响消息受理、命令投递、工具安全性和业务状态。
- 为后续指标趋势和测评保留稳定的事件名称、字段和关联编号。

**Non-Goals：**

- 不在本变更建设第三阶段指标报表或第四阶段测评平台。
- 不把 Trace 当作业务事实，不用采样后的 Trace 计算成功率。
- 不记录完整 Prompt、完整 request/response、异常长字符串、Cookie、密钥或用户隐私。
- 不自动执行真实数据库迁移，不自动回放线上写操作。

## Decisions

### 1. 使用 OpenTelemetry + W3C Trace Context 作为统一追踪层

Gateway 使用 Spring Cloud Gateway 的 Reactor 模型，Server 使用 Spring MVC Servlet。选择 Micrometer Tracing + OTel bridge + OTLP 导出，保留现有 Actuator/Prometheus 依赖，Agent 特殊步骤通过 OTel API 补充。

**为什么不用某个平台 SDK 直接埋点：** 平台 SDK 会把业务代码绑定到具体厂商，后续替换成本高。OTel 是通用标准，Collector 可以统一做过滤、限流和导出。

**替代方案：** 只扩展 `X-Request-Id`。它只能覆盖一次 HTTP 请求，无法表达后台命令、Agent 回合和跨进程恢复，因此不采用。

### 2. 三类编号职责分离

- `requestId`：一次接口请求，用于页面报错和历史日志查找。
- `traceId/spanId`：一次技术调用链路；后台任务不能保持一个 HTTP Span 运行数小时。
- `scopeId`：一次回复或目标任务的统一执行范围，跨越恢复；`executionId/taskId`、`commandId`、`jobId`、`bindingId/runId/turnId` 各自保持业务语义。

后台接手命令时开启新的 Trace 片段，通过 `sq_observability_link` 或已保存的业务编号关联上一次调用。无法关联的历史命令允许查询，但不伪造 Trace。

**为什么不把所有编号合并成一个：** 它们生命周期不同，合并后会丢失“哪次调用、哪次尝试、哪个业务对象”的区别。

### 3. Gateway 与 Server 分别处理线程上下文

- Gateway：在 `GatewayRequestIdFilter`、`GatewayAuthenticationFilter`、`GatewayRoutingConfiguration` 中传播 W3C 头和 `X-Request-Id`，Reactor 链使用上下文传播，不依赖 ThreadLocal 自动复制。
- Server：`RequestCorrelationFilter` 解析或生成 traceId/requestId，写入 MDC；请求结束、异常和异步回调都必须清理，避免线程池复用串号。

**为什么分两条路径实现：** WebFlux 和 Servlet 的线程模型不同，不能用一个全局 ThreadLocal 方案覆盖两者。

### 4. 公共追踪工具与允许字段

提供一个公共追踪工具，统一步骤名称、错误分类、耗时和字段白名单。业务 Service 只调用该工具，不直接依赖第三方 SDK。

允许字段包括：`event`、`message`、`traceId`、`spanId`、`requestId`、`scopeId`、`executionId`、`taskId`、`commandId`、`jobId`、`bindingId`、`runId`、`turnId`、`attempt`、`durationMs`、`status`、`errorCode`、`provider`、`model`、`toolName`、`tokenCount`、`costMicrounits`。

禁止记录：完整 Prompt/上下文、完整 request/response、Cookie、密码、Token、密钥、异常完整堆栈正文、大集合全量内容。

**为什么用白名单：** 先序列化整个对象再截断仍可能泄露敏感字段并产生大对象；白名单在入口就限制风险。

### 5. 单条结构化日志限制在约 4 KiB

初始上限 4 KiB，超限时截断非关键字段并记录 `logTruncated=true`；大集合和巨型工具结果只记录大小、摘要和允许的编号。上线前通过压测调整。

**为什么不是无限制：** 大日志会引发内存、磁盘和采集链路问题，也会把敏感内容带进集中日志系统。

### 6. 观测数据与业务事实分离

消息、任务、审批、操作账本和最终发布仍以 MySQL 业务记录为准。Trace 和日志只用于排障；Collector 不可用、队列满或导出失败时允许丢弃观测数据，但不能丢消息、命令、审计或改变工具安全性。

**为什么：** 监控平台不能成为业务单点故障。

### 7. 命令与 Agent 步骤命名

采用设计文档中的标准步骤名：`http.request`、`message.accept`、`command.deliver`、`invoke_agent`、`model.call`、`tool.execute`、`plan.publish`、`worker.dispatch`、`event.apply`、`interaction.wait`、`answer.publish`、`event.notify`。

**为什么固定名称：** 后续指标、看板和测评需要稳定维度，不能每个类自定义名称。

### 8. AgentScope 版本能力先验证再埋点

在 `2.1.0-BETA1` 上验证 `onAgent/onModelCall/onActing`、真实 token、工具状态和事件生命周期。能取得的事实才记录；取不到时标 `UNKNOWN` 或省略，不伪造耗时、token 或成功状态。

**为什么：** 该版本是固定 BETA，不同版本的中间件回调字段可能变化，不能凭稳定版文档推断。

### 9. 关联记录 `sq_observability_link` 最后引入

优先使用已有业务编号关联。只有确认“跨命令、跨重启无法仅靠现有字段关联”时，才新增 `sq_observability_link`。表结构、Mapper XML、`V1__baseline.sql` 和生产迁移必须单独评审；本变更不自动执行真实库 DDL。

**为什么不先加表：** 新增存储会增加事务和迁移成本，先用现有字段验证关联能力。

## Risks / Trade-offs

- **上下文串号风险**：异步线程和线程池复用可能导致 traceId/requestId 串用户。通过 MDC 清理、Reactor 上下文传播和并发测试降低。
- **日志体积风险**：结构化日志可能放大日志量。通过 4 KiB 上限、字段白名单、采样和只记录摘要降低。
- **AgentScope BETA 能力不确定**：通过先验证后接入，缺失字段标 `UNKNOWN`，不阻塞主流程。
- **观测平台故障**：通过异步 OTLP 导出、有界队列和失败隔离，保证业务链路不受影响。
- **新增关联表**：可能引入额外事务和迁移风险，因此最后引入并单独评审。

## Migration Plan

1. 先完成第一阶段的追踪关联、结构化日志和故障隔离，不改变业务事务。
2. 在隔离环境验证 AgentScope `2.1.0-BETA1` 回调字段和 token 可得性。
3. 第二阶段接入命令、Agent、模型、工具、计划和事件投影步骤。
4. 如确需 `sq_observability_link`，先同步 `V1__baseline.sql`、Mapper XML 和测试，再由用户决定是否执行本地/生产迁移。
5. 第三、四阶段分别以新的 OpenSpec 变更提出，不与本变更混做。

## Open Questions

- AgentScope `2.1.0-BETA1` 的 `onModelCall` 是否稳定提供真实 token 和模型版本；若不能，模型成本仍以预算账本为准。
- OTLP Collector 的部署位置和采样率由部署环境决定，本变更只提供可关闭配置。
- `sq_observability_link` 是否必须新增，需在第一阶段完成后用真实排障场景验证。
