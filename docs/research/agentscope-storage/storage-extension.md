# 存储扩展源码核对

版本：2.1.0-BETA1；Source Jar 根 `/tmp/agentscope-storage-2.1.0-BETA1`。下列路径相对此根。

【源码确认】`agentscope-core/io/agentscope/core/state/AgentStateStore.java:63-147` 定义单值全量覆盖与可选乐观锁。默认 supportsVersioning=false，saveIfVersion 退化到无条件保存；CAS版本不是格式版本。该接口独立于 SessionLog。

【源码确认】`AgentState.java:45-148` 保存 session_id、user_id、summary、context、reply_id、cur_iter、shutdown_interrupted、permission_context、tool_context、tasks_context、plan_mode_context。toJson/fromJsonString 使用 JsonUtils codec；这是执行状态，而非完整日志。

【源码确认】`JsonFileAgentStateStore.java:58-159,316-342` 默认根 ~/.agentscope/state，空用户归 __anon__；root/safe(user)/safe(session)/key.json，列表另有 key.jsonl 与 key.hash。单值临时文件原子替换，列表按前缀散列追加或重写。没有版本能力覆盖。delete(key)只删 json，不能假定删除列表文件。

【源码确认】`agentscope-harness/io/agentscope/harness/agent/filesystem/remote/store/BaseStore.java:28-99` 是 namespace(List<String>)+key → Map 的KV接口，版本能力默认关闭。`RemoteFilesystem.java:149,269-334` 暴露 sessionStorage，write以CAS创建FileData，edit有限CAS重试。

【源码确认】`filesystem/spec/RemoteFilesystemSpec.java:65-80,234-283` 默认USER隔离，可选SESSION/AGENT/GLOBAL；默认后端仍为LocalFilesystem，memory、skills、subagents、knowledge、plans、sessions、tasks使用overlay路由。远程配置不等于所有文件远程化。

## JDBC

【源码确认】`agentscope-extensions-jdbc/io/agentscope/extensions/jdbc/store/JdbcStore.java:47-157,223-242` 实现BaseStore，namespace段用U+001F连接并追加尾分隔符，Jackson将Map编码为字符串。CAS版本0走INSERT竞争，其他版本走条件UPDATE并判断影响行数；每次KV操作单独获取连接，不能推定多个操作组成事务。

【源码确认】`dialect/vendor/PostgresDialect.java:55-67` 的store表字段为namespace_path、item_key、value_json TEXT、version BIGINT、updated_at，联合主键(namespace_path,item_key)。不是JSONB。该方言在通用JDBC模块中。

【源码确认】同文件:102-112 定义State表，主键(session_id,state_key,item_index)，state_data TEXT，version BIGINT DEFAULT 0。`state/JdbcAgentStateStore.java:199-289` 在事务中完成CAS写和新版本读取；它单独实现AgentStateStore，不是BaseStore适配器。

【源码确认】`snapshot/JdbcRemoteSnapshotClient.java:39-85` 实现RemoteSnapshotClient，`JdbcSnapshotSpec.java:28-38` 包装该客户端。PostgresDialect:146-154 的snapshot表为snapshot_id VARCHAR(512)主键、data BYTEA。JDBC覆盖多个协议靠独立实现，不是将一个Store传入就替换所有存储。

## Workspace快照

【源码确认】`agentscope-harness/io/agentscope/harness/agent/sandbox/snapshot/SandboxSnapshot.java:33-109` 的实际Harness接口为SandboxSnapshot，而非SnapshotStore；存储内容是workspace tar流。支持local、remote、noop多态标签。它与AgentState JSON不同。

【源码确认】`LocalSandboxSnapshot.java:45-139` 将流写到basePath/id.tar，临时文件原子替换；restore打开tar。RemoteSnapshotClient只有upload/download/exists，没有版本、过期清理或删除入口。

【源码确认】`sandbox/AbstractBaseSandbox.java:72-136` 的start按目录和快照状态决定保留、恢复或初始化；stop在启用持久化时打包workspace并persist。不能据此推定外部远程路由数据也被打包。

【待验证】其他存储扩展的全部实现和真实集成测试尚未完成；Redis及文件操作的后续核对见下文，本文不将接口存在作为部署能力证明。

## 官方完整 Tag 补充核对

以下证据均相对 `/tmp/agentscope-beta1-official`，Tag v2.1.0-BETA1、Commit `5451c73abe47053340683a1cc6d67b290b056648`。

### Redis 的两个独立协议

【源码确认】`agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/store/RedisStore.java:29-88,132-201,247-279` 实现BaseStore。默认prefix为`agentscope:store:`；namespace各段以NUL连接；item键为`prefix + item: + ns + NUL + key`，Hash中value是Jackson编码Map的JSON，version是long字符串；索引键`prefix + idx: + ns`为全score=0的Sorted Set。put、CAS、delete均通过Lua原子更新数据与索引，CAS不存在版本0。search使用ZRANGEBYLEX，仅索引该精确namespace，并容忍并发删除造成的空Hash。此行为不能等同JdbcStore的namespace前缀搜索。代码中未为这些key配置TTL。

