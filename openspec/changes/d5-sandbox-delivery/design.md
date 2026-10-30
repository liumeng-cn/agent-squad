# Design

## Context

D5 依赖 `conversation-first-experience` 的 ExecutionScope/BudgetAccount、会话交付与故障动作契约，后接 D4 的固定上下文；同时依赖框架 Filesystem/Sandbox 驱动；平台补驱动选择、资源/网络策略、产物归属与全局预算。隔离未达标前不开放任意脚本。见 proposal.md - Why。

## Goals / Non-Goals

**Goals:**
- 不可信脚本在受限沙箱执行，资源/网络/挂载受控，缺失文件与外部未知先核对。
- 产物上传/交付/下载全程归属鉴权；全局预算跨 Master/Worker/摘要/重试共享。

**Non-Goals:**
- 不预设再造 Python Runner 服务；不把 Docker socket 或本机 shell 当作默认安全配置。
- 不承诺外部事务恰好一次。

## Decisions

### 沙箱驱动与默认关闭
- 选择：优先复用框架 Sandbox 驱动，默认关闭；资源/网络/挂载策略显式声明，未经验证的策略不开放脚本。
- 理由：不可信脚本隔离是硬门槛；驱动开关不能自动获得宿主 Docker 权限。
- 替代：直接使用默认本地 shell/Docker socket（不安全）。未采用。

### 产物交付与归属
- 选择：Artifact 上传 READY 前校验允许类型/大小/摘要；Worker 经 stage/commit 受控端口交付；下载后端重验授权，删除先禁读再物理清理。
- 理由：产物与用户/消息/执行 Scope 的归属、可选任务关联和交付完整性都由平台把关；下载与缺失文件说明放在对话内，不要求任务详情。
- 替代：把框架 Workspace 文件直接透传（无归属鉴权）。未采用。

### 全局预算
- 选择：D3 已交付父 Task Reservation；优先变更将其归属统一到 ExecutionScope/BudgetAccount，普通回复创建任务共享原账户。D5 将摘要、沙箱重试及供应商切换接入该账本，保留用户全局上限和 UNKNOWN 占用，并复核完整定价与并发实验。
- 理由：避免并行/重试/供应商切换绕过用户总预算。
- 替代：按实例或按 Agent 独立配额（扩容即可绕过）。未采用。

## Risks / Trade-offs

- [驱动配额/网络/挂载未真正生效] → D5 实验必须验证驱动实际执行限制，未达标不开放脚本（A16/A18）。
- [回执丢失] → 写操作 IN_FLIGHT 失联统一 UNKNOWN，不自动重发，按 KEY/QUERY/NONE 核对（A08 语义）。
- [备份缺窗口] → 无持久保障不发写操作，缺失文件阻塞（A18）。

## Open Questions

（无）

## 并行实施与共享契约（用户已确认）

- Skill 任务独占 skill 模块、V15 迁移及技能页面；Memory 任务独占 memory 模块、V16 迁移及记忆页面；Sandbox 任务独占 sandbox/artifact 模块与 V17 迁移。主任务统一修改运行、预算、Agent 注册、OpenAPI、路由和计划勾选。
- 请求/响应、工具输入/回执、执行引用采用明确顶层类型；动态 JSON Schema 仅用于真正开放结构。业务规则归属领域模型或明确服务，Controller 与 AgentTool 不编排持久化。
- ExecutionAuthorizationService 从服务端 Session/Binding 推导 Scope，固定 Skill 版本目录/摘要与记忆修订/引用。模型请求、工具调用、命令恢复和产物提交检查有效性；旧上下文无法证明安全时阻塞，不刷新失效引用继续原生 Session。
- 记忆纠正/删除持久化 tombstone 与代际；Skill 安全撤销持久化授权代际。沙箱已发出请求不可撤回，迟到结果与产物不得绕过屏障发布。
- SDK/沙箱调用在平台事务外执行；预算先事务预留，未知执行/计费保守占用，同意图不换键重发。stage/commit 校验执行归属、guard、摘要，提交失败不标为 READY。
- 分别验证后执行撤销/删除发生在模型或沙箱运行期间的交叉实验；对话改造 IME 与端到端收尾仍单独追踪。

## 当前交付优先级（2026-10-30）

用户确认优先完成使用体验与功能链路。保留用户归属、版本、基本输入校验及已有沙箱隔离；复杂攻击覆盖、安全纵深加固另行安排，不作为本轮功能交付的新增前置。已有防护与未验证项分别记录，不将延期项标为验证通过。
