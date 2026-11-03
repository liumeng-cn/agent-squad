# C3 本地增量验证（2026-11-03）

## 已实现及验证
- 新增 `npm test` 统一入口：既有 Node 键盘测试与 Vitest store/组件测试均执行。
- 权威消息回放覆盖：相同 messageId 重复、旧 revision 迟到、历史分页混合、切会话清理。
- Markdown 安全覆盖：原始 script/SVG、危险 URI、远程图片不生成执行节点；代码围栏、表格和引用保持转义。
- 代码块显示语言并提供代码正文复制，复制失败明确提示；引用递归限制 32 层，超限转义文本降级。
- `npm --prefix web/workbench test`：Node 2 项 + Vitest 8 项通过，0 skipped。
- `npm --prefix web/workbench run build`：类型检查和 Vite 构建通过。

## 未验证与未完成
- C3.1.3 仍未引入完整 Markdown AST 库，不能标记完整任务完成。
- C3.1.5 仅 store/Markdown组件测试已具备，SSE/草稿/登录切换覆盖仍需扩充。
- 流式 SDK 探针、真实模型、长上下文摘要、早到指令真实模型复测、完整多用户浏览器验收未执行。
- 本批不开放原生模型文本流，不将内部工具/推理视作公开正文。
- C2 验收依赖尚未闭环，C3 不宣称整阶段完成。

## 第二批增量
- SSE generation 变化不再静默丢弃，关闭旧流并通过快照重新订阅；无效/非整数 sequence 进入恢复。
- `message.completed` 对已知 messageId 按 revision 即时替换正文；未知身份保留快照权威，不猜测 sequence 或附件。状态仍经快照同步，尚未宣称完整精确局部投影。
- ChatComposer 新增切会话草稿、不确定发送幂等键复用、登出/换用户清理三个组件回归。
- 最终本地测试：Node 2 项 + Vitest 12 项通过（14项，0 skipped）；构建通过。
- pending Directive 源码检查：PlanService 当前只将首个真实 directiveId 嵌入异常字符串，尚不满足完整结构化读取与多指令协调验收。

## 规划协调反馈
- PlanService 在原有目标锁内读取全部 ACCEPTED 指令，结构化返回 `PENDING_DIRECTIVE/taskId/directives/nextAction`。
- 每项提供真实 directiveId、当前任务 expectedVersion 与 kind；不将用户补充正文放进异常，避免异常路径日志包含敏感内容。
- 所有读取以 userId 和当前任务归属过滤；多个指令处理后仍须按最新版本重新发布，既有 decide CAS/goalRevision 屏障不变。
- 本批 Java 修改尚待主代理统一编译/并发回归，未勾选 C3.2.1。

## 测试基础验收
- 已增加 fake EventSource 自动化，验证 sequence 缺口与 generation 改变均关闭旧订阅并请求快照恢复。
- `npm test` 最终 Node 2 + Vitest 13 = 15 项通过；`npm run build` 通过。
- C3.1.5 测试入口、store/SSE/组件覆盖已满足，已标记完成并刷新 apply instructions（1/14）。其余涉及更全面运行验收的任务不据此完成。
- 后续审核补齐安全内容通道：PendingDirectivesException 的 message 仅安全原因，正文存在独立 PendingDirectiveFeedback 内，由已授权 PublishPlanTool 单独序列化给目标 Master；普通异常日志不会打印正文。
- 多条指令的 expectedVersion 是读取时当前 Task 版本，工具要求每次处理后重新 publish_plan 获取剩余修改与新版本；既有 CAS 拒绝旧版本，不批量盲用。

## 统一 Java 验证结果
- 主代理统一定向测试 BUILD SUCCESS（`/tmp/agent-squad-final-targeted.log`）：PlanPendingDirectiveTest 1/1、ModelStreamContractTest 1/1、既有 TaskDirectiveIntegrationTest 2/2。
- PlanPendingDirectiveTest 证实两条待处理指令、真实 ID、当前 Task 版本、受控正文与安全异常消息；本地 OpenAI SSE 桩证实 SDK TextBlock 可解析。
- C3.2.1 仍保留未完成：尚缺发布提交前后并发、错误协调回合与多次版本刷新的完整确定性回归；现有源码和四项通过结果不足以替代这些门槛。
- C3.0.1 仍保留未完成：本地协议桩通过不等于真实供应商与安全公开事件完整证明。
