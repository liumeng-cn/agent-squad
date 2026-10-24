# Tasks

## 1. WorkerDefinition 契约与生命周期

- [ ] 1.1 实现 WorkerDefinition 发布/查询，同一 workerKey/version 不可重复发布、版本内容不可变、停用只改状态且 REVOKED 不可恢复。验证：A17，新能力注册后 Master 无业务分支改动即可发现使用。
- [ ] 1.2 实现管理员能力页与普通用户访问隔离。验证：B14，无权限直达被后端拒绝。

## 2. Plan/Job 持久化与发布校验

- [ ] 2.1 实现 Plan/Job 持久化与 publish_plan 事务：校验无环、expectedPlanVersion、节点数≤16、并行≤4、依赖完整、Schema、权限与上下文，一次创建 Plan/Jobs/Command/事件。验证：A04 依赖未满足不启动下游。
- [ ] 2.2 实现 Plan 版本化与旧 Job 处理：新 Plan 取代旧版，未开始 Job 置 SUPERSEDED，执行中写取消屏障。验证：A10 旧结果不推进新依赖、旧副作用证据不删除。

## 3. WorkerDispatcher 与恢复

- [ ] 3.1 实现 WorkerDispatcher：从 READY Job 绑定 guard，经能力工厂构建叶子 HarnessAgent，Command ID 作 SDK submit key。验证：A05 同 Job 两实例仅一个有效 guard，原生日志拒绝旧 writer。
- [ ] 3.2 实现 Job 失败分支恢复：崩溃后仅恢复未完成分支，不重跑成功分支。验证：A06 结果候选不提前算成功。

## 4. 汇合、重规划与结果复用

- [ ] 4.1 实现汇合信号：Job 结果持久完成时递增 signalVersion，满足条件时创建唯一 MASTER_SIGNAL Command（taskId+signalVersion）。验证：一个目标多个协调轮次仍只显示一个用户目标。
- [ ] 4.2 实现结果复用：成功结果仅在输入摘要/能力版本/Skill 版本/权限/证据时效全匹配时复用，记录 reusedFromJobId。验证：A09 计划重放返回相同 Plan/Jobs，无重复节点。

## 5. 页面交付与证据

- [ ] 5.1 交付任务详情（Job 列表、进度时间线、交互、脱敏操作详情、产物与核对入口）与子任务列表。验证：与真实 API 联调，B05/B08 状态与正文行为正确。
- [ ] 5.2 记录 D3 证据：单/并行/串行/失败分支恢复实验、固定 SDK/配置版本、故障注入点、平台与原生日志、外部操作次数。验证：A04–A06/A09/A10/A17 全部通过且证据目录齐全。

## Workflow follow-up

- 归档本 change 前按项目评审要求复核并同步 D3 证据。
