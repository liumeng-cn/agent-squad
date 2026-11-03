# 交给本地编码执行体的指令（全文复制）

你现在在 `liumeng-cn/agent-squad` 的本地代码仓库中。用户正式确认产品目标：**以 ChatGPT 式 Web 对话为核心入口，支持 Master/Worker 多智能体协作的多用户正式在线 AI 产品**，不是 AI Coding 客户端。

你必须以当前真实工作区源码为事实依据，执行交付包 `AGENT_SQUAD_MULTIUSER_EXECUTION_PLAN.md` 中的四个 OpenSpec 变更：

1. `openspec/changes/multiuser-01-identity-trust`
2. `openspec/changes/multiuser-02-admission-isolation`
3. `openspec/changes/multiuser-03-chat-agent-experience`
4. `openspec/changes/multiuser-04-release-readiness`

**执行流程与优先级：**

- 首先阅读 `AGENTS.md`、`openspec/config.yaml`、`openspec/README.md`、`openspec/specs`、`api/openapi.json` 与以上各 change 的 proposal/design/tasks/spec delta。检查 `git status`、当前 SHA、未提交文件与配置。基线设计来自 `main@23c057bf5d138f12d772edf4b673a16b4533f0c5`；如当前仓库不同，以最新代码为准逐项核对，不得照抄不存在的路径或覆盖未提交修改。
- 使用仓库的 OpenSpec 工作流：proposal→design→spec delta→tasks→apply→verify→archive。本文已提供设计初稿；发现真实代码差异时先更新 design 和 decision-log，再实现。
- 按 C1→C2→C3→C4 的依赖顺序实施。每个 C 独立 feature 分支、独立提交、可复现实验与测试门槛；**上一阶段未通过关键验收，不得推进下一阶段并宣称总体完成**。
- 按 tasks.md 的每个子任务完成“代码 + 契约 + 事务/状态 + 测试 + 证据”。不得仅创建空类、接口、SQL 表或 UI 占位并打勾；不得只生成报告不改代码。
- 对 SDK 行为、并发、性能、跨实例恢复等无法仅靠静态审查确认的结论，必须写测试。尤其 `CommandDeliveryService` 是否真实阻塞、AgentScope 单例 Master 线程安全、不同进程 Journal 接管、模型输出是否存在可安全公开的流式事件。**先测试，后架构改造**。
- C1 需实现可用的双账号注册/创建（邀请制）、登录、会话、改密、停用、网关身份断言，且业务端不能再信任未签名 `X-User-*`；保留旧 API 的合理兼容性。
- C2 保留已经成熟的命令幂等、租约、AgentScope 原生运行事实、操作账本 UNKNOWN 和预算预留。不得为了高并发引入不可验证的“恰好一次”承诺。
- C3 仅公开真正 user-facing 的文本，不能将模型思考、工具调用原文、Worker 原始输出当聊天正文。真实流式能力由 SDK 探针证明；如果不支持，保留最终回复，标记阻碍，不能伪造流式。
- C4 只完成 CI/staging/部署脚本/恢复演练设计与可控测试；**未经用户单独批准严禁执行生产部署、生产迁移、生产数据/账号修改、外部服务写操作。**
- 严格遵守 `AGENTS.md` 的 Java 约定：`@Resource` 注入、Spring 类无内部类、功能注释中文、MyBatis XML、业务 UUID + BIGINT 主键、数据库审计字段与日志脱敏。
- 不得删除/重建用户本机真实数据库，不得修改 Git remote、强推 main、覆盖未提交修改、把密钥/令牌/完整 Prompt 写日志/提交仓库。测试仅使用独立 `as_test_*` 隔离库；缺少资源则如实 BLOCKED。
- 数据库设计的关键变更：dev/test 可使用完整单文件 baseline，prod 必须独立受控版本化迁移且启动 `sql.init.mode=never`。先审查仓库规范冲突，修改 config/spec/docs 并跑 schema parity；生产 DDL 只能输出脚本不能直接执行。
- 前端需要真实 API/SSE 契约，更新 OpenAPI、前端类型、测试；保留当前消息发送幂等与 SSE epoch/水位防污染。

## 每阶段必须输出的报告格式

```markdown
# <C1/C2/C3/C4> 实施结果
- 基线 commit：
- 当前 commit：
- 实际完成任务 ID：
- 未完成/阻碍任务 ID：
- 关键设计决策（路径与理由）：
- 真实执行命令及运行环境：
- 测试结果（run/pass/fail/error/skipped）：
- 代码证据（文件路径+关键方法/SQL）：
- 数据迁移/授权变化风险：
- 兼容性与回滚方式：
- Go/No-Go：
```

当前阶段所有门槛通过才更新 `tasks.md` 勾选、产出 `evidence/` 并提交。出现阻碍只提交已验证的安全变更，记录阻碍，停止后续依赖变更。**这项任务要求实际实施和验证，不允许只写技术评审报告。**