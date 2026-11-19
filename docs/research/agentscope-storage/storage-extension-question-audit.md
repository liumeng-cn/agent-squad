# 扩展研究逐题核对

目标Tag及源码根与storage-extension.md相同。下文路径简写：C=agentscope-core/src/main/java/io/agentscope/core；H=agentscope-harness/src/main/java/io/agentscope/harness/agent；J=agentscope-extensions/agentscope-extensions-jdbc/src/main/java/io/agentscope/extensions/jdbc；R=agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis。所有“无”结论限定于所指接口和目标版本实现。

## AgentState九问

1. 【源码确认】保存对话缓冲、summary、replyId、迭代计数、shutdownInterrupted及permission/tool/tasks/planMode上下文。C/state/AgentState.java:45-148，构造与fromJson。
2. 【源码确认】它是当前可变执行状态；日志记录持久化事件，不能将当前context等同完整历史。H/HarnessAgent.java:2596-2603明确原生会话仅从journal恢复。
3. 【源码确认】单值save全量替换；列表由后端决定。JsonFile列表按前缀散列追加或重写。C/state/AgentStateStore.java:65-180；JsonFileAgentStateStore.save:104-141。
4. 【源码确认】寻址三元组(userId,sessionId,key)，并非只有sessionId。AgentStateStore.save/get:79-198。
5. 【源码确认】文件实现root/safe(user)/safe(session)/key.json，匿名__anon__；Redis实现slot=user/session。C/state/JsonFileAgentStateStore.getSessionDir:316-342；R/state/RedisAgentStateStore.slotId:439-451。
6. 【源码确认】get/getList按指定类型反序列化；原生Harness恢复不依赖独立StateStore，仅legacy或sandbox元数据才需要。H/HarnessAgent.Builder.build:2585-2603。
7. 【源码确认】接口支持可选CAS，默认降级。J/state/JdbcAgentStateStore.saveIfVersion:199-289在事务中处理，Redis通过Lua，文件不支持版本。不能笼统称所有StateStore都支持多实例。
8. 【源码确认】UNVERSIONED=-1，空记录version=0；这是并发版本，不是schema。C/state/AgentStateStore:63-147。
9. 【源码确认】可连接同一数据库，但协议与存储行不同；AtomicSessionStorage通过BaseStore/StoreSessionStorage操作字节，AgentStateStore独立实现。J/store/JdbcStore:47，J/state/JdbcAgentStateStore:48；H/session/StoreSessionStorage:27-74。

## Snapshot十问

1. 【源码确认】Harness SandboxSnapshot保存workspace tar，Admin SnapshotStore保存AgentState JSON撤销栈。H/sandbox/snapshot/SandboxSnapshot:33-109；admin starter snapshot/SnapshotStore:28-143。
2. 【源码确认】AgentState是运行状态JSON，workspace快照是文件归档；Admin快照反而直接采用AgentState JSON。以上同处。
3. 【源码确认】快照不充当journal commit/head，也不提供事件扫描；SandboxSnapshot仅persist/restore/isRestorable/getId等方法。
4. 【源码确认】SandboxSnapshot包含调用方提供的workspace归档；远程BaseStore路由内容不会仅凭目录名自动包含。H/sandbox/AbstractBaseSandbox.stop:127-139。
5. 【源码确认】LocalSandboxSnapshot.persist将输入流全量替换id.tar，没有增量合并。H/sandbox/snapshot/LocalSandboxSnapshot:71-105。
6. 【源码确认】AbstractBaseSandbox.start:72-115检查workspace和快照可恢复性，restore→doHydrateWorkspace；不是读取AgentState JSON恢复文件。
7. 【源码确认】通用快照接口无过期清理/删除；RedisRemoteSnapshotClient构造可配置TTL，upload先SET后EXPIRE。R/snapshot/RedisRemoteSnapshotClient:31-55。这两个命令非同一原子操作，故不能承诺上传与TTL绑定原子性。
8. 【源码确认】RemoteSnapshotClient公开upload/download/exists；OSS/COS/Redis/Mongo/JDBC等实现存在。详见清单。
9. 【源码确认】J/snapshot/JdbcRemoteSnapshotClient:39-85和JdbcSnapshotSpec:28-38提供支持，方言SnapshotDialect定义二进制列；Postgres为BYTEA。
10. 【源码确认】SandboxSnapshot type区分local/remote/noop，未声明tar格式迁移；Local实现ignoreUnknown不等于跨版本兼容。SandboxSnapshot:33-39；LocalSandboxSnapshot:45-65。

