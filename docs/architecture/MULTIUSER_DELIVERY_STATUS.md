> 历史文档：本文只代表原 `feature/multiuser-c1-c4-implementation` 分支；集成后请参考 `MULTIUSER_INTEGRATION_20261103.md`，本页测试数字不是集成分支的验收结果。

# 多用户正式产品 C1–C4：代码交付状态

> 更新时间：2026-11-02。分支：`feature/multiuser-c1-c4-implementation`
>
> **状态：PARTIAL IMPLEMENTATION / LOCAL VERIFICATION PARTIAL PASS / NOT PRODUCTION READY**
>
> 2026-11-02 已执行 Maven 全量测试、前端构建、设计校验、C1 真实 HTTP/SSE 与 C2 真实 MySQL 回归；真实模型/SDK、经网关的 Cookie/CSRF、双网关、staging/CI 和生产迁移仍未验证。

## 2026-11-02 本地验证结果

| 验证项 | 命令或环境 | 结果 |
| --- | --- | --- |
| 后端全量测试 | 隔离测试 MySQL，`mvn test` | 159 项，0 失败，0 错误，23 跳过 |
| 新增 C1 单元测试 | `SignedIdentityVerifierTest`、`UserContextInterceptorTest`、`UserStatusGuardTest` | 17 项通过 |
| 新增 C2 集成测试 | `DeliveryFairScanIntegrationTest` | 5 项通过 |
| 前端构建 | `npm --prefix web/console run build` | 通过 |
| 设计校验 | `python3 tools/check_design.py` | 通过：52 operations、82 schemas、47 张业务表、9 条 UI 路由 |
| C1 真实 HTTP/SSE | 隔离库、端口 18082、strict + enforce-user-status | 13 项全部通过 |
| C2 执行计划 | 1 万/10 万条真实 MySQL 数据 | 约 20.7ms / 169ms；全表扫描与窗口排序 |
| C2 领取并发 | 同一候选顺序 claim | 第二次影响 0 行 |

## 2026-11-02 C2 批次：恢复类原生 Run ID 关联（NOT_RUN）

- 领取冻结原生投影水位，恢复/答复由后续 `run/start` 绑定新 Run ID，再由匹配 `run/end` 释放容量。
- 新增 V4 迁移与 local 基线两个字段；尚未验证 SDK、旧 Run 迟到、多实例竞争或安全性。
- 持久运行容量功能继续默认关闭。

## 2026-11-02 C2 批次：Slot 恢复一致性补强（NOT_RUN）

- 改造回执绑定的锁序及事务内核对，普通提交的 `run/end` 先到、回执后到时按原 Turn 补偿释放容量。
- Master、Worker 和普通回复统一写入 `last_ended_turn_id`，与原生事件投影水位同事务完成。
- 对 RESUME/RESPOND 的旧 Turn 复用不做不可靠自动回收；无法确认新 Run 结束时保守保留 Slot。
- 补齐原生调用前确定性拒绝的占位清理，以及单条命令异常不影响批量扫描。
- 增加全局和每用户占位计数只读巡检，异常只告警不自动调整；更新设计与回归清单。

**状态：源码完成 / 编译、MySQL、SDK、事务并发、恢复与压测全部 NOT_RUN。** 

## 2026-11-02 C2 批次：持久原生运行容量 Slot（NOT_RUN）

- 新增 `sq_runtime_admission_counter` 与 `sq_runtime_admission_slot`，使用固定 Binding、原命令 ID 与原生 Turn 持久定位运行占位。
- Command 领取与全局/用户配额递增在同一数据库事务中完成；额度不足时回滚领取，保留已受理命令。
- 同一个原命令在原生提交不确定时重试复用已有占位，不重复计数；STOP 不占用新额度。
- 收到原生投递收据时关联 Turn，只有原生 `run/end` 被持久投影且 Turn 匹配时才与水位同事务释放，防止重复回收。
- 对收据缺失、异常、进程崩溃等未确证结束状态，采取保守占位，不以命令短期租约到期替代运行结束事实。
- 增加独立 V3 迁移文件、local 基线和配置；默认关闭，待隔离数据库与真实 SDK 验收后才能显式启用。
- 使用边界、恢复运维及待执行用例见 `docs/architecture/C2_RUNTIME_ADMISSION.md`。

**验证状态：NOT_RUN**：该批次专用的 MySQL、真实 SDK、多实例并发与灾备均未运行；全量 Maven 通过不覆盖其启用后的行为。这是首版持久占位实现，**不能标记 C2 完成或生产就绪**；长期占位、原生回合不一致和恢复期间的容量行为需要实测裁决。

## 2026-11-02 C2 批次：到期命令公平扫描（本地回归已执行）

- 调整 `DeliveryMapper.findDueIds`：从全局 `due_at,id` 前 20 条改为 MySQL 8 的 `ROW_NUMBER() OVER (PARTITION BY user_id)`，按用户轮次扫描到期命令。
- `PENDING` 命令以 `due_at`、过期 `DELIVERING` 命令以 `delivery_lease_until` 作为排序时间；同用户内按时间、ID 稳定排序。
- 过滤已经明确标记 `DISABLED` 的用户，让其历史待处理命令不反复占用轮询窗口；本地未注册账号仍由原命令领取规则处理。
- 完整保留 `MESSAGE_SUBMIT` 先前回复阻断逻辑，命令领取、租约更新、AgentScope 原生幂等链路不变。
- 回归及风险清单：`docs/architecture/C2_FAIR_DUE_SCAN.md`。

