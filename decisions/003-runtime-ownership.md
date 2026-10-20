# ADR-003：平台目标与框架执行分层

2026-10-20，设计提案。保持需求，使用固定 BETA1 会话机制作为设计依赖，兼容及故障实验未完成。

## 决定

平台 GoalTask 是用户目标，WorkerJob 是有界委派节点；AgentScope Session/Turn/Run 是执行事实。Master 可多轮 SDK Turn，Worker 可在原 Turn 恢复；同一产品任务不因此变成新的用户目标。

采用受控委派工具和持久 WorkerDispatcher，通过能力工厂构建 HarnessAgent。生产禁用自由后台 spawn，不使用默认 WorkspaceTaskRepository 同时执行同一 Job。Agent 的推理、工具循环、Skill 和检查点继续由 SDK 管理。

## 理由

原生后台本地子任务不能保证跨节点接管；直接采用会违反不变的 R08。完全自建 ReAct 又重复框架。这个边界只补跨 Worker 持久协作与授权，不介入模型步骤。

平台事务和 SDK journal 提交不原子，使用持久 Command、稳定 SDK submit key、原生日志导出幂等、业务工具回执和未知结果核对跨越窗口；不采用双写“最终一致就当成功”。

## 代价与替换

需维护 Job/Plan/Command 和协议适配，但无需 sq_step/sq_checkpoint 或 Java/Python 内部 API。框架未来提供可验证的持久子任务接管后，可替换 Dispatcher 适配器，不改变公共 taskId/审批/Operation 语义。

详细规则见[运行协议](../docs/runtime-contract.md)。SDK 制品、Hook 配置与恢复可行性验证失败时修订此 ADR，不把候选直接投入生产。
