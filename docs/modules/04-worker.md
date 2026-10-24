# M04 Worker 定义与可恢复执行

WorkerDefinition 描述能力版本，不等于实例或 Skill。发布包含 workerKey/version、description、inputSchema/outputSchema、toolAllowlist、modelProfileKey、runtimeProfileKey；只有管理员可发布不可变版本，停用只改状态，安全撤销阻止在途新调用。

当前 D3 第一切片已实现定义发布、分页查询和状态更新：发布键按管理员主体幂等，同一 workerKey/version 不可重复，状态用 expectedVersion 乐观更新，REVOKED 不可恢复。服务端限制定义大小、Schema 深度及工具名格式。当前本地账号可通过 `SQUAD_LOCAL_ROLE` 配置管理员角色；这一步只管理能力元数据，尚未接入 Plan、WorkerFactory 或实际派发，不能把 ENABLED 解读为 Worker 已能执行。

## 接口与执行端口

GET/POST /api/v1/worker-definitions；PATCH /worker-definitions/{definitionId} 更新状态。GET /tasks/{taskId}/jobs 查询脱敏 Job 列表。没有公共或内部远程 steps/checkpoints API；SDK 自己保存局部执行过程。

WorkerDispatcher 从 READY Job 创建/取得固定 RuntimeBinding，先 CAS guard，再用 WorkerFactory 构建叶子 HarnessAgent。工厂按已发布 Definition 装配模型、过滤工具、固定 Skill 和上下文，不从模型 URL 下载执行代码。

一个 Job 的初始输入固定，Command ID 作为 SDK submit key。后续崩溃恢复原 Session/Turn；不能重新调用新 submit 来替代 resume。SDK Session 内保存的消息、工具 ID、检查点由 SDK 唯一推进。平台只存业务 Job、结构化结果及 Binding 映射。

## Job 状态与结果

BLOCKED → READY → RUNNING → SUCCEEDED/PARTIAL/FAILED；支持 WAITING_INPUT、WAITING_APPROVAL、RECOVERY_BLOCKED、RECONCILING、CANCELLING、CANCELLED、SUPERSEDED。重启不自动将 RUNNING 标失败，先查持久原生状态；产品截止也不能跳过 UNKNOWN。

Worker 用 report_job_result 平台工具提交 JobResult（outcome/findings/evidenceRefs/unresolved/verification），先作为候选记录。该 Turn 成功的持久事件到达后，ResultFinalizer 校验 guard/Plan、产物与输出 Schema，再写 Job 终态、下游 READY 和 Master signal Command。数据库候选已写而原生 tool result 丢失时按原逻辑键返回同一候选，不重新执行业务。

缺字段用平台 ask_user 适配工具（映射到框架已支持的原生 INPUT 挂起，不声称 ask_user 是SDK同名内置API）返回需求，不猜测；平台从原生持久 pending 建立 Job/Interaction 映射，答复用 respond 继续原 Turn。Worker 可以读获准上下文和调用工具，但不能写全局记忆、发布 Plan、结束 GoalTask 或再派 Worker。

## 调度、隔离与故障

数据库事务只选择业务 Job、建立 Binding/Command，不启动模型。框架自己的 inbox 再负责该 Agent 的执行序列；两者层级不同。默认原生后台 TaskRepository 不用于生产 Job，以免执行归属重复。

恢复需要模型/工具版本、Skill 固定内容、上下文引用和工作文件；任一丢失进入 RECOVERY_BLOCKED。空白 Workspace 冷启动不能视为恢复成功。工具结果不确定先读 Operation 再原生 reconcile，普通 Worker 无权自报外部操作已成功。

绑定 guard 失效时停止派发；旧模型输出不能提交业务结果。平台回调需要 binding + 当前 fence，日志导出也核对绑定 epoch 与业务取消屏障，原生日志本身不可人为删改。

验收：杀 Worker 实例后新实例发现相同 Job；只继续未完成部分，成功分支不重跑；旧实例迟到不能覆盖；结果候选已保存但原生 Turn 失败不能被当作成功。