**验收状态：** 真实 MySQL 集成测试 5 项通过；1 万/10 万条执行计划已记录（约 20.7ms/169ms，均全表扫描）；顺序重复 claim 第二次影响 0 行。**限制**：100 个活跃用户各自积压时，连续 8 轮每轮覆盖 20 个不同用户，但仅 1 条的 B 仍未进入候选；当前窗口只防止单用户占满名额，不提供用户数超过窗口时的有界等待。未执行：双进程并发、旧回复阻断真实数据用例、CPU/IO 并发负载。该批**不等同于 C2 完整的运行 Slot 公平调度或全链路容量隔离**。

## 2026-11-02 后续代码批次：C1 请求身份与订阅撤权（本地回归已执行）

- Server 对正式请求要求有效网关 HMAC 身份断言；即使误开启本地身份兼容，strict 模式仍拒绝未签名身份。
- local 模式通过显式 `agentsquad.identity.allow-unsigned-local` 控制兼容头；prod profile 强制关闭。
- 身份断言复核签名、密钥轮换配置、严格到期时间、请求方法/路径和 UUID；拒绝格式异常或过期断言。
- 业务请求入口重新查询用户活跃状态，避免已经禁用的账户继续使用短期已签名请求。
- SSE 订阅建立和每次轮询复核用户活跃状态，账号禁用后停止后续事件推送。
- 需求和回归矩阵见 `docs/architecture/C1_SECURITY_REGRESSION_CASES.md`。

本批次已通过 17 项身份单元测试与 13 项真实 HTTP/SSE 检查（隔离库、strict、enforce-user-status）：未签名 401、合法断言 200、篡改/过期/超期 401、禁用 403、跨用户 404、SSE 禁用后 5 秒内关闭、重新启用 200、第 6 条并发连接 409。**未执行**：经网关的 Cookie/CSRF 与双网关链路、`strict=true` 且 `allow-unsigned-local=true` 的进程组合、真实模型/SDK。

## 2026-11-02 后续代码批次：C1 账户管理补强（部分验证）

本批次在原 C1 部分实现基础上继续补强，不代表 C1 全部验收完成。

- Gateway 账号管理引入统一显示名称/角色/状态参数校验，并对密码输入长度做边界限制；
- 管理员写操作锁定活跃管理员集合，并在事务内保护最后一名管理员，减少双网关并发降权风险；
- 修改密码增加账号行锁、旧密码校验和认证版本 CAS，防止与管理员重置操作发生覆盖；
- 用户创建、更新、密码重置、退出及失败登录增加审计记录；原有成功登录审计保留；
- 用户查询增加真正的 ID 游标分页，返回 `nextCursor`，管理界面可逐页加载；
- 前端用户管理支持“加载更多”、临时密码清屏、限制显示名称长度和操作冲突时刷新。

**验证状态**：Java 全量测试与 TypeScript 构建已通过，但账户管理相关的真实网关登录、Cookie/CSRF、管理员并发竞态、最后管理员保护、密码重置与跨用户越权仍未执行；由于尚未验证这些运行时行为和生产迁移，本次仅说明源码可编译、现有自动化通过，不得标记 C1 GO。

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

1. 本地自动化与 C1 核心链路已通过，但真实模型、网关链路、跨实例和生产迁移未验证，仍不能合并或部署生产。
2. 先由用户在隔离数据库检查 44→48 表结构及账号映射。禁止重建或清空既有库。
3. 首次启用 `SQUAD_AUTH_MODE=database` 前，单独执行受控管理员 bootstrap；注意旧 local 模式账号不会自动迁移为数据库账号。
4. 测试 JWT/HMAC 断言（本实现使用定制短效 HMAC 格式而非标准 JWT）、浏览器 Cookie/CSRF、停用用户后模型和工具安全点；服务之间必须共享同一密钥。
5. 启用 `SPRING_PROFILES_ACTIVE=prod` 前确认内网端口隔离，`SQUAD_IDENTITY_HMAC_KEY` 不少于 32 字节且不进 Git；网关须通过 HTTPS 反向代理供浏览器使用。
6. 本地验证命令由用户自主决定：`mvn -f server/pom.xml -B -ntp test`、`npm --prefix web/console ci`、`npm --prefix web/console run build`、`python3 tools/check_design.py`。
7. 在继续建设 C2 调度和 C3 流式事件前，必须确认 AgentScope Harness 真实 SDK 事件语义，绝不通过直接透传原生事件提供“假流式”体验。

## 本次未执行

- 真实模型 / AgentScope 原生 SDK 端到端：NOT_RUN；
- 经网关的浏览器 Cookie/CSRF、双网关代理链路：NOT_RUN；
- 双进程并发 claim、跨实例调度与恢复：NOT_RUN；
- staging / Docker / CI 流水线与正式发布：NOT_RUN；
- 生产或共享数据库迁移、TLS 与容量压测：NOT_RUN。

目前这不是全部 C1–C4 任务完成报告：C1 已完成本地身份与订阅核心回归，C2 仍缺有界公平调度与跨实例验证。用户可以在此分支继续本地验收与完善。
