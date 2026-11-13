# AgentScope Studio 本地观测

本项目复用原生 `OtelTracingMiddleware`，将执行调用链通过 OTLP HTTP 上报到独立 Studio 容器。Langfuse 容器及数据卷保留，当前不接收新的 Squad v2 Trace。

## 地址与运行

- 页面：http://127.0.0.1:13001
- Trace 接收：http://127.0.0.1:13001/v1/traces
- Docker 项目：`squad-studio`
- 数据：独立命名卷 `squad-studio_studio_data`

```bash
docker compose -p squad-studio -f deploy/agentscope-studio/compose.yml up -d
docker compose -p squad-studio -f deploy/agentscope-studio/compose.yml ps
docker compose -p squad-studio -f deploy/agentscope-studio/compose.yml logs --tail 60 studio
docker compose -p squad-studio -f deploy/agentscope-studio/compose.yml stop
```

官方镜像、独立数据卷、仅回环地址映射。Studio 默认使用其本地存储，不连接 Squad 数据库。中文界面在 Studio 设置的显示语言中切换。

## 两个地址分别配置

后端 v2 的 `application-local.yml` 已显式配置：

```yaml
agentsquad:
  v2:
    observation:
      enabled: true
      endpoint: http://127.0.0.1:13001/v1/traces
      environment: local
```

`endpoint` 是实际导出目的地，必须包含 `/v1/traces`。关闭 `enabled` 后不创建导出 SDK 和观测中间件。配置改变后需要重启 v2 应用。其他 profile 默认不开启观测，不隐式连接本地服务。

前端管理入口单独使用 `VITE_OBSERVATION_URL`，默认 `http://127.0.0.1:13001`。该地址用于浏览器打开页面，不加 `/v1/traces`，也不包含服务端密钥。管理员可从聊天页、工作台账户菜单、用户管理和能力管理打开原生 Studio 页面。

## 同步逻辑

1. `ChatAgentConfiguration` 将观测中间件挂到 HarnessAgent，正常执行仍由 AgentSession 管理。
2. `AgentTracingMiddleware` 在后台执行开始时设置用户、会话及原生 turnId/runId 上下文。父类负责 Agent、模型、工具的 Span，不重写推理或工具执行。
3. `TraceContextSpanProcessor` 将白名单上下文字段附加到各层 Span。`gen_ai.conversation.id=userId:sessionId` 供 Studio 识别会话；独立执行对应不同 Trace。
4. 每个 Span 结束后进入 OTel `BatchSpanProcessor` 内存队列。每秒触发批量导出，最多排队 2048 个，单批最多 512 个，HTTP 超时两秒；SDK 的有界重试行为沿用 OTel 默认设置。满队列或进程异常退出可能丢失未导出的观测数据，不承诺持久补发。
5. `OtlpHttpSpanExporter` 向配置 endpoint 发送 Protobuf，Studio 保存 Trace 和父子 Span 并展示。应用关闭时由 SDK 执行刷新与资源释放。

这里同步的是 Trace，不是日志文件。`log.info/error` 保持原有输出；SSE 独立用于页面实时正文和执行过程；原生 Journal 保存执行事实。本实现不扫描日志文件、不轮询业务数据库、不从 SSE 重建调用树，也不回放启用前的历史。

长时间执行的 Span 通常结束后才上报，工具完成记录可能早于整轮 Agent 完成记录到达；页面实时进度继续由 SSE 提供。网络断开不取消原生执行，所以其 Trace 后续仍可结束。导出失败不改变聊天执行结果。

## 数据边界

默认只采集名称、关联标识、状态、耗时及框架真实返回的用量。没有额外采集完整 Prompt、模型思考正文、工具参数或结果。原生 onActing 仍可能以一批工具为一个 Span，不伪造逐工具粒度。未提供 Token 或价格时不伪造用量和成本。

## 主要代码

- `config/ObservationConfiguration.java`：独立 OTel SDK、批量导出器及关闭生命周期。
- `config/properties/ObservationProperties.java`：开关、接收地址及环境绑定。
- `agent/observer/AgentTracingMiddleware.java`：原生中间件的上下文补充。
- `agent/observer/TraceContextSpanProcessor.java`：逐 Span 传播关联字段。
- `shared/ui/AgentObservationLink.vue`：管理员原生页面入口。

官方依据：

- https://agentscope-ai.github.io/agentscope-studio/zh_CN/develop/tracing.html
- https://github.com/agentscope-ai/agentscope-studio/blob/main/docker/docker-compose.yml
- https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/building-blocks/middleware.md
