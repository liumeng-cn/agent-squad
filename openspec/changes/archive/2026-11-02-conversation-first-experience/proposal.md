# Proposal

## Why

验收发现当前实现把每条消息受理成 GoalTask、执行时锁住输入，普通用户还需跳转详情寻找失败与产物。问题涉及任务运行边界与对话事实，并非附件样式调整；应在 D4 能力接入前修正，避免 Skill、记忆、沙箱继续绑定错误入口。

## What Changes

- **BREAKING**：消息入口改为 Conversation → Message → ResponseExecution；普通交流不创建 GoalTask。Master 通过受控工具按需建立任务，再沿用 D3 Plan/WorkerJob。
- 普通回复仍具备持久 Command、SDK Binding、预算、授权、取消与故障恢复；不得将 GoalTask 换名后仍每句建任务。
- 在途任务不锁住聊天输入；消息按会话顺序持久化，由 Master 解释补充、询问或目标变化，平台不按关键词分流。
- 对话统一呈现真实运行状态、答案、追问、审批、文件和分级故障；取消普通用户任务详情入口。对外未知写入继续核对，禁止自动重放。
- 自动生成且允许修改标题；入口立即显示可输入的空白对话，首发才持久创建。历史空标题安全补全。
- 参考用户提供的 DeepSeek 布局重做侧栏、留白、居中空态与输入框；附件以紧凑卡片置于输入框内，隐藏原生文件控件，不展示未实现的思考/搜索开关。
- 同步 D4–D6 依赖、验收与页面计划；保留既有 D3 完成证据，新增变更独立验收。

## Capabilities

### New Capabilities

无。扩展现有能力，不另造并行规范。

### Modified Capabilities

- `runtime`：消息与回复执行持久受理、任务按需创建、在途消息顺序、取消与恢复边界（R02/R08/R11，A01–A03/A11/A12）。
- `master`：会话理解、受控创建任务、补充与目标变化、统一收尾（R03/R06，A04/A09/A10）。
- `events-artifacts`：会话级一致消息流、回复范围预算与产物归属（R10，A13/A16/A18）。
- `web-console`：对话内完整交互、真实状态、错误行动、标题与附件布局（R10，B02–B10/B15）。
- `skills`：普通回复与任务都可按需发现、固定版本和授权（R05，A14）。
- `context-memory`：会话上下文与普通回复同样遵守显式记忆删除屏障（R04/R09，A15）。

## Impact

- 后端：conversation/runtime/presentation/governance/interaction/operation/artifact 与 AgentScope 适配层；增加迁移，保留实际数据库与 Git remote。
- 契约：实施时同步 api/openapi.json、api/ui-manifest.json 与新增迁移；本提案不提前把未实现接口标为可用。
- 前端：聊天 store、会话侧栏、消息流、输入框；复用既有受控表单、审批、下载、核对能力。
- D4 暂排在本变更主链路验收后；D5 沙箱隔离和 D6 多副本目标不变，但其引用对象、预算范围与验收入口需更新。
- 不增加业务连接器、自动记忆提取、搜索/深度思考功能；不承诺任意格式附件或外部写入恰好一次。
