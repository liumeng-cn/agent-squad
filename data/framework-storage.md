# AgentScope 固定版本存储映射

2026-10-20。依据 v2.1.0-BETA1 标签提交 `5451c73abe47053340683a1cc6d67b290b056648` 只读提取；未编译或在 MySQL 执行。[结构参考](framework-schema.sql)不是生产迁移，实施前须与实际制品的 schema validation 对照。

## 1. 显式选择 EVENT_LOG

该标签 HarnessAgent.Builder 默认 SessionHistoryMode.EVENT_LOG，使用 SessionLogStore；本项目保持该模式，不开启 legacy history，也不因已有 stateStore 就认为 journal 已持久。

设计装配链：受控 JDBC BaseStore → RemoteFilesystem（稳定 namespace）→ WorkspaceSessionLogStore → JournalSessionLog；通过 builder.sessionLogStore 显式提供，避免沙箱文件系统变化导致日志落到本地。框架创建/使用每个 AgentSession，平台 Binding 只发现和引用，不更改内部 head。

StoreSessionStorage 只接受声明支持原子 session storage 的 BaseStore，命名空间追加 `__agentscope_session_log_v1__`，将字节以 Base64 封装后经 putIfVersion 写入。普通 KV set、共享文件复制或最终异步写 MySQL 不满足这个协议。

## 2. 物理表

框架前缀建议 as_，平台业务表 sq_；命名和库在部署时固定，不能随实例名改变。

| 表 | 固定标签主要列 | 本项目含义 |
| --- | --- | --- |
| as_store | namespace_path(512)、item_key(255)、value_json LONGTEXT、version、updated_at BIGINT | Workspace KV 与私有原生日志对象的持久载体；journal 的业务事实在这里，不另建 sq_checkpoint |
| as_sessions | session_id、state_key、item_index、state_data、version、created_at、updated_at | JDBC AgentStateStore 兼容/沙箱状态组件需要的结构；不能当成 EVENT_LOG 的第二权威 |
| as_snapshots | snapshot_id、data LONGBLOB、created_at | JDBC 沙箱快照；后续可替换对象存储，不能把这里当最终交付产物 |

基础表组默认包含这三张。我们不用上游全局同名 Skill 表作用户版本权威，Skill 表组关闭，通过 sq_skill_version/grant 的只读视图适配；不同时维护两份可发布 Skill 目录。

MySQL SandboxExecutionGuard 用 GET_LOCK/RELEASE_LOCK，锁绑定 JDBC 连接，不是自动过期的业务 fence；长时间沙箱锁会占用连接，需专用小池与超时，不能挤满业务事务池。MySQL 这一路不依赖 portable distributed_locks 表。

## 3. 租约、命名空间与模型隔离

SDK 原子写 version、journal writer epoch、平台 Binding.guardFence、Operation.executorFence 四者不同，不可互换。平台只能调用 SDK 接口，不用 MyBatis UPDATE 原生 payload 修状态。导出成功后由框架推进自己的 export cursor。

namespace 只由应用/环境/用户作用域组成；SessionKey 再含 userId/agentId/sessionId。具体编码见[SDK适配表](../docs/sdk-adapter-map.md)：user/session用UUID36，agentId用32字符摘要，不用业务显示名。平台采用短固定编码，并检查 JOIN 后 namespace≤512、journal item key≤255 与主键总字节上限；超限返回配置错误，不能静默截断导致串会话。

模型文件工具不可访问私有 journal 分区或数据库凭据；Skill 发布视图只读，运行时目录与产品下载接口分开。只读历史接口不能调用 session.start/submit。

## 4. 迁移与备份

关闭上游 autoCreateTable，以 Flyway 受控建表；运行账号无 CREATE/ALTER/DROP。保留上游关键字段、类型、索引/排序规则，不能为了统一审计列擅改其读写协议。框架表是三方协议表，不套业务实体映射；结构参考补中文注释，但不计入 21 张自有业务表。

数据库和对象快照备份要能关联 GoalTask/Binding/SDK namespace/原生 commit blobs 与 inbox。未知原生事件类型、缺 blob、CAS 不支持或解码失败必须阻止恢复，禁止回退空白会话。

固定标签源码依据：

- [MysqlDialect](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-extensions/agentscope-extensions-jdbc/src/main/java/io/agentscope/extensions/jdbc/dialect/vendor/MysqlDialect.java)
- [StoreSessionStorage](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/session/StoreSessionStorage.java)
- [WorkspaceSessionLogStore](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/session/WorkspaceSessionLogStore.java)

源码提取证明接口与结构存在，不证明它们在我们的组合下满足耐久性、性能或 HA。
