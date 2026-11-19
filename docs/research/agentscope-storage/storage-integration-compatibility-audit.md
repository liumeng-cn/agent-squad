# 接入 默认装配与兼容性逐项审计

目标源码为官方 Tag 2.1.0-BETA1、Commit `5451c73abe47053340683a1cc6d67b290b056648`。以下 S 为 `agentscope-harness/src/main/java/io/agentscope/harness/agent/`，C 为 `agentscope-core/src/main/java/io/agentscope/core/`，P 为项目 `server/v2-server/src/main/java/com/agentsquad/squad/v2/`；它们是精确目录前缀，表内前缀加文件名即完整仓库路径。此处只审计代码，无数据库或配置修改。

## V2 接入十一问

| 问题 | 审计结论与方法位置 |
|---|---|
| 1 Harness 构建 | 【源码确认】P/config/ChatAgentConfiguration.java:40-68 chatAgent()，enabled 条件 Bean，设置模型/工具/agentId/workspace，然后 build |
| 2 SessionLogStore | 【源码确认】同一方法未设置；S/HarnessAgent.java:2724-2727 Builder.build 默认 WorkspaceSessionLogStore |
| 3 Workspace | 【源码确认】P/config/ChatAgentConfiguration.java:65 显式 Path；P/config/properties/ChatAgentProperties.java:58 默认本机绝对路径；application-local.yml:22 同值 |
| 4 AtomicSessionStorage | 【源码确认】S/session/WorkspaceSessionLogStore.java:93-97 open 调 Filesystem.sessionStorage；S/filesystem/local/LocalFilesystem.java:597-599 返回 LocalSessionStorage |
| 5 AgentStateStore | 【源码确认】V2 builder 未指定；S/HarnessAgent.java:2586-2604 原生非 sandbox 非 legacy 不隐式启用；C/ReActAgent.java:722-750 activateSlotForContext 使用 recorder projection 恢复 |
| 6 SnapshotStore | 【源码确认】V2 builder 无 sandbox/snapshot 配置；不得将 Journal state/checkpoint 当成独立 SnapshotStore 接入 |
| 7 BaseStore | 【源码确认】P/config/ChatAgentConfiguration.java:59-68 无 distributedStore/remote spec/BaseStore；默认本地 Filesystem |
| 8 与默认差异 | 【源码确认】显式 agentId 和 workspace、模型、工具、子 Agent；没有覆盖原生 Journal 默认。默认 Filesystem 解析见 S/HarnessAgentBuilderSupport.java:147-167 resolveFilesystem |
| 9 已持久化数据 | 【源码确认】P/service/ChatService.java:32-64 send 调原生 submit/steer；原生写入 Journal 与 Inbox。checkpoint 恢复见 C/session/SessionProjection.java:46-115；本次实际样例见 runtime-samples.json，不能替代当前 HTTP 集成证明 |
| 10 本地文件或内存 | 【源码确认】Journal 与普通文件本地；P/service/ChatSessionService.java:24-58 list/create/rename 返回 classpath fixture、写操作仅回显。执行句柄与 JVM synchronized 是进程内协调 |
| 11 多实例能力 | 【源码推导】当前本机路径未配置共享存储；不能宣称跨机器可恢复。共享目录可达、CAS实现和工具外部副作用是不同验收层次，均未由当前配置证明 |

## 默认装配六问

| 问题 | 结论 |
|---|---|
| SessionLogStore 默认 | 【源码确认】S/HarnessAgent.java:2724-2727 WorkspaceSessionLogStore，显式 sessionLogStore 优先 |
| Filesystem 默认 | 【源码确认】S/HarnessAgentBuilderSupport.java:147-167 resolveFilesystem，无 spec 使用 new LocalFilesystemSpec().toFilesystem |
| Atomic 默认 | 【源码确认】非全局固定实例；本地 S/filesystem/local/LocalFilesystem.java:597-599 LocalSessionStorage，远程 S/filesystem/remote/RemoteFilesystem.java:149-150 StoreSessionStorage |
| AgentStateStore 默认 | 【源码确认】S/HarnessAgent.java:2586-2604，只有 legacy/sandbox 需要且无 override 才 new JsonFileAgentStateStore；原生 Journal 是另一恢复路径 |
| SnapshotStore 默认 | 【源码确认】不存在与上述 SessionLogStore 对等的统一默认注入。S/HarnessAgent.java:2564-2571 仅 sandbox 配置下从 distributedStore 获取 sandboxSnapshotSpec，不能推出普通本地 Harness 自动创建沙箱快照 |
| 默认配置与隐式装配 | 【源码确认】S/HarnessAgent.java:2553-2578 Builder.build 在 distributedStore 非空时按条件注入，优先级 explicit > distributedStore > workspace。V2 是普通 @Bean 手动 builder；未发现该调用绕过 builder 注入另一存储 |

## 升级兼容十问

