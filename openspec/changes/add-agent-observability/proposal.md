# 提案：全链路日志追踪与 Agent 观测基础

## Why

当前系统已有 requestId、scopeId、executionId、taskId、commandId 和 AgentScope 原生执行事件，但这些编号没有形成一条可连续查询的调用链。出现慢请求、重复回答、工具重试或外部操作结果未知时，排障人员仍需要在网关、业务服务、命令投递、Agent 执行和事件投影之间手工拼接日志。

`docs/agent-observability-design.md` 已给出三层方案：应用日志、执行追踪（Trace）和 Agent 测评（Eval）。本变更只交付前两层的第一、二阶段，为后续指标趋势和测评平台保留稳定接口，不把观测数据当作业务事实。

## What Changes

- 在 Gateway 与 Server 接入 OpenTelemetry Trace，使用 W3C `traceparent`/`tracestate` 跨服务传播，继续保留现有 `X-Request-Id`。
- 在 WebFlux/Reactor 与 Servlet/MDC 两条线程模型上分别实现上下文传播和清理，避免异步/跨线程串号。
- 提供公共追踪工具，统一步骤名称、允许字段、错误分类和日志大小上限；业务 Service 不直接依赖第三方 SDK。
- 将关键路径改为结构化日志：消息受理、命令投递、Agent 运行、模型/工具调用、计划分派、事件投影、等待交互、最终答案发布和 SSE 通知。
- 评估并按需引入 `sq_observability_link` 关联记录，保存 userId、scopeId、commandId、上一次 trace/span 和保存时间；表结构与迁移另行审查，不在本变更自动执行生产 DDL。
- 增加可关闭的 OTLP 输出与可选开发期 Collector；导出失败、队列满或监控平台不可用时不得阻塞业务。
- 补充伪造追踪头、跨线程、重复提交、失败重试、进程重启、Master + 多 Worker、取消、审批等待和 `UNKNOWN` 不重复外部写入的测试。

## 范围之外

- 第三阶段的时间线看板、模型/工具/失败类型趋势指标、成本报表和采样压测。
- 第四阶段的测评数据集、实验、评分、人工反馈和离线隔离回放。
- 生产环境 OTLP Collector、存储后端、告警规则和真实数据库迁移的部署操作。
- 记录完整 Prompt、用户隐私、Cookie、密钥、完整 request/response 或异常长文本。
- 用观测数据替代 MySQL 业务事实，或把采样后的 Trace 数量当作任务成功率。

## Impact

- 代码：`server/agent-squad-gateway` 的过滤器与路由配置；`server/agent-squad-server` 的 `RequestCorrelationFilter`、命令投递、Agent 执行、模型预算、计划、事件投影、操作账本和最终发布服务。
- 配置：两个模块的 `pom.xml` 与 `application*.yml`；新增 OTLP/采样/日志限额配置。
- 存储：仅在评审通过后新增 `sq_observability_link`，并同步 `V1__baseline.sql`、Mapper XML 与迁移链；不自动修改真实数据库。
- 测试：追踪头伪造、网关转发、异步线程、长日志/大集合、观测服务不可用、重复提交、重试、重启、多 Worker、取消、审批和 `UNKNOWN`。
- 文档：更新 README 的验证命令，并保留 `docs/agent-observability-design.md` 作为设计依据。