## Workspace十四问

1. 【源码确认】WorkspaceManager以workspace Path和Filesystem管理模板与运行数据文件。H/workspace/WorkspaceManager.resolveRuntimeDataPath:230-245。
2. 【源码确认】一个workspace可包含多个会话；路径由RuntimeContext身份和namespace决定，不要求每会话独占整个根目录。同上。
3. 【源码确认】Filesystem提供访问行为；WorkspaceManager解析业务路径并使用filesystem，物理介质取决于后端。RemoteFilesystemSpec.toFilesystem:234-283。
4. 【源码确认】默认远程overlay路由memory/skills/subagents/knowledge/plans及agents/<agentId>/sessions、tasks模板。H/filesystem/spec/RemoteFilesystemSpec:259-278。
5. 【源码确认】Memory、Plan、Skills位于各自路由；普通文件走默认LocalFilesystem，不能称整个workspace都在BaseStore。同上。
6. 【源码确认】SESSION=[agents,agentId,sessions,sid]；USER=[agents,agentId,users,uid]；AGENT=[agents,agentId,shared]；GLOBAL=[global]，再追加route。RemoteFilesystemSpec.storeNamespace:332-352。Local路径另外由namespaceFactory解析，不能直接套远程tuple。
7. 【源码确认】LocalFilesystem使用本地NIO路径及实际文件；具体读写结果不自动变成BaseStore条目。H/filesystem/local/LocalFilesystem，方法read/write/edit/delete/move；完整方法行号须见本报告后附自动索引。
8. 【源码确认】RemoteFilesystem将文件路径当key（write直接使用传入filePath，不能笼统认为所有入口均规范化），FileData映射Map，通过BaseStore访问。H/filesystem/remote/RemoteFilesystem:269-334,697-722。
9. 【源码确认】BaseStore提供KV、版本、搜索、删除；不是操作系统文件接口。H/filesystem/remote/store/BaseStore:28-99。
10. 【源码确认】通过RemoteFilesystemSpec(BaseStore)或store注入；RedisStore/JdbcStore/PostgresBaseStore均实现BaseStore。RemoteFilesystemSpec:96-123。
11. 【源码确认】ls按已有键生成目录视图，read取FileData.content按行截取；没有独立目录对象。RemoteFilesystem.ls/read:162-267。
12. 【源码确认】delete按前缀逐项删；move先put目标再删源，非目录原子事务；write CAS0只创建、upload覆盖。RemoteFilesystem:269-279,503-533,601-643。
13. 【源码确认】BaseStore version用于当前值CAS，无历史文件版本检索接口；不能称支持可回溯文件版本。BaseStore:28-99。
14. 【源码推导】远程BaseStore可让多个进程看到共享条目，CAS可保护单项更新；普通本地文件仍依赖共享挂载或独立副本，远程配置不自动解决所有workspace多实例问题。RemoteFilesystemSpec.toFilesystem及RemoteFilesystem.move上述证据。

## JDBC与Redis十问

1. 【源码确认】JdbcStore→BaseStore；JdbcAgentStateStore→AgentStateStore；JdbcRemoteSnapshotClient→RemoteSnapshotClient，另有skill repository与sandbox execution guard。J对应类声明。
2. 【源码确认】通用JDBC内PostgresDialect是方言；独立PostgresBaseStore是直接实现BaseStore，非JdbcStore子类。两个路径并存，不能混用初始化和表配置。
3. 【源码确认】Store表namespace_path,item_key,value_json,version,updated_at，联合主键(namespace_path,item_key)。J/dialect/vendor/PostgresDialect.storeCreateTableDdls:55-67。
4. 【源码确认】Postgres store JSON=TEXT，State=TEXT，snapshot=BYTEA；不是JSONB。PostgresDialect:55-67,102-112,146-154。
5. 【源码确认】namespace段U+001F连接带尾分隔符，key映射item_key；不允许段包含分隔符。JdbcStore.namespacePath:223-242。
6. 【源码确认】期望版本0走INSERT并把唯一键冲突视为失败；其他版本条件UPDATE，以影响行数判定。JdbcStore.putIfVersion:122-157。
7. 【源码确认】单KV原子SQL不同于跨条目事务；State saveIfVersion专门在事务内写后读version。JdbcAgentStateStore:199-289。
8. 【源码确认】有CAS版本，无格式schema迁移承诺。见State/Store上述证据。
9. 【源码确认】RedisStore、RedisAgentStateStore及其客户端变体、RedisRemoteSnapshotClient分别覆盖KV、状态、归档。Lua原子性只针对对应脚本，不涵盖全Agent执行外部副作用。
10. 【源码确认】不能将一个JdbcStore直接作为所有接口传入；需要各协议实现与装配。即便数据库统一也不代表工作目录默认本地文件已远程化。

