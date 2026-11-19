# 版本兼容性边界

研究版本为2.1.0-BETA1，准确源码位置参见storage-extension.md。

【源码确认】AgentStateStore的version和BaseStore的StoreItem.version用于并发CAS，不是schemaVersion。证据：core/state/AgentStateStore.java:63-147、jdbc/store/JdbcStore.java:114-157。

【源码确认】AgentState.fromJson对缺失字段使用Builder默认值，见core/state/AgentState.java:98-148。这只能证明当前类对部分缺失字段的容忍，不能证明字段改名、Msg子类型变化或运行语义兼容。

【源码确认】SandboxSnapshot以type标记noop/local/remote，LocalSandboxSnapshot启用ignoreUnknown；见harness/agent/sandbox/snapshot/SandboxSnapshot.java:33-39和LocalSandboxSnapshot.java:45-65。该持久化接口没有声明tar内部格式版本。

| 兼容层面 | 已知事实 | 尚不能证明 |
|---|---|---|
| 数据库物理格式 | PostgresDialect明确TEXT/BYTEA与主键 | 内容可恢复 |
| 原生JSON | AgentState显式snake_case字段及默认值 | 消息与嵌套类型跨版本兼容 |
| 运行语义 | 当前版本能执行其恢复路径 | 新版本工具权限任务语义一致 |
| 数据迁移 | SessionMigration提供baseline导入导出与fork；LegacyStateLoader提供旧状态读取 | 任意版本全历史自动升级 |

【源码推导】脱离SDK时普通文件文本与tar内容可提取；AgentState与Session event仍须保留目标版本消息、内容块、事件定义并重新解析，JSON本身不能恢复执行器行为。

【源码推导】跨版本共享存储与滚动升级的兼容承诺不能由本版本单独证明。源码已明确版本验证、类型映射及迁移边界，研究问题已有回答；尚未运行跨版本双向继续执行验收。目标版本真实样例见 runtime-samples.json；本次还完成官方215项测试及独立三JVM强杀、租约与fencing验证，具体命令与输出见主研究验证记录。这些证据证明目标版本行为，不替代跨版本兼容验收。

## 已补充的扩展证据

【源码确认】官方Tag的RedisStore通过Lua维护Hash中的version；PostgresBaseStore通过版本条件UPDATE。这两类都仅保留当前值，没有历史版本回溯，也没有JSON模型迁移。路径与行号见storage-extension.md补充节。

【源码确认】Admin SnapshotStore只保存进程内AgentState JSON的undo/redo堆栈，不是持久化迁移工具。恢复仍依赖目标版本的AgentState反序列化。SnapshotStore:28-143。

【源码推导】不同实现都能存Map并不表示数据可直接跨后端搬迁：JdbcStore namespace编码使用U+001F并带尾分隔符，RedisStore使用NUL且不带尾分隔符；JSON内容相似也需要分别迁移namespace、索引及版本元数据。远程文件的created_at/modified_at属于文件元数据，不能充当格式版本。

## 会话格式 事件类型与完整性校验

【源码确认】完整路径 `agentscope-core/src/main/java/io/agentscope/core/session/SessionHeader.java:19-29`：SessionHeader字段为formatVersion、authority、replayCoverage、createdAt；current()返回1/event_log/adapter_chunks，validate()拒绝非1格式或非event_log权威。`agentscope-core/src/main/java/io/agentscope/core/session/JournalSessionLog.java:84,110` 在读取/初始化header处执行校验。这是明确格式闸门，不能只写Session没有格式版本。

【源码确认】`agentscope-core/src/main/java/io/agentscope/core/session/SessionEvent.java:24-35` 独立校验schemaVersion=1。`agentscope-core/src/main/java/io/agentscope/core/session/SessionEventCodecRegistry.java:50-54` validate(event) 对已注册事件执行其payload校验；未注册且required=true且不在BUILTIN集合时抛Unknown required event。未知可选事件不因这一条件被拒绝，但不表示旧版本可理解其运行语义。

【源码确认】`agentscope-core/src/main/java/io/agentscope/core/session/JournalSessionLog.java:41-48` Commit没有单独schemaVersion。读取提交payload时281-286行根据payloadBlobs映射加载blob，缺失或hash不符抛Missing or corrupt committed payload blob。只有Commit JSON而没有被引用blob，不能视为完成迁移；这属于数据完整性错误，应与不支持schema版本的兼容错误分开判断。

## JSON类型与迁移入口

【源码确认】`agentscope-core/src/main/java/io/agentscope/core/util/JacksonJsonCodec.java:71-75` 默认忽略未知属性，`agentscope-core/src/main/java/io/agentscope/core/message/Msg.java:54-66` 同时声明ignoreUnknown、按role的四种子类与defaultImpl=Msg.class。`agentscope-core/src/main/java/io/agentscope/core/message/ContentBlock.java:46-56` 按type识别九种内容块，`agentscope-core/src/main/java/io/agentscope/core/message/Source.java:27-30` 识别url/base64，没有同样defaultImpl。未知属性可以忽略不等于未知多态type可以恢复；旧读者重写时也可能丢弃未来字段。

【源码确认】完整仓库迁移扫描发现 `agentscope-core/src/main/java/io/agentscope/core/state/LegacyStateLoader.java:25-34,119-145` 显式读取旧memory_messages/toolkit_activeGroups，不原地改源；`agentscope-core/src/main/java/io/agentscope/core/session/SessionMigration.java:29-72` importBaseline写baseline_only与source/sourceVersion/sourceHash，仅接受空目标或相同hash幂等。82-101行exportSnapshot要求源暂停，并拒绝未决工具、active runs、pending interactions或无baseline。104-124行fork调整user/session再导入；不复制完整历史事件链。

【源码确认】官方 `agentscope-service/service-controlplane/internal/store/postgres/migrate.go` 与migrations/*.sql属于控制平面SQL迁移，不能当作Harness Journal通用升级器。此次目标Tag全仓按migration/migrate及相关符号扫描，未发现任意版本全历史自动升级CLI；已找到上述公开迁移原语，不能再写“迁移工具尚未扫描”或“无迁移入口”。逐问证据见 storage-integration-compatibility-audit.md。

【源码确认】MongoRemoteSnapshotClient:55-194提供GridFS缺失时回退旧集合{_id,data:Binary}的下载/存在判断及双位置删除。这是目标版本真实的局部格式兼容措施，不能据其他通用接口无migration方法断言整个SDK完全没有迁移兼容。完整路径见storage-backend-inventory.md。
