# AgentScope PostgreSQL 验收

V2 使用 2.1.0-BETA1 官方 JdbcDistributedStore 自动识别 PostgreSQL，连接见 `server/v2-server/src/main/resources/application-local.yml`，与用户模块共用本地 agent_squad 库。启动时由 SDK 创建并校验其表结构；项目没有自行编写 JDBC 持久化 SQL。SDK 表由固定版本 SDK 管理，不复制到业务 V1 基线。

## 存储范围

- `agentscope_store`：BaseStore KV，包括原生会话 Journal 和共享文件路由下的会话、任务、消息、异步工具、记忆等产物。各类数据复用 namespace_path/item_key，不各建业务表。
- `agentscope_sessions`：AgentStateStore 状态表。原生会话从 Journal 恢复，不因普通聊天必然写入此表。
- `agentscope_snapshots`：沙箱归档。当前为 RemoteFilesystemSpec，不运行 Docker 沙箱，所以普通聊天不会产生快照。
- 沙箱执行锁由官方 PG 方言提供。消息总线、异步工具注册表、任务仓库沿用 SDK 的工作区实现，共享文件由 PG KV 承载；sessionTurnGate 仍使用 SDK 进程内默认实现，不代表已具备多实例执行互斥。

RemoteFilesystemSpec 按用户隔离共享数据，未管理的路径仍在本地工作区。该模式不提供本地 Shell，旧本地聊天数据不自动迁入 PG。Markdown 子角色的本地安装文件作为只读模板保留，共享路由优先读取 PG，缺失时可读取模板。

## 运行与观察

重新启动 V2，使用新聊天依次测试普通对话、记忆保存、子任务、停止与继续，再重启检查历史恢复。真实模型流程由人工运行。已用脚本模型完成实际 PG 回归：提交对话后确认 KV 中存在 Journal commits，关闭 Agent 后换空工作区恢复两条消息，验证用户隔离，并断言原工作区没有本地 Journal 文件。

以下为只读查询，不输出上下文载荷：

```sql
SELECT schemaname, tablename
FROM pg_tables
WHERE tablename LIKE 'agentscope_%'
ORDER BY schemaname, tablename;

SELECT namespace_path, count(*) AS item_count,
       sum(octet_length(value_json)) AS payload_bytes
FROM agentscope_store
GROUP BY namespace_path
ORDER BY namespace_path;

SELECT item_key, version, octet_length(value_json) AS payload_bytes, updated_at
FROM agentscope_store
ORDER BY updated_at DESC
LIMIT 50;

SELECT relname,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_catalog.pg_statio_user_tables
WHERE relname LIKE 'agentscope_%'
ORDER BY pg_total_relation_size(relid) DESC;
```

这里查看的是表和占用空间；未配置独立 PostgreSQL TABLESPACE，使用连接的默认 schema/tablespace。

## Journal 路由修正

2.1.0-BETA1 的 `CompositeFilesystem.sessionStorage` 按 `agents/` 根路径选择后端，但 RemoteFilesystemSpec 默认仅配置更深的 sessions/tasks 路由，不能覆盖根路径。必须显式 `.addSharedPrefix("agents/")`，否则 Journal 仍回退到本地 `.agentscope-runtime`，即使 PG 连接和其他 KV 写入正常。

修正后重新启动 V2 才会生效。原有本地 Journal 不删除、不自动迁移；已有会话在 PG 中可能没有原历史，验收请新建聊天。

回归命令（使用本地配置，只写入隔离的测试用户，不调用真实模型）：

```sh
mvn -f server/pom.xml -pl v2-server -am test -Dtest=AguiSessionTest -Dsurefire.failIfNoSpecifiedTests=false -Dagentsquad.test.postgres=true
```
