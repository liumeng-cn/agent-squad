# AgentScope 运行记录的数据库存储

## 当前存储边界

Master、Worker、停止控制、已提交日志读取及恢复均显式使用同一个 `RuntimeSessionProvider`。官方 `JdbcDistributedStore.baseStore()` 承接 SDK `RemoteFilesystem` 的原子读写，`WorkspaceSessionLogStore` 只作为 SDK 日志格式适配器，不代表落盘。

- `agentscope_store`：官方日志对象、输入队列、检查点、大载荷、写入者屏障和导出水位。命名空间为 `squad/native-journal-v2/{userId}`，对象路径保留原 Agent/Session 身份。
- `agentscope_sessions`、`agentscope_snapshots`：保留 SDK 官方状态及快照能力需要的既有结构。不自行编写 SQL 修改 SDK 数据。
- `sq_*`：平台消息、执行、预算、操作、计划、节点和公开事件，继续由 Mapper/XML 访问。
- SDK 必需的搜索索引及技能缓存放在进程独立的系统临时目录 `squad-sdk-cache-*`；正常退出时清理，重启不读取旧缓存。容器应将系统临时目录挂到临时卷；无须持久化 workspace 卷。

数据库是运行记录的恢复来源。删除本实例缓存、换工作目录或换服务节点，不影响已提交执行历史。源码中的自定义技能、附件和正式产物各自遵循其已有存储协议，不在此处转移到 SDK 日志表。

## 从旧本地日志导入

必须停止所有仍写旧目录的 Server。Gateway 可保持运行，但这段时间不能接受业务执行。准备数据库备份，保留旧目录作为只读回退来源；工具不删除来源，不清空数据库，不重做模型或工具调用。

使用与当前业务库相同的配置，显式运行一次：

```sh
java -jar /path/to/agent-squad-server.jar \
  --spring.profiles.active=local \
  --spring.config.additional-location=file:/path/to/application-local.yml \
  --spring.main.web-application-type=none \
  --agentsquad.delivery.enabled=false \
  --agentsquad.runtime.import-local-journal=true \
  --agentsquad.runtime.import-workspace=/path/to/old/workspace
```

导入通过 SDK 的 `AtomicSessionStorage` 复制对象，跳过文件锁和未提交临时文件，head 最后写入；随后核对事件顺序、内容及输入队列。每个对象只允许首次创建或完全相同的重放，遇到数据库内容不同立即拒绝覆盖。来源尚有有效 writer 时拒绝导入。部分失败可以在来源不变、服务仍停止的条件下重新运行。

完成后命令进程退出，再正常启动新 Server。不要在业务已经向数据库推进后重新导入旧文件；此时差异拒绝是数据保护，不能用删表或强制覆盖绕过。

## 验收与回退

- 隔离数据库验证：大载荷、检查点、输入队列、导出游标逐项保留；相同数据重跑幂等，数据库已经推进时拒绝覆盖。
- Master/Worker 集成验证：独立 Spring 实例、崩溃后原 Turn 接管、只读查询不执行模型，停止控制不创建 Worker。
- 实际环境验证：旧成功会话可读，新任务完成后重启 Server，仍可恢复结果；旧 workspace 文件数量及修改时间不再变化，新临时目录不出现 `.agentscope-runtime` 日志。
- 回退应保留新数据库运行记录；不能把旧文件目录当作新执行的完整副本。旧二进制若仍固定使用本地日志，不得在同一批新执行上直接回退运行。
