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

### Worker 用受控能力工厂构建
- 选择：WorkerDispatcher 从 READY Job 取得/创建固定 RuntimeBinding，先 CAS guard，再用 WorkerFactory 构建叶子 HarnessAgent；Command ID 作为 SDK submit key。
- 理由：与 runtime 的 guard/租约一致；恢复原 Session/Turn，不以新 submit 冒充 resume。
- 替代：直接调用默认 agent_spawn/Subagent（无持久归属与恢复协调）。未采用。

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
