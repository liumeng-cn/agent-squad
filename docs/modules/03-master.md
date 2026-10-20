# M03 Master 规划与协作

Master 是一个 HarnessAgent 配置，不是手写 JSON 决策主循环。模型自行选择直接回答、发现能力、发布计划、追问或给出完成候选；确定性规则由平台工具执行。

## 工具契约

| 平台工具 / 端口 | 输入 | 输出与事务 |
| --- | --- | --- |
| list_worker_capabilities | query、limit | 经授权的 key/version/description/inputSchema/outputSchema |
| prepare_context | targetNodeKey、goal、sourceRefs | ContextSnapshot；冻结授权局部材料，返回 snapshotId |
| publish_plan | logicalKey、expectedPlanVersion、nodes、joinPolicy、acceptanceCriteria | PlanReceipt；Plan/Jobs/Command/事件同事务 |
| request_input | logicalKey、question、answerSchema、targetJobIds、expiresAt | InteractionView；创建等待项和事件 |
| propose_completion | logicalKey、outcome、finalAnswer、evidenceRefs、planVersion、answerRevision | ProposalReceipt；只存候选，不立即终态 |

以上同 JVM 工具，不新增 Python 服务或 master/claims HTTP。ExecutionScope 从适配器注入。所有逻辑键绑定 taskId/当前协调轮次和请求摘要；工具重放返回原回执。

## 计划数据与约束

PlanPublish.nodes 每项：nodeKey、workerKey、workerVersion、goal、contextSnapshotId、dependsOn、required、maxRecoveryAttempts。Worker Schema 来自冻结 Definition，不允许模型另填一个更宽权限的 Schema。引用统一由平台验证 owner/task/version/expiry。

发布在事务中锁 GoalTask，expectedPlanVersion 必须匹配。最多 16 节点、4 并行、3 次重规划为初始 STANDARD 档位；所有节点 key 唯一、依赖完整无环、上下文已固定，超过总预算拒绝。创建 Plan(v+1) 与 Job，根节点 READY，其余 BLOCKED；事件与命令同步提交。模型输出无效最多两次反馈修正，之后明确失败/追问，不无限循环。

ALL_REQUIRED：所有 required 节点必须成功；可选节点失败也要列入缺失信息。BEST_EFFORT：全部已结束或截止后允许带缺口汇总。required 节点失败不能让依赖节点继续，进入 Master 复核；下游允许部分输入须在定义中明确声明。

Job 的结构化结果包含 findings、evidenceRefs、unresolved、verification；平台先校验 Schema 和实际产物归属，再更新 Job，推进满足依赖的节点并增加 signalVersion。确定性屏障与业务状态不是另一套模型循环。

## 重新规划与结果复用

新 Plan 取代旧版本，未开始旧 Job SUPERSEDED，执行中写取消屏障；旧结果只进入审计，不能推进新依赖。已发生 Operation 不删除。成功结果仅当输入摘要、能力版本、Skill 版本、权限及证据时效全部匹配时显式引用复用，记录 reusedFromJobId，不再执行原节点。

## 协调轮次与完成

Master 发布计划后本轮 Turn 可以正常结束；系统发现待 Job 则不对用户报完成。汇合条件满足后持久 Command 提交新的系统协调输入，内容为固定结果引用。新 Turn 与原 Turn 都映射到同一产品 Task，页面只有一个用户目标。

直接回答也调用 propose_completion；如果模型只输出文本没提交候选，最多一次系统纠正，仍无候选则进入明确可诊断失败。Finalizer 必须等该 Turn 持久成功后发布最终答案。Master 不会因某个 Worker 返回 success 就跳过未完成必需节点或 UNKNOWN 操作。

验收：直接回答、单 Worker、双并行、串行、冲突核验、替代计划都遵循相同工具契约；新增 Worker 不新增 Master 业务 if/else；计划重放与重复完成通知不能多次派发。
