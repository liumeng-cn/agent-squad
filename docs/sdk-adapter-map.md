# SDK适配与验证边界

固定版本2.1.0-BETA1，提交5451c73abe47053340683a1cc6d67b290b056648。仅查源码，未编译/执行。下面分清已存在API与本项目需写的适配，避免为想象中的框架接口开发。

| 平台责任 | 固定标签源码入口 | 适配/实验重点 |
| --- | --- | --- |
| Agent构建与关闭 | HarnessAgent.builder/build/session/close | 共享配置、按Binding持有；HTTP返回不close后台Agent |
| 原生日志模式 | Builder 默认 EVENT_LOG；`distributedStore(JdbcDistributedStore.create(dataSource, dialect))` + `filesystem(RemoteFilesystemSpec)` | 复用 Spring `DataSource` 与 SDK 原生 Journal；不开 legacy，不再维护自写的 `SessionLogStore` |
| 持久输入 | AgentSession.submit(key,input)、start | commandId稳定，同key同内容；收据丢失可补 |
| 中断/继续 | interrupt(runId)、resume(turnId)、inspect/pending | resume无应用key；先查inbox待处理命令与Turn.started，再检查Run，不能只看latestRunId |
| 原生ASK | pending、respond(requestId,SessionAnswer) | 用户回复→真实request映射；重复请求按平台Command幂等，原生答复使用原request |
| 未知工具结果 | inspect.uncertainToolCalls、reconcileToolOutcomes | 只能用可信Operation结果补原toolCallId，不重新执行未知写入 |
| 去重投影 | SessionLogExporter、SessionExportSink | accept持久后推进水位；重复eventId无害，业务状态和公开事件同事务 |
| 关闭默认委派 | Builder.disableSubagents | 注册平台持久委派工具，Worker叶子无再派发能力 |
| 关闭默认记忆 | disableMemoryHooks、disableMemoryTools | 两者关闭后再提供显式记忆工具/ContextSource，实验覆盖压缩和背景路径 |
| 共享存储 | RemoteFilesystem、WorkspaceSessionLogStore、StoreSessionStorage | BaseStore必须支持原子CAS；私有日志分区不可被模型工具读取 |
| 模型 | agentscope-extensions-model-openai | DeepSeek配置化；取消、toolcall、超时、计量都在真实适配路径验证 |
| 沙箱 | 框架Filesystem/Sandbox驱动 | 不假设所有驱动都有完整配额/网络限制；未证实的策略不开放 |

## 控制命令的重要限制

AgentSession.resume在调用时生成原生命令ID，inbox会拒绝同Turn未开始的重复resume；但已开始和已结束场景仍需查原始状态。适配器在guard内固定previousRunId，读取SessionInbox.snapshot和SessionTurns.started：已排队则绑定原命令，已有新Run则补回执，未知则阻塞核对。不能把捕获异常后一律resume作为重试策略。

respond以requestId集合派生原生答复ID，pending请求校验先于写入；已消费后再次调用可能拒绝，因此平台重复请求返回自己的原回执，并用已提交interaction/resolved核对，不反复构造新答复。

这些都是原生API查询与调用，不直接修改SDK内部日志。若固定标签不能提供所需查询，D0探针必须暴露缺口并修改设计，不猜测成功。

## 标识编码与长度

框架SessionKey把userId/agentId/sessionId分别做Base64URL后拼成对象key；还要加inbox、writer-seals、exports等路径。不能将三个160字符业务名称直接拼接到item_key VARCHAR(255)。

平台编码约定：userId为UUID36字符；frameworkSessionId为根据task/job/generation确定的UUID36；agentId为32字符摘要编码（逻辑能力名/版本保留在业务定义中）。命名空间使用固定短应用/环境编码及用户ID，禁止显示名称。按实际生成路径校验≤255；namespace校验≤512。长度上限只是SDK适配约束，不裁剪用户业务内容。

源码入口：[AgentSession](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/session/AgentSession.java)、[SessionKey](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-core/src/main/java/io/agentscope/core/session/SessionKey.java)、[SessionInbox](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-core/src/main/java/io/agentscope/core/session/SessionInbox.java)。
