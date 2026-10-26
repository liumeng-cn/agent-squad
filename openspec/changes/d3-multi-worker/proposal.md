# Proposal

## Why

D0–D2 已交付骨架、单 Master 正向链路与受控工具（Connection/Operation/Interaction）后端；进入 D3 实现多 Worker 协作，让一个目标通过 Plan/Job 组织串行、并行与显式汇合，并支持失败分支恢复与重规划。相关可观察行为已在主 spec `master`(M03)、`worker`(M04)、`runtime`(M02) 规定，本 change 只把它们落成实现与验证。

## What Changes

- 实现 WorkerDefinition 发布、版本不可变、停用与撤销（R12）。
- 实现 Plan/Job 持久化与 publish_plan 一次性校验（无环、版本、节点数、依赖、Schema、权限、上下文）。
- 实现 WorkerDispatcher：能力工厂构建叶子 HarnessAgent、guard 领取、固定输入与 SDK submit key、崩溃恢复原 Session/Turn。
- 提前建立配置型模型/运行 Profile 注册表及父 Task 共享的模型预算预留，使 WorkerDefinition 中的 profileKey 可解析且并行执行不绕过预算；D5 继续扩展摘要、工具与沙箱的完整治理实验。
- 实现汇合信号（MASTER_SIGNAL，taskId+signalVersion 唯一）与重规划/结果复用（SUPERSEDED、reusedFromJobId）。
- 工作台交付任务详情、子任务列表、管理员能力页（R10）。

## Capabilities

### New Capabilities

（无——本 change 不新增/修改任何 spec 级行为，D3 行为已由主 spec master/worker/runtime 覆盖，已在 `.openspec.yaml` 声明 `skip_specs: true`。）

### Modified Capabilities

（无）

## Impact

- `server`：M03/M04/M02 的 Service/Repository/DTO 与 WorkerDispatcher、Plan/Job 状态机。
- `web/console`：任务详情、子任务列表、管理员能力页。
- 数据：`sq_worker_definition` 在 `V1__baseline.sql`，Plan/Job 在 `V2__plan_worker_job.sql`；D3 还需为局部 ContextSnapshot 建立权威存储，并以新增迁移承载，不改写已编号迁移。
- 验收：A04–A06、A09、A10、A17。
