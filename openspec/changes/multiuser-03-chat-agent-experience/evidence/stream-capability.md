# 固定 SDK 流式能力边界（2026-11-03）

- SDK 使用仓库固定 2.1.0-BETA1；本机 Maven 缓存含对应 sources.jar，已检查 OpenAIChatModel 与 ChatModelBase。
- ChatModelBase.stream 返回 Flux<ChatResponse>；OpenAIChatModel 根据 stream 选项调用 client.stream，响应含结构化 ContentBlock。
- 新增 ModelStreamContractTest 使用本地 OpenAI SSE 桩及 stream(true)，仅验证传输文本块的实际解析能力；统一定向测试通过（1/1，日志 `/tmp/agent-squad-final-targeted.log`）。
- 当前正式 Master/Worker 均 stream(false)，本批不改变该开关。
- 本地 ResponseModelStub 与正式交付链路显示：最终用户正文由 propose_completion 工具参数交付，模型随后 TextBlock 可以是“等待平台发布”。因此 TextBlock 类型本身不是用户可见授权标记；不可将其直接转成 response.delta。
- 真实模型事件、reasoning_content、工具参数分片、重试/取消归属与完整公开文本安全性仍 NOT_RUN。C3.0.1/C3.1.2 不标记完成，不新增持久 draft 表或公开 delta API。
