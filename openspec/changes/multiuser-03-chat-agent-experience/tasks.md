# Tasks：C3 ChatGPT 式对话与 Agent 用户体验

## 0. 确认模型事件契约
- [ ] C3.0.1 固定 SDK 版本，在测试代码内分析 `stream(true)` 的实际事件类型和可公开文本来源；分别跑本地 OpenAI 协议桩与真实模型（受用户已授权的测试用量约束），生成 `evidence/stream-capability.md`。未确认可安全公开内容之前不开放流式功能。
- [ ] C3.0.2 契约先行：完善 `events-artifacts/web-console` spec delta 和 `api/openapi.json` 的 `response.delta`/`message.completed`/回放字段；明确最终确定的 chunkIndex 或 offset 语义，不能同时保留冲突权威。

## 1. 事件与前端体验
- [ ] C3.1.1 先将 SSE `payload` 用于精确事件处理并按 seq/epoch 校验；必要时只刷新受影响区域；序列缺口退回 snapshot；测试同一 conversation 不重复消息。
- [ ] C3.1.2 若 C3.0.1 验证可安全流式：实现模型增量→可见响应 draft→有界批次公共事件→SSE→前端局部拼接→最终全文替换，严格过滤 ToolCall/Chain-of-thought/内部 Worker；补全过程回放/取消/失败测试。若 SDK 无此能力，记录阻碍并停止该子任务，继续其他 C3 安全改进，禁止假的打字动画。
- [ ] C3.1.3 引入安全 Markdown AST 渲染，实现代码围栏、表格、引用、链接/代码复制并覆盖恶意内容测试。
- [ ] C3.1.4 重新组织 ChatComposer 会话草稿保存与切换、附件展示；登出清空；保留不确定发送的幂等键；测试切换/刷新/重复发送/多用户登录。
- [x] C3.1.5 建立前端 test script + store/SSE/组件测试；可读的构建/类型/测试输出成为 C4 CI 入口。

## 2. Agent 任务语义与长上下文
- [ ] C3.2.1 实现 pending Directive 的确定性读取/结构化提示（含真实 directiveId），修复规划回合被 pending 阻断后的安全协调；对并发/错误回合加确定性测试。
- [ ] C3.2.2 真实模型复测首轮规划期间早到补充，并记录任务计划版本、directiveId、终态、是否错误重放；不得以提示词微调代替结构化安全点。
- [ ] C3.2.3 为模型上下文设置按模型 token profile 的选材预算和近期窗口；新增摘要版本与 userId/generation/memoryRevision 无效化策略。测试冻结重放不变、删除/纠正记忆不复活、超限时不丢最新消息。
- [ ] C3.2.4 添加可选长会话压力测试（1000+ 消息和大段 Markdown），比较原版与改版渲染内存、延迟；只有必要才引入虚拟滚动。

## 3. 验收
- [ ] C3.3.1 真实多用户浏览器回归：连续对话、Master/Worker、早到补充、审批/取消、历史加载、附件、Markdown、SSE 断线/重连；截图只记录非敏感数据。
- [ ] C3.3.2 所有既有后端测试、前端 build+test、OpenAPI/设计检查实际通过，记录 disabled/skipped 项。
- [ ] C3.3.3 将已验证的 SDK 能力、流式数据安全边界、性能基线、失败记录放入 `evidence/` 后提交；没有完成真实模型流式证明时不得宣称“已完整流式”。
