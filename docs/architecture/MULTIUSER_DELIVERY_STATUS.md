# 多用户正式产品 C1–C4：代码交付状态

> 更新时间：2026-11-02。分支：`feature/multiuser-c1-c4-implementation`
>
> **状态：PARTIAL IMPLEMENTATION / NOT TESTED / NOT PRODUCTION READY**
>
> 用户要求暂时跳过测试环节，当前所有功能验证、构建及回归结果均为 NOT_RUN。GitHub 写入成功不是编译通过。

## 2026-11-02 后续代码批次：C1 账户管理补强（NOT_RUN）

本批次在原 C1 部分实现基础上继续补强，不代表 C1 全部验收完成。

- Gateway 账号管理引入统一显示名称/角色/状态参数校验，并对密码输入长度做边界限制；
- 管理员写操作锁定活跃管理员集合，并在事务内保护最后一名管理员，减少双网关并发降权风险；
- 修改密码增加账号行锁、旧密码校验和认证版本 CAS，防止与管理员重置操作发生覆盖；
- 用户创建、更新、密码重置、退出及失败登录增加审计记录；原有成功登录审计保留；
- 用户查询增加真正的 ID 游标分页，返回 `nextCursor`，管理界面可逐页加载；
- 前端用户管理支持“加载更多”、临时密码清屏、限制显示名称长度和操作冲突时刷新。

**验证状态**：Java 编译、TypeScript 构建、SQL 测试、并发竞态、真实网关、跨用户越权、模型/SDK 及 E2E 均为 **NOT_RUN**。由于尚未验证事务、Spring 运行时行为和生产迁移，本次仅说明源码已提交，不得标记 C1 GO。

## 本次实际代码（可逐项核查）

### C1 身份与信任
- 新建 `sq_user`、`sq_auth_session`、`sq_auth_rate_bucket`、`sq_auth_audit`；
- Gateway JDBC / BCrypt 持久用户、会话、失败登录计数、用户启停、密码重置、首次改密与受限管理员 API；
- 通过显式 bootstrap 环境变量初始化空账户库的首个管理员；
- Gateway 的短效 HMAC 内部断言和 Server 严格验签逻辑；prod profile 要求 32 字节密钥；
- Message、Command、Model、Operation、Sandbox 接入活动用户身份检查；
- Vue 首次改密和用户管理入口、相应类型与 OpenAPI 契约；
- Production profile 默认禁止 Server 自动执行 SQL，当前业务仍支持 local 开发兼容身份头。

### C2 并发与隔离（仅完成部分）
- SSE 新增 per-user 连接上限与清理，与现有全局 Semaphore 共存。
- 保留原持久命令、AgentScope、MySQL Budget 与恢复链路；未实现完整的公平调度、持久跨实例 slot、真实压力基线。
- **未完成**：跨进程 Master/Worker 接管、安全回放验证、压测证明及完整用户越权矩阵。

### C3 对话与 Agent 体验（仅完成部分）
- 会话草稿按用户隔离于 sessionStorage；不确定发送复用原幂等键；
- Markdown 表格、引用采用 Vue VNode 安全渲染，不使用动态 HTML；
- 早到指令导致 publish_plan 拒绝时，返回真实 directiveId 并要求先应用或拒绝。
- **未完成**：真实 token delta 流式、安全事件白名单与回放、模型 token 窗口和摘要、真实模型修订竞态验证。

### C4 发布基础（仅完成部分）
- 双模块 prod profile、内部 Actuator/Micrometer、Prometheus Registry、请求相关 ID；
- Staging 镜像和 Compose、手动触发的无测试构建 workflow、独立隔离的部署说明；
- 44 表生产基线快照与新增 4 表增量 SQL（未在任何正式库执行）。
- **未完成**：CI 真正运行并强制通过、正式发布证据、schema-parity 验证、Flyway CLI 接入、灾备实测、TLS 真实配置、容量压测与生产批准。

## 风险与本地执行说明

1. 仅把此分支视为**未测试开发版本**，不能合并或部署生产。
2. 先由用户在隔离数据库检查 44→48 表结构及账号映射。禁止重建或清空既有库。
3. 首次启用 `SQUAD_AUTH_MODE=database` 前，单独执行受控管理员 bootstrap；注意旧 local 模式账号不会自动迁移为数据库账号。
4. 测试 JWT/HMAC 断言（本实现使用定制短效 HMAC 格式而非标准 JWT）、浏览器 Cookie/CSRF、停用用户后模型和工具安全点；服务之间必须共享同一密钥。
5. 启用 `SPRING_PROFILES_ACTIVE=prod` 前确认内网端口隔离，`SQUAD_IDENTITY_HMAC_KEY` 不少于 32 字节且不进 Git；网关须通过 HTTPS 反向代理供浏览器使用。
6. 本地验证命令由用户自主决定：`mvn -f server/pom.xml -B -ntp test`、`npm --prefix web/console ci`、`npm --prefix web/console run build`、`python3 tools/check_design.py`。
7. 在继续建设 C2 调度和 C3 流式事件前，必须确认 AgentScope Harness 真实 SDK 事件语义，绝不通过直接透传原生事件提供“假流式”体验。

## 本次未执行

- Maven compile/test：NOT_RUN；
- TypeScript build：NOT_RUN；
- MySQL / HTTP / SSE E2E：NOT_RUN；
- 真实模型 / SDK / Docker / staging：NOT_RUN；
- OpenAPI 与 OpenSpec 设计校验：NOT_RUN。

目前这不是全部 C1–C4 任务完成报告，仍有关键能力需要实现和回归。用户可以在此分支继续本地验收与完善。
