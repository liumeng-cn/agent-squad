# M08 持久输出、产物与治理

## 用户事件与快照

GET /api/v1/tasks/{taskId}/snapshot → TaskSnapshot；GET /tasks/{taskId}/events 首次用 after=snapshot.cursor，自动重连用 Last-Event-ID（taskId:seq）返回持久 SSE；GET /tasks/{taskId}/jobs、operations、interactions 是授权读视图，不驱动推理。

原生 SessionLog 是 Agent 执行事实，SessionProjector 以 bindingId/nativeEventId 去重，平台事务追加公开 Event、更新 Job/Task 的业务读投影并持久存 sourceSeq。公开 seq 在每个 Task 内递增，与原生 seq 不互换。原生实时 AgentEvent 只能提示刷新，不能作为持久成功或消费水位。

快照只投影截至某个公开 watermark 的同一一致前缀，返回 task/jobs/pending/answer/cursor；之后 SSE 读取 seq > cursor。持久轮询补偿实时通知丢失，不存在“先查历史再订阅”的空窗。游标未来值 400，过期 410；跨任务/用户拒绝。断开仅停止订阅，不调用模型也不取消任务。

公开类型：task.accepted/state_changed、plan.published、job.progress/completed、interaction.requested/resolved、operation.unknown/reconciled、answer.started/delta/replaced/completed、task.completed。正文只来自 Master 已授权回答轮次；Worker 日志和工具输出脱敏后变为进度/证据，不串进主正文。思考块、原始 Prompt、密钥和内部路径不对用户输出。

真实增量先经过原生日志持久，再投影公开事件；有延迟就记录延迟，不用 sleep 模拟流。恢复无法续接供应商生成位置时增加 answerRevision，发 replaced 并替换旧未完成回答。完成答案与业务终态同平台事务，不能让最终文本比核对状态先宣告成功。

## 产物

POST /api/v1/artifacts 为 multipart 上传，返回 READY Artifact；GET /artifacts/{artifactId} 与 /content 都重验 owner/引用范围。上传最大 10MiB 初值，允许类型、实际字节数与摘要均验证；临时对象半上传不对外可见。对象 key 是服务端生成的，不接受宿主路径或任意 URL。

Worker 通过 ArtifactService.stage/commit 受控端口交付文件；Operation/Job guard、写空间和文件 hash 全部匹配才 READY。框架交付工具适配到同一端口，不自建 Python 上传协议。DB 元数据和对象存储不是原子事务：先 stage 对象再提交 READY，失败对象清理；绝不先给 READY 再补上传。

上下文、Skill、检查点或 Job 结果引用的文件要保留；归属删除先禁读，再清运行引用后物理清理。快照、工作目录、最终产物有不同保留期，不能笼统删一个目录。

## 预算与档位

STANDARD 设计初值：maxJobs=16、maxParallelJobs=4、maxReplans=3、maxModelCalls=40、maxToolCalls=80、maxRecoveryAttempts=3；输出/输入累计 token、deadline 和整数成本上限通过 Profile 配置冻结。具体成本档位在运行前配置，不允许无预算启动。

父 Task 全局预算由 Reservation 事务预留，所有 Master/Worker 共享。模型 middleware 在每次真实请求前预留 token/费用，结束按可信供应商用量结算；SDK 重试、摘要、辅助模型及恢复都计费，不能只统计主请求。未知收费保留保守占用，不能超时就释放；供应商切换不重置预算。

模型辅助上限和平台预算都生效，框架一个子 Agent 的 maxIters 不能代替全局上限。工具调用、依赖等待、人工等待、平台总期限分开计时；deadline 不强行将 UNKNOWN 改失败。

## 生产与运维

目标档位：任务与审批保留 30 天、公开事件 7 天、已交付产物 30 天，运行引用优先；审计 180 天，需按部署策略复核。生产目标初稿：持久受理 RPO≤5 分钟（按备份/复制策略），服务恢复 RTO≤30 分钟，单实例退出可接管目标≤120 秒（不含人工核对/供应商耗时），同区公共 API 月可用性 99.9%。这些是验收目标，不是已经满足的 SLA。

监控至少包含桥接积压、journal 导出延迟、guard 丢失、UNKNOWN 年龄、权限拒绝、预算、对象孤儿与恢复阻塞。备份同时覆盖平台表、原生 journal/inbox/水位、Skill 固定版本及对象引用；恢复演练验证同一时点或按缺失窗口保守核对，不能凭一份业务数据库备份宣称无损恢复。

验收：页面重连无重复业务；主/子消息不串；公开投影能从原生日志幂等补建；并发预算预留不超；备份缺窗口的操作进入核对；日志无密钥。
