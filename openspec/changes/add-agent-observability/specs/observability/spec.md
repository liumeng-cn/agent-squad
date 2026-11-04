# Spec Delta: 可观测性（Observability）

## ADDED Requirements

### Requirement: 跨网关与业务服务的追踪关联

系统 MUST 使用 W3C `traceparent`/`tracestate` 在 Gateway 与 Server 之间传播 Trace，并继续保留 `X-Request-Id`；Gateway 的 WebFlux/Reactor 与 Server 的 Servlet/MDC MUST 分别处理跨线程传播和清理，不得因线程池复用串用其他用户或请求的编号。

#### Scenario: 同一请求可连续查看（OBS-01）
- **WHEN** 用户发起一次页面请求并经过 Gateway 转发到 Server
- **THEN** 两侧日志使用同一 `traceId`，保留原始或新生成的 `requestId`，可按接口、状态和耗时连续查看

#### Scenario: 跨线程不串号（OBS-02）
- **WHEN** 请求经过 Reactor 异步链或 Servlet 线程池切换
- **THEN** 每个请求只看到自己的 traceId/requestId/scopeId，请求结束或异常后 MDC 被清理

### Requirement: 结构化日志安全与限额

系统 MUST 使用公共追踪工具输出结构化日志，只记录白名单字段、摘要和大小；单条日志 MUST 有大小上限，超出时截断非关键字段并标记；系统 MUST NOT 记录完整 Prompt/上下文、完整 request/response、Cookie、密码、Token、密钥、异常长正文或大集合全量内容。

#### Scenario: 敏感字段不落日志（OBS-03）
- **WHEN** 记录模型、工具、审批或外部写操作日志
- **THEN** 日志只包含允许的编号、名称、状态、耗时、token/成本和摘要，不包含完整上下文或凭据

#### Scenario: 大对象不整包序列化（OBS-04）
- **WHEN** 工具结果或集合超过日志上限
- **THEN** 只记录大小、摘要和允许的编号，单条日志不超过配置上限，并带有截断标记

### Requirement: 后台命令执行追踪

系统 MUST 为消息受理和后台命令投递记录可关联的 Trace 片段，包括 `scopeId`、`commandId`、尝试次数、等待时长、投递结果和失败阶段；同一命令重试 MUST 保持 `commandId`，不得因重试伪造新的业务对象。

#### Scenario: 命令等待与重试可查（OBS-05）
- **WHEN** 消息受理后后台命令等待、首次投递失败并重试
- **THEN** 可按 `scopeId`/`commandId` 查看每次尝试的时间、等待时长、失败阶段和最终结果

#### Scenario: 重启后仍可追溯（OBS-06）
- **WHEN** 进程在命令投递或执行期间重启
- **THEN** 恢复后的 Trace 片段通过已保存的业务编号关联原请求；无法关联的历史命令允许查询但不伪造 Trace

### Requirement: Agent 执行追踪

系统 MUST 为 Master、Worker、模型调用、工具执行、计划发布和 Worker 分派记录 Span 或结构化事件，记录 AgentScope Session/Run/Turn、模型名/版本、真实 token（可得时）、预算预留与实际结算、工具名和结果类型；Worker 成功 MUST NOT 被当作整个用户目标成功。

#### Scenario: Master 与多 Worker 可区分（OBS-07）
- **WHEN** 一个目标由 Master 和多个 Worker 处理
- **THEN** 可看出各节点运行顺序、模型调用数、工具调用、耗时和结果，并区分节点成功与目标最终成功

#### Scenario: 预算与真实消耗不混同（OBS-08）
- **WHEN** 模型调用发生预算预留和结算
- **THEN** 日志分别记录预留、实际结算和差异；真实 token 不可得时标记 `UNKNOWN`，不伪造数值

### Requirement: 事件投影与最终发布追踪

系统 MUST 为 Agent 事件投影记录已处理序号、延迟、重复或缺号，并为最终答案发布和 SSE 通知记录发布资格、发布结果和通知结果；重复事件 MUST NOT 导致重复创建业务结果或重复终稿。

#### Scenario: 重复事件不重复发布（OBS-09）
- **WHEN** 同一 Agent 事件被重复读取或多条路径竞争最终发布
- **THEN** 日志显示重复/竞争事实，业务侧仍只有一条权威最终结果，页面通知不改变后台执行事实

#### Scenario: 等待与取消不误判为失败（OBS-10）
- **WHEN** 任务处于等待输入、等待审批、取消中或外部操作结果 `UNKNOWN`
- **THEN** 观测记录使用对应状态和原因，不一律记为 ERROR，也不把等待当作成功

### Requirement: 观测故障隔离

系统 MUST 保证 Collector 不可用、OTLP 队列满或导出失败时允许丢弃观测数据，不得阻塞消息受理、命令投递、工具安全校验、审计或业务状态变更；采样后的 Trace MUST NOT 被当作任务成功率或业务事实的权威来源。

#### Scenario: Collector 不可用不影响业务（OBS-11）
- **WHEN** OTLP Collector 宕机、网络断开或导出队列满
- **THEN** 请求仍正常受理和执行，日志记录导出失败，但不改变业务结果、不重复外部写入

#### Scenario: 采样不影响事实判断（OBS-12）
- **WHEN** 生产 Trace 按比例采样
- **THEN** 任务成功、失败、重复交付、审批合规等判断仍从持久业务事实读取，不从采样 Trace 数量推断
