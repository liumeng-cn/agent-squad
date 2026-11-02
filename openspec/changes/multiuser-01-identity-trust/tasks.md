# Tasks：C1 身份与可信网关（严格顺序，未验证不得勾选）

## 0. 基线与契约
- [x] C1.0.1 确认当前 Git 提交、工作区状态与现有测试命令；创建 `feature/multiuser-c1-identity`，不得覆盖现有改动。输出 `evidence/baseline.md`（SHA、构建/测试真实结果与跳过项）。
- [x] C1.0.2 审阅 `access/runtime/web-console`、`api/openapi.json`、44 表 DDL 与 Gateway 模块；形成数据流和身份威胁模型，说明各入口、信任边界、账号停用的运行安全点。验收：链接到真实代码路径。
- [x] C1.0.3 更新此 change 的 spec delta 与 OpenAPI（先契约后实现）。验收：`python3 tools/check_design.py`（所需依赖齐全时）通过；标注既有 runtime schema/启动语义冲突并同步修复。

## 1. 数据库与持久身份
- [x] C1.1.1 在开发 baseline 增加 `sq_user/sq_auth_session/sq_auth_rate_bucket/sq_auth_audit`（中文注释/审计字段/索引）；创建生产迁移快照 V1 和增量 V2，严禁 DROP 开发库；验证空隔离库 48 表与 schema parity，记录 DDL 证据。
- [x] C1.1.2 区分 local/test/prod SQL 初始化：local/test 隔离库允许 baseline，prod `sql.init.mode=never` 且验证 schema 版本；生产默认使用低权限 DB 账号。测试 prod 配置不会在启动时写 DDL。
- [x] C1.1.3 Gateway 引入 MyBatis Mapper/XML 持久化 + `@Transactional`（用户明确要求全部使用 MyBatis，不引入 JdbcTemplate）、PasswordEncoder、登录限流和过期会话清理；所有阻塞 SQL/密码计算 offload 到 boundedElastic，连接池和调度线程可配。测试：真实 MySQL 事务与并发登录/锁定。

## 2. 身份生命周期与 API
- [x] C1.2.1 实现账号创建/查询/状态/角色/密码重置服务；最后一个活动管理员不可禁用；初始管理员由一次性 CLI bootstrap 手动创建；测试唯一用户名、CAS 冲突与 bootstrap 幂等。
- [x] C1.2.2 替换单账号 `AuthService` 为持久 Session：CSPRNG Token/CSRF、摘要落库、绝对+空闲 TTL、跨实例登出/禁用/改密撤销；接口仍保持现有 Cookie/CSRF 语义。测试：Cookie Secure、no-store、过期、失效、跨实例会话。
- [x] C1.2.3 新增 `/api/v1/auth/password`、`/api/v1/admin/users` CRUD 的受限操作（以 design.md 接口为准），错误语义统一且内容脱敏；测试 ADMIN 不可读他人对话。
- [x] C1.2.4 Web 登录与路由守卫适配强制首次改密、新管理员用户列表与创建/禁用/重置页面；无公开注册入口。验证：Vue build、两用户 UI 流程、401 退出清空 SSE/会话缓存。

## 3. 可信内部身份与执行安全点
- [x] C1.3.1 Gateway 清理来自浏览器的内部头，并签发带 method/path/iat/exp/aud/kid 的短效 HMAC 断言；Business 严格校验并只从断言构建 UserContext；生产拒绝未签名/错误签名请求。真实 HTTP 集成测试覆盖重定向、multipart 和 SSE，不破坏原始字节/事件流。
- [x] C1.3.2 补充密钥轮换（当前/上一把）、超时/签名/重复头/错误 path 负例；测试直接访问 18080 伪造 `X-User-Id` 返回 401。
- [x] C1.3.3 增加 `UserStatusGuard` 并接入受理、命令领取、模型调用、外部操作、沙箱的受控安全点；禁用后不再新发模型/写入，IN_FLIGHT 维持账本核对语义。测试禁用与执行竞态。
- [x] C1.3.4 将基于裸 `X-User-*` 的旧业务测试迁移为测试签名断言/完整真实网关，保留明确模拟身份的**内部单元测试**，不能留下生产绕过开关。

## 4. 完成门槛
- [x] C1.4.1 `mvn -f server/pom.xml -B -ntp test`、`npm --prefix web/console run build`、`python3 tools/check_design.py` 实际通过；环境跳过逐项记录，不允许写“全部通过”掩盖 skipped。
- [x] C1.4.2 使用两个真实账号验证登录、修改密码、退出、管理员禁用、越权、SSE、发送消息、断网后快照恢复；两个网关实例同库读取同一 Session。将 HTTP 状态、DB 状态与结果存 `evidence/acceptance.md`，不记录密钥/Token。
- [x] C1.4.3 提交本阶段代码/契约/文档与验收证据；未确认所有门槛前禁止启动 C2；禁止生产 DDL、部署或推送线上服务。