## 官方后端实现全量声明扫描

【源码确认】扫描目标Tag的core/harness/extensions生产Java声明；下列为源码声明匹配结果，不意味着每个后端都通过相同的CAS及故障测试。类名相同但包不同者必须分开看。

```text
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-oss/src/main/java/io/agentscope/extensions/oss/OssAgentStateStore.java:66:public class OssAgentStateStore implements AgentStateStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-mongodb/src/main/java/io/agentscope/extensions/mongodb/store/MongoBaseStore.java:57:public class MongoBaseStore implements BaseStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-jdbc/src/main/java/io/agentscope/extensions/jdbc/store/JdbcStore.java:47:public class JdbcStore implements BaseStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/store/PostgresBaseStore.java:73:public class PostgresBaseStore implements BaseStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-oss/src/main/java/io/agentscope/extensions/oss/OssBaseStore.java:62:public class OssBaseStore implements BaseStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-oss/src/main/java/io/agentscope/extensions/oss/OssSnapshotSpec.java:26:public class OssSnapshotSpec extends RemoteSnapshotSpec {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-controlplane/src/main/java/io/agentscope/extensions/controlplane/store/ControlPlaneBaseStore.java:39:public final class ControlPlaneBaseStore implements BaseStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/state/PostgresAgentStateStore.java:58:public class PostgresAgentStateStore implements AgentStateStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-oss/src/main/java/io/agentscope/extensions/oss/OssRemoteSnapshotClient.java:28:public class OssRemoteSnapshotClient implements RemoteSnapshotClient {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-controlplane/src/main/java/io/agentscope/extensions/controlplane/store/ControlPlaneSnapshotSpec.java:37:public final class ControlPlaneSnapshotSpec extends RemoteSnapshotSpec {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-controlplane/src/main/java/io/agentscope/extensions/controlplane/store/ControlPlaneSnapshotSpec.java:43:    private static final class ControlPlaneRemoteSnapshotClient implements RemoteSnapshotClient {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-mongodb/src/main/java/io/agentscope/extensions/mongodb/state/MongoAgentStateStore.java:100:public class MongoAgentStateStore implements AgentStateStore, AutoCloseable {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/snapshot/PostgresSnapshotSpec.java:27:public class PostgresSnapshotSpec extends RemoteSnapshotSpec {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/snapshot/PostgresRemoteSnapshotClient.java:39:public class PostgresRemoteSnapshotClient implements RemoteSnapshotClient {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-mongodb/src/main/java/io/agentscope/extensions/mongodb/snapshot/MongoSnapshotSpec.java:28:public class MongoSnapshotSpec extends RemoteSnapshotSpec {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-mongodb/src/main/java/io/agentscope/extensions/mongodb/snapshot/MongoRemoteSnapshotClient.java:55:public class MongoRemoteSnapshotClient implements RemoteSnapshotClient {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-mysql/src/main/java/io/agentscope/extensions/mysql/store/JdbcStore.java:74:public class JdbcStore implements BaseStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-cos/src/main/java/io/agentscope/extensions/cos/CosBaseStore.java:68:public class CosBaseStore implements BaseStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-cos/src/main/java/io/agentscope/extensions/cos/CosSnapshotSpec.java:25:public class CosSnapshotSpec extends RemoteSnapshotSpec {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-cos/src/main/java/io/agentscope/extensions/cos/CosRemoteSnapshotClient.java:32:public class CosRemoteSnapshotClient implements RemoteSnapshotClient {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-cos/src/main/java/io/agentscope/extensions/cos/CosAgentStateStore.java:71:public class CosAgentStateStore implements AgentStateStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-jdbc/src/main/java/io/agentscope/extensions/jdbc/snapshot/JdbcRemoteSnapshotClient.java:39:public class JdbcRemoteSnapshotClient implements RemoteSnapshotClient {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-mysql/src/main/java/io/agentscope/extensions/mysql/state/MysqlAgentStateStore.java:74:public class MysqlAgentStateStore implements AgentStateStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-jdbc/src/main/java/io/agentscope/extensions/jdbc/snapshot/JdbcSnapshotSpec.java:28:public class JdbcSnapshotSpec extends RemoteSnapshotSpec {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-jdbc/src/main/java/io/agentscope/extensions/jdbc/state/JdbcAgentStateStore.java:48:public class JdbcAgentStateStore implements AgentStateStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-mysql/src/main/java/io/agentscope/extensions/mysql/snapshot/JdbcRemoteSnapshotClient.java:39:public class JdbcRemoteSnapshotClient implements RemoteSnapshotClient {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-mysql/src/main/java/io/agentscope/extensions/mysql/snapshot/JdbcSnapshotSpec.java:27:public class JdbcSnapshotSpec extends RemoteSnapshotSpec {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/store/RedisStore.java:57:public class RedisStore implements BaseStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/state/jedis/JedisAgentStateStore.java:58:public class JedisAgentStateStore implements AgentStateStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/state/RedisAgentStateStore.java:178:public class RedisAgentStateStore implements AgentStateStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/state/redisson/RedissonAgentStateStore.java:62:public class RedissonAgentStateStore implements AgentStateStore {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/snapshot/RedisRemoteSnapshotClient.java:29:public class RedisRemoteSnapshotClient implements RemoteSnapshotClient {
/tmp/agentscope-beta1-official/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/snapshot/RedisSnapshotSpec.java:25:public class RedisSnapshotSpec extends RemoteSnapshotSpec {
/tmp/agentscope-beta1-official/agentscope-harness/src/main/java/io/agentscope/harness/agent/filesystem/remote/store/InMemoryStore.java:34:public class InMemoryStore implements BaseStore {
/tmp/agentscope-beta1-official/agentscope-core/src/main/java/io/agentscope/core/state/JsonFileAgentStateStore.java:69:public class JsonFileAgentStateStore implements AgentStateStore {
/tmp/agentscope-beta1-official/agentscope-core/src/main/java/io/agentscope/core/state/InMemoryAgentStateStore.java:46:public class InMemoryAgentStateStore implements AgentStateStore {
```

