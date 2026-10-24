# Master 规划与协作（Master）

## Purpose

Master 规划与协作能力负责能力选择、计划发布、汇合与完成条件；Master 是 HarnessAgent 配置，确定性规则由平台工具执行，不手写 JSON 决策主循环。（M03）

## Requirements

### Requirement: 直接回答与收尾

Master 理解目标，可直接回答、委派、核验与聚合；直接回答也走 propose_completion 候选，只有该 Turn 持久成功后 Finalizer 才发布终态。（R03）

#### Scenario: 直接回答
- **WHEN** Master 直接回答并调用 propose_completion
- **THEN** 保存候选，等该 Turn 持久成功且候选匹配后发布 answer.completed/task.completed 并释放对话

#### Scenario: 无候选纠正
- **WHEN** 模型只输出文本未提交完成候选
- **THEN** 最多一次系统纠正；仍无候选则发布诊断答案并置 FAILED，取消意图优先于纠正

### Requirement: 计划发布与约束

publish_plan 事务内校验无环、版本、节点数、依赖、Schema、权限与上下文，一次性创建 Plan、Jobs、Command 与事件；串行依赖、并行、明确汇合和有效结果复用是确定性规则。（R06）

#### Scenario: 计划校验（A04）
- **WHEN** publish_plan 校验无环、expectedPlanVersion 匹配、节点数≤16、并行≤4、依赖完整、上下文已固定
- **THEN** 创建 Plan(v+1) 与 Job，根节点 READY 其余 BLOCKED；超过总预算拒绝；依赖未满足不启动下游

#### Scenario: 无效输出有限修正
- **WHEN** 模型计划输出无效
- **THEN** 最多两次反馈修正，之后明确失败或追问，不无限循环

### Requirement: 汇合与协调轮次

Job 结果完成时推进满足依赖的节点并递增 signalVersion；汇合条件满足后创建唯一 MASTER_SIGNAL Command，新协调 Turn 与旧 Turn 同属一个 taskId。（R06）

#### Scenario: 汇合提交
- **WHEN** 依赖与等待条件满足且无未处理 Master 轮次
- **THEN** 创建唯一键为 taskId+signalVersion 的 MASTER_SIGNAL Command，输入为固定结果引用，不伪装用户新消息

#### Scenario: 页面单目标
- **WHEN** Master 分多个协调轮次推进同一目标
- **THEN** 页面只显示一个用户目标，不把每个内部 Turn 显示为新的用户消息

### Requirement: 重新规划与结果复用

新 Plan 取代旧版本，未开始旧 Job 置 SUPERSEDED、执行中写取消屏障；成功结果仅在输入摘要、能力版本、Skill 版本、权限及证据时效全部匹配时显式复用。（R06）

#### Scenario: 计划重放（A09）
- **WHEN** Plan 已写而 SDK tool result 未提交
- **THEN** 以稳定 logicalKey 返回原 Plan/Jobs，无重复节点

#### Scenario: 新计划替代旧计划（A10）
- **WHEN** 新计划替代在途旧计划
- **THEN** 旧结果不推进新依赖，旧副作用证据不删除，复用记录 reusedFromJobId