【源码确认】同模块`state/RedisAgentStateStore.java:178-299,435-451,481-508` 实现AgentStateStore，是另一套key和序列化逻辑。slotId=`normalizeUser(userId) + / + sessionId`，空用户为__anon__；单值键`prefix+slotId+":"+key`，列表键再加`:list`，session跟踪集合键再加`:_keys`。getVersioned读取JSON与辅助版本键；saveIfVersion使用RedisStateVersionSupport的Lua。它支持Jedis、Lettuce、Redisson与自定义adapter，而RedisStore直接使用UnifiedJedis。不能因两者都连接Redis而混用key。

【源码推导】Lua原子性可支持同一Redis实例的多进程CAS；这不证明Redis Cluster多键脚本在任意key布局下都可执行，也不证明服务端故障后的持久性。后者依赖Redis部署与恢复配置，未进行真实验证。

### PostgreSQL 独立扩展与通用 JDBC

【源码确认】`agentscope-extensions/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/store/PostgresBaseStore.java:43-106` 是直接implements BaseStore的独立实现，不继承JdbcStore；仍使用TEXT而非JSONB，主键(namespace_path,item_key)，namespace用U+001F并带尾分隔符。它与通用JdbcStore + PostgresDialect是两条可选实现路径，不能视为继承关系。独立扩展提供PostgresAgentStateStore和PostgresDistributedStore。构造器是否初始化表受initializeSchema显式配置控制，不是所有装配都自动建表。

### Admin SnapshotStore 同名异义

【源码确认】`agentscope-extensions/agentscope-spring-boot-starters/agentscope-admin-spring-boot-starter/src/main/java/io/agentscope/spring/boot/admin/snapshot/SnapshotStore.java:28-143` 是final具体类，并非Harness SPI。字段是capacityPerSession(int)、sessions(ConcurrentMap<String,SessionStacks>)；SessionStacks持有undo/redo两个Deque<String>。保存的是AgentState序列化JSON，默认每会话20份，push清空redo，容量溢出移除最旧；undo/redo将当前JSON压入另一栈，clear移除session。同步方法仅保护本JVM，服务重启丢失，无Workspace文件、无JDBC实现、无TTL。源码注释虽建议定制Redis bean，但类本身final，不能据此声称存在可继承的SnapshotStore SPI。

### 目录与隔离的精确规则

【源码确认】`agentscope-harness/src/main/java/io/agentscope/harness/agent/filesystem/spec/RemoteFilesystemSpec.java:295-352` 构建route上层RemoteFilesystem与下层本地模板OverlayFilesystem；远程namespace为下表base再追加routeSegment。

| IsolationScope | base namespace |
|---|---|
| SESSION | agents / agentId / sessions / sessionId，空session为default |
| USER | agents / agentId / users / userId，空用户默认_default |
| AGENT | agents / agentId / shared |
| GLOBAL | global |

【源码确认】同文件:234-283 中memory、skills、subagents、knowledge、plans对应同名本地模板目录；sessions、tasks模板位于agents/agentId/sessions与agents/agentId/tasks；路由不是把所有普通文件放入BaseStore。`WorkspaceManager.java:230-245` 的resolveRuntimeDataPath在namespaceFactory存在时解析为workspace/namespace各段/relativePath，否则为workspace/relativePath。

### 文件结构与操作语义更正

【源码确认】`filesystem/model/FileData.java:26-51` 为record：content(String)、encoding(String)、createdAt(String)、modifiedAt(String)；创建时间用Instant.toString，encoding为utf-8或base64。`filesystem/remote/RemoteFilesystem.java:697-722` 映射成Map键content、encoding、created_at、modified_at。write在:269-279使用expectedVersion=0的CAS，只创建不存在文件，**不是覆盖**；uploadFiles在:503-533调用无条件put，可覆盖。edit在:288-334读取版本后CAS重试。

【源码确认】同文件:601-643 的delete扫描匹配自身或目录前缀的key逐个删除；move逐个put目标然后delete源。移动整个目录不是单事务原子重命名；中途失败可能留下两份或部分完成，需要调用方识别。BaseStore版本只保留当前值与版本号，并不保存可回溯的文件历史。

【源码推导样例】下列仅由fileDataToStoreValue还原，非实际运行输出：

```json
{"content":"example","encoding":"utf-8","created_at":"2026-11-17T00:00:00Z","modified_at":"2026-11-17T00:00:00Z"}
```

【源码推导样例】LocalSandboxSnapshot的descriptor JSON字段由type多态和getter推导为`type=local,basePath=<配置目录>,id=<快照ID>`；实际数据另在basePath/id.tar。RemoteSandboxSnapshot对应type=remote和id，客户端为运行期依赖，不能把descriptor当作tar内容。Admin快照值直接是AgentState JSON，无独立快照Envelope。