## 本地文件方法索引

```text
238:    public LsResult ls(RuntimeContext runtimeContext, String path) {
275:    public ReadResult read(RuntimeContext runtimeContext, String filePath, int offset, int limit) {
325:    public WriteResult write(RuntimeContext runtimeContext, String filePath, String content) {
348:    public EditResult edit(
393:    public GrepResult grep(
414:    public GlobResult glob(RuntimeContext runtimeContext, String pattern, String path) {
529:    public WriteResult delete(RuntimeContext runtimeContext, String path) {
558:    public WriteResult move(RuntimeContext runtimeContext, String fromPath, String toPath) {
```

【待验证】上述各后端实现存在性已按准确Tag扫描，未把其所有集成测试都运行。真实服务故障持久性、对象存储条件写配置、数据库隔离级别、Redis集群脚本槽位仍需独立运行证据，不能由声明扫描推定。

## 独立交叉审核补正

- 【源码确认】AgentState第5问补充：JDBC物理session_id不是裸会话ID，而是`normalizeUser(userId)+":"+sessionId`；`J/state/JdbcAgentStateStore.slotId/validateSlotId:552-579`。用户字段未独立成列，冒号未转义，任意字符串可产生拼接歧义；不可承诺无条件强用户隔离。
- 【源码确认】Snapshot第1、9问补充：默认由SandboxFilesystemSpec的override/子类工厂决定；DockerFilesystemSpec:35默认NoopSnapshotSpec；HarnessAgent:2564-2565可注入distributedStore提供的Spec。接口本身没有“默认快照”。
- 【源码确认】Workspace第8问措辞修正：write直接将filePath用作key；delete/move才在方法内明确normalizePath，不能把路径规范化表述为所有入口共有行为。RemoteFilesystem.write:269-279、delete/move:601-643。
- 【源码确认】JDBC第3、4问完整DDL字段已补于storage-extension.md最后一节，包括State.created_at/updated_at和Snapshot.created_at，避免把关键列摘要误读为完整DDL。
- 【源码确认】AgentState第3问补充：JdbcAgentStateStore.save(List):99-106对空列表直接返回，不删除旧列表。文件后端也不能根据统一接口名称推断相同的空值行为。
