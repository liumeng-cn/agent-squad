# ADR-002：Java 主语言与 AgentScope 路线

2026-10-20。已确认方向：需求不变，以 Java 为主，调研采用 AgentScope Java；具体发布版本与容错集成待验证。

选择嵌入 Harness SDK，优先复用现有 Agent 能力。暂停原 Python/LangGraph、固定四角色部署及手写运行引擎设计；不整套引入 AgentScope Service 后再重复建设其平台。

依据与缺口见[调研报告](../docs/research/agentscope-java.md)。v2.0.4 是正式比较基线；v2.1.0-BETA1 包含官网新会话机制，是固定评估候选。不能将 BETA1/main API 写成稳定版已有能力，也不能因共享状态就宣称后台任务可接管。

R01–R12 保持不变。原模块/57 接口/27 表已由 v0.2 现行设计替换，运行边界以 [ADR-003](003-runtime-ownership.md) 为准。框架执行状态与平台读模型只有一个权威推进机制；仍须补资源授权、副作用幂等与核对、显式记忆、文件及生产治理。

模块、标识、恢复及契约已细化为评审稿；SDK与数据库验证未完成，未授权开始应用实现或部署。
