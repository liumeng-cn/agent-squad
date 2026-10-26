# Design

## Context

D0–D2 已完成骨架、单 Master 正向链路与受控工具后端。D3 把跨 Worker 协作从设计落到实现；权威与恢复语义见主 spec runtime/master/worker 与 `openspec/config.yaml` 的运行权威约束。见 proposal.md - Why。

## Goals / Non-Goals

**Goals:**
- 一个 GoalTask 内支持串行依赖、并行与显式汇合，节点数/并行度受确定性上限约束。
- Worker 崩溃后仅恢复失败分支，不重跑成功分支；新计划替代在途旧计划时旧结果不推进新依赖。
- Master 不新增业务分支即可接入新 WorkerDefinition。

**Non-Goals:**
- 不实现通用 DAG 引擎或任意递归委派；Worker 不能写全局记忆、发布 Plan、结束 GoalTask 或再派 Worker。
- 不开放自由后台 spawn；生产禁用框架 WorkspaceTaskRepository 与平台 Dispatcher 同时推进同一 Job。

## Decisions

### Plan/Job 与 Master Turn 分离
- 选择：Plan 是不可变 DAG（taskId+planVersion），Job 是有界委派节点；publish_plan 在一个平台事务内创建 Plan、Jobs、Command 与事件。
- 理由：产品目标跨多轮 Master Turn，恢复保持 taskId 不变；把协作结构与框架 Turn/Run 分开，避免以框架 Turn 完成误判目标完成。
- 替代：把每个 Job 当一个独立 SDK 子任务（可复用默认后台子任务，但无法保证跨节点接管，违反 R08）。未采用。

### 存储模型：Plan 头表 + Job 节点表
- 选择：`sq_plan` 存计划头（taskId+planVersion 唯一、不可变 `definition_json`、`signal_version`、`status`）；`sq_worker_job` 一节点一行（`job_id` 唯一、`task_id+plan_version+node_key` 唯一、`depends_on_json`、`input_json`、`result_candidate_json`/`result_json`、`binding_id`、`reused_from_job_id`）。二者均带 user_id，外键指向 `sq_goal_task(user_id, task_id)`，Job 另以外键指向 `sq_plan(task_id, plan_version)`。
- 理由：DAG 结构与节点状态分离，汇合/重规划按 `taskId+planVersion` 定位，恢复按 `jobId` 定位，与运行协议的标识权威一致。
- 替代：把节点只作为 Plan 定义 JSON 内嵌、不建节点表（无法对单节点做持久状态与恢复）。未采用。

### publish_plan 的实施边界
- 选择：D3 先实现 `prepare_context` 的最小局部快照：固定用户、目标、节点、会话代际、允许的材料引用与内容摘要；D4 再扩展 Skill/显式记忆的选择与删除屏障。`publish_plan` 在一个平台事务内校验快照归属、目标节点与版本，并创建 Plan、全部 Job、根节点 Binding/派发 Command 和 `plan.published` 事件；同 logicalKey 重放返回原回执。依赖节点变为 READY 时，由 WorkerDispatcher 在同一事务创建其 Binding/Command。
- 理由：主 spec 要求发布事务包含 Command 与上下文校验。READY 节点若仅落库而没有持久派发意图，实例退出窗口无法由原命令恢复；没有权威快照，Worker 的局部材料边界也无法验证。
- 替代：先保存任意 `contextSnapshotId` 字符串、待 D4 再校验（会让 D3 Worker 执行未经授权的材料）。未采用。

### Worker 用受控能力工厂构建
- 选择：WorkerDispatcher 从 READY Job 的持久 Command 取得固定 RuntimeBinding，先 CAS guard，再用 WorkerFactory 构建叶子 HarnessAgent；Command ID 作为 SDK submit key。下游 Job 转 READY 时在状态事务里创建 Binding/Command。
- 理由：与 runtime 的 guard/租约一致；恢复原 Session/Turn，不以新 submit 冒充 resume。
- 替代：直接调用默认 agent_spawn/Subagent（无持久归属与恢复协调）。未采用。

### 已发布能力的运行时发现
- Master 通过 `list_worker_capabilities` 从持久 Definition 查询当前 ENABLED 版本；列表只返回键、版本和描述，精确键/版本查询返回输入输出 Schema、工具白名单及两个 profileKey。`publish_plan` 仍在事务内重新核验版本，不能把一次目录查询当长期授权。
- WorkerFactory 在固定 Binding 对应的 Job 上重新读取不可变 Definition，解析 modelProfileKey/runtimeProfileKey 与工具白名单后构建叶子 Agent。Definition 在计划后停用或撤销时不得新启动该版本；已启动的执行通过 Job/Binding guard 和取消屏障裁决。未知 profileKey 或未注册工具必须明确拒绝，不能悄悄回退默认模型或扩大工具权限。
- A17 验收必须覆盖新 Definition 从目录发现、计划选择、固定 Binding 派发到 Worker 结果终态；目录查询成功不能代替执行证据。

### D3 Profile 与模型预算前置
- 模型 Profile 是服务端配置的不可变版本引用：每个 key 解析模型提供方、模型名称、端点和定价版本，密钥仅通过服务端密钥引用取得；运行 Profile 解析节点并发、超时、工具调用上限和恢复次数。发布 Definition 和启动 Worker 都验证引用存在，未知 key 拒绝，不使用隐式 default 回退。现有 `default` 是第一条显式配置，新增 Profile 通过配置注册而无需修改 Master 业务分支。
- 父 GoalTask 固定预算 Profile 和统一微单位上限。Master/Worker 每次真实模型请求前，以稳定请求键在同一 Task 的 Reservation 账本预留；事务锁定父 Task 后汇总已结算与未结预留，超额拒绝。请求完成按可信用量与定价版本结算，响应丢失或收费未知保守保留预留；重试同请求键不能新增预留。现有 D2 工具预留接入同一锁和单位口径，不在不同预算池中分别放行。
- `publish_plan` 在事务内按各节点 Profile 的最坏一次启动成本及既有预留核验总预算，避免持久创建无法启动的 READY 节点；实际模型请求仍逐次预留。D3 验证并行模型请求和工具请求共享上限、未知收费不释放。D5 保留摘要与沙箱重试的接入和全链路压测。

### 汇合与重规划
- 选择：Job 结果持久完成时递增 signalVersion；汇合器在依赖/等待满足且无未处理 Master 轮次时创建唯一 MASTER_SIGNAL Command（key=taskId+signalVersion）。新 Plan 取代旧版：未开始 Job 置 SUPERSEDED，执行中写取消屏障，成功结果仅在输入摘要/能力版本/Skill 版本/权限/证据时效全匹配时复用。
- 理由：保证确定性汇合与结果复用，避免每条日志唤醒 Master 或产生重复节点。
- 替代：每次 Job 完成即唤醒 Master（刷屏、非确定性）。未采用。

## Risks / Trade-offs

- [框架 BETA1 后台子任务接管缺口] → 以平台 Dispatcher + 持久 Command/guard 承担归属，恢复实验覆盖同 Job 两实例与迟到提交（A05）。
- [publish_plan 校验遗漏] → 按 spec 逐项校验并在任务中补契约测试（A04）。
- [页面把内部 Turn 显示成新用户消息] → 页面只渲染一个用户目标，内部协调轮次折叠（B08 语义，见 web-console spec）。

## Open Questions

（无——SDK 具体接口以 D0/D1 已验证的固定标签为准；若固定标签缺少必要查询，按 runtime-contract 暴露缺口并修订，不降级需求。）
