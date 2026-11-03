# Spec Delta: Web Console / R10

## ADDED Requirements

### Requirement: 多用户对话实时反馈与安全草稿

Web Console MUST 在当前登录用户与会话内隔离待发送草稿和可见回复增量；完成消息以服务端最终记录为权威，断线恢复不重复提交模型请求。不得公开系统 Prompt、模型隐藏推理、Worker 内部日志。

#### Scenario: 断线与文本增量回放
- **WHEN** 部分回复已展示、SSE 断线或丢失一个连续 chunk
- **THEN** 依据事件水位恢复并补齐或重新读取权威 draft/snapshot，最终全文只展示一次，不重复/错序/串会话

#### Scenario: 切换会话与登出
- **WHEN** A 会话有未发送文本草稿，切换至 B 后再返回 A；或者用户登出切换账户
- **THEN** 同用户保留 A 的草稿，登出清除全部草稿及临时附件引用，另一个账号不会看到旧用户的草稿

### Requirement: 安全可用的 Markdown

Web Console MUST 支持基本表格、围栏代码、链接、引用、列表与标题，同时禁止脚本、危险 URI scheme、原始 HTML 和未经许可的远程图片跟踪。

#### Scenario: 恶意 Markdown
- **WHEN** 用户或模型输出脚本标签、javascript URL、SVG 事件属性或超长嵌套内容
- **THEN** 只渲染受限安全内容，不执行任意脚本，不触发危险外部资源加载
