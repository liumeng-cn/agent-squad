# v2-observability Specification

## Purpose

在 Squad v2 持续对话中复用 AgentScope 原生 OpenTelemetry 埋点，向 Langfuse 导出执行调用链，并通过平台管理入口打开 Langfuse 页面，保持执行与观测故障隔离。

## Requirements

### Requirement: 原生调用链与身份关联

系统 SHALL 通过原生 OtelTracingMiddleware 生成 Agent、模型及工具 Span，以用户和会话共同标识 gen_ai.conversation.id，并将关联属性传播到每个 Span。系统 MUST 不从 SSE 重建执行调用链，不额外记录完整上下文、工具输入输出或思考正文。

#### Scenario: 后台 Session 调用工具

- **WHEN** 原生 AgentSession 在后台执行一次模型、工具与模型调用
- **THEN** 相关 Span 保持父子关系并属于同一 Trace
- **AND** 每个 Span 携带相同的用户和 Session 分组标识

### Requirement: 可关闭的有界异步导出

系统 SHALL 默认关闭 Langfuse 导出，启用时使用配置的 OTLP HTTP endpoint 和项目认证信息，通过有界批量队列异步发送，应用关闭时释放 SDK。

#### Scenario: 导出端故障

- **WHEN** Studio 接收端不可用
- **THEN** 观测导出不得改变 Agent 执行结果，不阻塞业务等待无限重试

### Requirement: 复用原生管理界面

系统 SHALL 为管理员提供“Agent 观测”链接，新标签页打开配置的 Studio 地址，复用原生中文界面；地址配置不包含接入密钥。

#### Scenario: 管理员打开观测入口

- **WHEN** 管理员查看工作台账户菜单、账号管理、能力管理或 v2 聊天页
- **THEN** 可以从入口访问 Studio 原生页面
- **AND** 普通用户不显示管理入口，非法协议或含凭据的 URL 不渲染