| 问题 | 结论与证据 |
|---|---|
| 1 Session schemaVersion | 【源码确认】C/session/SessionEvent.java:24-35 有 int schemaVersion 且只接受1；不是接受任意未来版本 |
| 2 Commit 是否版本化 | 【源码确认】C/session/JournalSessionLog.java:41-48 Commit record 没有 schemaVersion；存储 CAS version、事件 schemaVersion、Commit 格式版本是三件事 |
| 3 AgentState 迁移 | 【源码确认】C/state/LegacyStateLoader.java:25-34,53-76,119-145 显式读取旧 memory_messages/toolkit_activeGroups 并构造状态，不原地修改，不在正常激活自动执行 |
| 4 Snapshot 兼容 | 【源码推导】须分别看沙箱基础设施与 admin snapshot；没有统一 SDK 全数据版本迁移保证。不能借 Journal schemaVersion 推导 Snapshot 兼容 |
| 5 Workspace 格式版本 | 【源码确认】本地普通文件 API 不强制全局格式版本；S/filesystem/local/LocalFilesystem.java:597-599 单独划定 Journal runtime 子目录，不能把其 CAS前缀当所有文件版本 |
| 6 JSON 改变处理 | 【源码确认】C/util/JacksonJsonCodec.java:71-75 默认关闭 FAIL_ON_UNKNOWN_PROPERTIES；C/state/AgentState.java:95-125 显式 JsonCreator 字段、缺省构建。忽略未知字段可能丢弃未来字段，并不完成迁移 |
| 7 升级是否影响历史读取 | 【源码推导】会受事件版本验证、Msg/ContentBlock 类型映射、状态字段及恢复语义影响；不能仅以相同DB列声明兼容 |
| 8 不同版本共库 | 【源码推导】未见允许任意版本并发共享的协商机制。旧读者忽略新字段后重写会丢信息；CAS只能防并发覆盖，不能保证语义兼容 |
| 9 迁移工具 | 【源码确认】C/session/SessionMigration.java:29-72 importBaseline、82-101 exportSnapshot、104-124 fork。不是全历史格式升级器；导入只写 baseline_only，目的必须空或同hash幂等；导出要求暂停并核对未决执行与交互 |
| 10 脱离 SDK | 【源码推导】普通文本可直接复用；事件及结构化Msg需解析类型/版本、提交链与blob；AgentState 的 context可提取消息但任务/权限/工具上下文具有框架语义，迁移 baseline 不能补造完整过去事件 |

## JSON 与 Msg 的具体边界

【源码确认】C/message/Msg.java:54-66 同时有 ignoreUnknown=true、基于 role 的 USER/ASSISTANT/SYSTEM/TOOL 子类型和 defaultImpl=Msg.class。此 defaultImpl 不是“未来角色任意可用”的承诺：role 自身字段与业务校验仍可能拒绝陌生值。C/message/ContentBlock.java:46-56 按 type 声明 text/thinking/image/audio/video/tool_use/tool_result/hint/data；没有同样声明的 defaultImpl。C/message/Source.java:27-30 只有 url/base64 类型。未知普通属性与未知多态 type 是不同失败面，前者忽略不能保证后者成功。

【源码确认】C/state/AgentState.java:250-258 toJson/fromJsonString 走 JsonUtils.getJsonCodec；C/util/JacksonJsonCodec.java:62-75 支持自定义 ObjectMapper，因此上述 unknown-field 默认只适用于默认 codec，不能替应用自定义配置保证行为。

【源码推导】物理 TEXT/JSONB 或byte[]保持不变只证明容器能存；unknown-field容忍证明有限语法容忍；writer/turn/tool语义与历史迁移完整性需要独立验证。当前没有跨版本双向读写实测，不将“可反序列化”表述为“可安全继续执行”。

## 迁移工具扫描范围与负面结论边界

【源码确认】已在目标 Tag 全仓库按文件名 migration/migrate 及 Java 符号 migration/baseline、LegacyStateLoader、SessionMigration 扫描；核心运行数据明确入口是上述两个类。`agentscope-service/service-controlplane/internal/store/postgres/migrate.go` 和 migrations/*.sql 属于官方服务控制平面数据库升级，不是 Harness Journal/AgentState 通用历史转换工具；Spring admin SnapshotStore 属于另一子系统。未发现全版本任意 Journal 格式自动升级 CLI；这属于本次目标仓库扫描结论，不是对所有外部工具的断言。

【源码确认】SessionMigration.exportSnapshot 在 C/session/SessionMigration.java:82-101 获取 writer 后，拒绝 uncertainToolCalls、activeRuns、pending interactions 或无可恢复 baseline；fork:104-124 重写 user_id/session_id 后 importBaseline。它保留来源归档，通过 source/sourceVersion/sourceHash 留来源信息，不复制原完整事件链。LegacyStateLoader 缺少旧权限上下文时需要调用者显式给定；不能静默宣称旧权限语义全部保留。