## 扩展入口汇总

以下为【源码确认】的公开装配关系，详细实现证据见本篇与 storage-architecture.md；难度为【源码推导】，表示必须承担的协议义务，而非开发工期估计。

| 接口或类型 | 默认实现 | 替换入口 | 维护边界 |
|---|---|---|---|
| SessionLogStore | WorkspaceSessionLogStore | HarnessAgent.Builder.sessionLogStore | 高：自实现须保留可提交前缀、fencing、Inbox、发现能力；仅open不等于完整能力 |
| AtomicSessionStorage | LocalSessionStorage，由Filesystem决定 | JournalSessionLog构造器；Filesystem.sessionStorage | 高：真正跨进程原子CAS与确认后持久化，不可用普通覆盖模拟 |
| AgentStateStore | 原生journal模式不隐式启用；需要时JsonFileAgentStateStore | Builder.agentStateStore或DistributedStore | 中：单值、列表、用户隔离和可选版本协议 |
| SandboxSnapshot | 依sandbox配置选择noop/local/remote | SnapshotSpec与RemoteSnapshotClient | 中：归档流保存读取，不负责事件日志 |
| Admin SnapshotStore | JVM内存undo/redo | 非Harness存储SPI | final具体类，不应当作通用后端扩展接口 |
| AbstractFilesystem | LocalFilesystemSpec构建本地实现 | Builder.filesystem与FilesystemSpec | 高：路径隔离、普通文件语义和sessionStorage分别落实 |
| BaseStore | 远程模式需提供，非全局隐式默认 | RemoteFilesystemSpec与DistributedStore | 中到高：namespace、搜索、CAS、删除与持久性必须按实现核对 |

【源码推导】仅实现基础抽象接口中的必选方法仍可能缺少运行功能：SessionLogStore默认list抛异常、SessionLog默认inbox或seal可不支持、BaseStore默认版本能力关闭。替换时必须检查使用链路调用了哪些可选协议，不能只判断Java类型可赋值。

## 交叉审核补正与完整物理字段

【源码确认】JdbcAgentStateStore的用户隔离并非独立user_id列：`state/JdbcAgentStateStore.java:552-579` 将userId空值归`__anon__`，拼成`userId + ":" + sessionId`写入物理session_id，长度上限255。与Redis状态的`user/session`规则不同。该方法未转义冒号，因而不能仅凭API三元组宣称任意字符串输入都具有无歧义隔离，例如(a:b,c)与(a,b:c)产生相同slot；此为源码推导的键碰撞边界，不是已运行安全测试。

【源码确认】PostgresDialect的完整DDL字段如下（`dialect/vendor/PostgresDialect.java:55-74,102-121,146-154`），表名由方言配置提供，下面使用逻辑名称说明：

| 表域 | 完整字段 | 约束与索引 |
|---|---|---|
| Store | namespace_path VARCHAR(2048) NOT NULL；item_key VARCHAR(255) NOT NULL；value_json TEXT NOT NULL；version BIGINT NOT NULL；updated_at BIGINT NOT NULL | PK(namespace_path,item_key)；namespace_path索引 |
| State | session_id VARCHAR(255) NOT NULL；state_key VARCHAR(255) NOT NULL；item_index INT NOT NULL DEFAULT 0；state_data TEXT NOT NULL；version BIGINT NOT NULL DEFAULT 0；created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP；updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP | PK(session_id,state_key,item_index)；session_id索引 |
| Snapshot | snapshot_id VARCHAR(512) NOT NULL；data BYTEA NOT NULL；created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP | PK(snapshot_id) |

【源码确认】State单值item_index=0；列表使用按索引存储的数据及`key + ":_hash"`辅助条目；JdbcAgentStateStore:52-53,99-141。save空列表直接返回，不执行清空。因此“全量替换”是单值契约，不应套用到列表空值。

【源码确认】快照默认必须沿Spec装配判定，不能将SandboxSnapshot接口当默认实例。`H/filesystem/spec/SandboxFilesystemSpec.java:45-54,67-73,117` 采用显式override或子类snapshotSpec；`H/sandbox/impl/docker/DockerFilesystemSpec.java:35,110` 默认NoopSnapshotSpec；`H/HarnessAgent.java:2564-2565` 在distributedStore存在且无override时注入其sandboxSnapshotSpec。LocalSnapshotSpec.build:50-53与RemoteSnapshotSpec.build:48-50才创建具体snapshot对象。没有配置sandbox不等于自动创建本地快照。

【源码确认】RemoteFilesystem.write:269-279直接使用传入filePath作为KV key；不能笼统声称所有入口都先规范化。delete/move明确normalizePath，但write/upload与路由入口有各自路径处理；调用者应遵守Filesystem约定，完整跨入口路径等价性未运行验证。
