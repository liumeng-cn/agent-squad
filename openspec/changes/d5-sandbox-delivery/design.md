# Design

## Context

D5 依赖框架 Filesystem/Sandbox 驱动；平台补驱动选择、资源/网络策略、产物归属与全局预算。隔离未达标前不开放任意脚本。见 proposal.md - Why。

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
- 理由：产物与用户/任务归属、交付完整性都由平台把关。
- 替代：把框架 Workspace 文件直接透传（无归属鉴权）。未采用。

### 全局预算
- 选择：父 Task 全局预算由 Reservation 事务预留，所有 Master/Worker/摘要/工具/沙箱重试共享；模型 middleware 每次真实请求前预留、按可信用量结算。
- 理由：避免并行/重试/供应商切换绕过用户总预算。
- 替代：按实例或按 Agent 独立配额（扩容即可绕过）。未采用。

## Risks / Trade-offs

- [驱动配额/网络/挂载未真正生效] → D5 实验必须验证驱动实际执行限制，未达标不开放脚本（A16/A18）。
- [回执丢失] → 写操作 IN_FLIGHT 失联统一 UNKNOWN，不自动重发，按 KEY/QUERY/NONE 核对（A08 语义）。
- [备份缺窗口] → 无持久保障不发写操作，缺失文件阻塞（A18）。

## Open Questions

（无）
