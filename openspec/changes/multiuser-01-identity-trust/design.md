# Design：C1 多用户身份与可信网关

## 1. 当前源码边界与选择

基线 `main@23c057bf5d138f12d772edf4b673a16b4533f0c5`：网关 Spring Cloud Gateway WebFlux，单账号 `AuthService` + `ConcurrentHashMap`，业务端 MVC `UserContextInterceptor` 信任 `X-User-Id/X-User-Role`，业务表的绝大多数用户资源已含 `user_id`；网关原本无 DB。OpenSpec `access` 已要求禁用/authVersion 失效但没有实现。

**选择**：保留双模块（Gateway、Server），网关通过 MyBatis Mapper/XML + MySQL 提供持久认证，业务服务通过同库只读账号状态并核验带签名内部断言。**不引入 Redis、独立 IAM 微服务或 JWT 浏览器令牌**。理由：当前容量和模块化单体可由 MySQL 解决；浏览器保留 HttpOnly Cookie，撤销能立即生效；不增加第三种基础设施。

**替代**：OAuth2/OIDC 外部身份服务——长期可用，但改变独立部署与收费；Redis Session——高性能，但引入新组件；单纯内网 IP 信任——无法抵挡内部误配置/绕过网关。后续再评估外部 IdP，不在 C1 实现。

## 2. SQL 结构（概念契约，执行时按既有字段规范落地）

以下为建议结构；**先检查现有 DDL 的公共审计字段、命名约束、字符集及索引风格后再生成最终 DDL**。业务 UUID 使用 CHAR(36)；存储主键 BIGINT；业务审计字段沿用仓库规范。不得 `DROP/DELETE` 用户实际数据。

```sql
-- 新增账户，用户名规范化后唯一；不可把 email 当作已验证事实
CREATE TABLE sq_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '存储主键',
  user_id CHAR(36) NOT NULL COMMENT '用户业务ID',
  username VARCHAR(64) NOT NULL COMMENT '规范化用户名',
  display_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '显示名称',
  password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt或Argon2密码哈希',
  role VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER或ADMIN',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE或DISABLED',
  auth_version BIGINT NOT NULL DEFAULT 0 COMMENT '授权撤销代际',
  must_change_password TINYINT(1) NOT NULL DEFAULT 1 COMMENT '首次登录修改密码',
  -- 按项目基线补齐 creator_id/creator_name/create_time/last_updater_id/last_updater_name/last_update_time/del_flag
  PRIMARY KEY (id), UNIQUE KEY uk_user_id (user_id), UNIQUE KEY uk_username (username)
) COMMENT='用户身份';

CREATE TABLE sq_auth_session (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id CHAR(36) NOT NULL COMMENT '会话业务ID',
  user_id CHAR(36) NOT NULL,
  token_hash CHAR(64) NOT NULL COMMENT '原始会话密钥SHA256',
  csrf_hash CHAR(64) NOT NULL COMMENT 'CSRF随机值SHA256',
  issued_auth_version BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  last_seen_at DATETIME(6) NOT NULL,
  idle_expires_at DATETIME(6) NOT NULL,
  absolute_expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_session (session_id), UNIQUE KEY uk_token_hash (token_hash),
  KEY idx_session_user (user_id, revoked_at), KEY idx_session_expire (idle_expires_at)
) COMMENT='持久登录会话';

CREATE TABLE sq_auth_rate_bucket (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  bucket_key CHAR(64) NOT NULL COMMENT '隐私化账户/IP限流键',
  window_start DATETIME(6) NOT NULL,
  failures INT NOT NULL DEFAULT 0,
  locked_until DATETIME(6) NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_bucket (bucket_key)
) COMMENT='登录失败限流状态';

CREATE TABLE sq_auth_audit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id CHAR(36) NOT NULL,
  user_id CHAR(36) NULL,
  actor_user_id CHAR(36) NULL,
  event_type VARCHAR(48) NOT NULL,
  result VARCHAR(16) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  request_id VARCHAR(64) NOT NULL DEFAULT '',
  PRIMARY KEY(id), UNIQUE KEY uk_auth_audit (event_id), KEY idx_auth_audit_user (user_id, occurred_at)
) COMMENT='认证安全审计，不存令牌、明文密码和完整IP';
```

DDL 实施要求：4 表与既有 44 表形成空库 48 表；增加完整公共审计字段和 FK/复合归属索引时保持仓库约定；记录每项索引的查询来源。对既有单用户开发数据，**禁止擅自归并/改写其 user_id**；为当前已有 user_id 创建可登录账号的迁移映射由脚本预检查输出并提示用户决定，迁移不能凭默认 `000...001` 自动覆盖其他身份。

## 3. 数据库环境与迁移策略

当前 `V1__baseline.sql` + `spring.sql.init.mode=always` 属开发实现，不可直接用于正式在线部署。此变更明确更新 `openspec/config.yaml`、`runtime` spec 和 README 中相互矛盾的表述：

- `local/test`：保留单文件完整 baseline，增加上述表；测试继续使用 **隔离 MySQL schema**。严禁清理现有开发库。
- `production`：`spring.sql.init.mode=never`，启动仅 `validate` schema version；禁止服务启动执行 DDL。发布操作由单独 DBA/部署作业完成。
- 在 `deploy/db/migrations/` 固化 `V1__initial_44_tables.sql`（来自本次 Git 基线的原始 44 表快照，冻结后不可改）与 `V2__multiuser_identity.sql`（新增 4 表），使用独立 Flyway CLI/受控迁移作业维护版本；**不得**以 `baselineOnMigrate=true` 自动接管现有库。已有数据的库先人工备份、检查表/列/索引一致性，再由操作者显式 baseline 和 apply；空库可 V1→V2。业务服务只验证迁移版本，不持有数据库 DDL 权限。
- 新增自动化 `schema-parity`：在隔离库上比较“开发 baseline”与“生产 V1→V2”的最终表/列/索引；排除 `flyway_schema_history` 自身。以后生产新增 V3，开发 baseline 也必须同步，自动校验一致。
- 当现有仓库设计约束与本变更冲突，以**经本 change 审核后的新规范**为准；不假装原始规范已经允许版本迁移。

## 4. 账号与密码生命周期

- 用户名：trim + 小写 Locale.ROOT 规范化，3–64 字符，服务端唯一校验；用户名不允许跨账户重复，displayName 可重复。
- 密码：随机临时密码 20+ 字符，或用户设置的强密码；采用 Spring Security Crypto `BCryptPasswordEncoder`（成本参数可配置，合理上限校验）或等效经审计 PasswordEncoder，使用自带安全盐；禁止 SHA256/MD5 单独存密码。恒定时间比较密钥摘要；登录错误统一响应。
- 仅 `ADMIN` 可创建用户、禁用/启用和重置临时密码；API 不返回其他人的密码哈希。临时密码仅通过创建/重置接口响应显示**一次**，带 `Cache-Control: no-store`，不得日志记录；必须首次改密，未改密会话仅允许 `/me`、`/password`、`/logout`。
- 管理员自己不能禁用最后一个活动管理员。注册默认邀请制，无 SMTP/短信依赖。
- 管理员初始化：独立 `bootstrap-admin` 命令/脚本，经显式环境变量和空用户表检查创建初始管理员，创建后删除/清除明文初始密码；应用启动**不能**自动覆盖账户或重复初始化。无管理员时生产失败提示初始化，不输出密码。
- 改密/重置/禁用/角色变化均原子 `auth_version=auth_version+1`，使现存会话失效。用户自行改密返回重新登录响应；当前会话也失效，以避免保留旧授权。

## 5. 持久会话与 CSRF

- 浏览器 Cookie 名继续 `SQUAD_SESSION`，值为 CSPRNG 生成的 256-bit 随机密钥（base64url）；数据库**只存 SHA256(token)**，不可存明文。
- 过期：滑动空闲 24 小时 + 绝对 7 天（可配）；活跃更新 `last_seen_at/idle_expires_at` 每 5 分钟最多一次；过期/撤销/禁用/auth_version 不一致始终 401。多网关实例读同一 DB；退出/改密/停用跨实例立即失效。
- CSRF 128-bit 以上随机值，数据库存摘要；由会话令牌经 HMAC-SHA256 派生（而非每次重新随机构造），使刷新或多标签页重复取 `/me` 得到同一令牌、不互相失效。`/login` 和 `/me` 返回给已验证的同源客户端，写请求 `X-CSRF-Token` 必须匹配；保持 `web/console/src/shared/api/csrf.ts` 与 HttpOnly Cookie 分离。`/me` 响应 no-store；CORS 生产只允许明确同源，不接受 `*` 或 `localhost:*`。
- 生产 Cookie：`HttpOnly; Secure; SameSite=Lax; Path=/`，按环境明确控制 Secure，不可仅依靠 `X-Forwarded-Proto` 无条件信任；所有 auth 响应 `Cache-Control: no-store`。使用 HTTPS/HSTS（部署阶段落实）。
- 登录防爆破：对规范化用户名 + 来源 IP 派生不可逆 bucket key，使用 DB 原子窗口计数或渐进锁定；推荐默认 5 次失败/15 分钟、15 分钟冻结，**不可令单个恶意客户端轻易锁死所有账号**；额外设 IP 维度总限流。失败不暴露账户是否存在，成功审计不记明文 IP。
- 数据清理：定时分批清理已过期会话与旧 rate bucket；不使用 JVM 无上限 Map 做正式会话权威。

## 6. 可信内部身份断言

当前 Gateway 的 `GatewayAuthenticationFilter` 设置 `X-User-Id`，业务仅检查非空。替换为：

1. Gateway 从持久会话取得已认证账号，移除所有外部传入的 `X-User-*`、`X-Squad-Assertion` 等内部身份头；不转发 Cookie、原始 CSRF 到业务（保持现行为）。
2. Gateway 使用唯一内部共享密钥（不少于 256 bit；从密钥管理/环境注入）生成 `X-Squad-Assertion`。格式可用简单 `base64url(payload).base64url(HMAC-SHA256(payload))`，固定 JSON 字段并校验严格：`iss=agent-squad-gateway`、`aud=agent-squad-server`、`sub=userId`、`roles`（角色数组）、`authVersion`、`iat`、`exp<=iat+30s`、`method`、`path`、`requestId`、`kid`。签名覆盖全部 payload 字节；时间判定允许有限时钟偏差（<=5s）。
3. Business `UserContextInterceptor` 在所有 `/api/**` 路由上验证签名、密钥版本、受众、方法、规范化后的转发路径与时间窗，然后设置 `UserContext`；**禁止在生产回退读取 `X-User-Id`**。常量时间签名比较，拒绝带多个同名认证头、缺失/超长/畸形断言。
4. 所有业务端点只在受限私有网络监听；公开反向代理只暴露 Gateway，服务间使用 TLS（生产更推荐 mTLS）。短时断言只是纵深防御，不替代网络隔离。若未来出现跨网关重放风险，再评估 jti 存储或 mTLS 绑定；当前不得将此方案宣称为防止被盗断言在 30 秒内同端点重放。写接口依靠原有业务幂等、CSRF 和网络隔离。
5. 密钥轮换：按 `kid` 支持当前与上一把公用 HMAC 密钥的短暂双读，网关只用当前钥签名；旧钥超过最大断言寿命后撤销。禁止把公用密钥写在 Git 或日志。
6. 网关已全面移除 `X-User-*`、`X-Squad-*` 与 `Cookie`/`X-CSRF-Token` 的转发，不存在日志兼容旁路；业务身份**只能来自已验证断言**。测试使用真实 HMAC 断言，不能直接伪造普通头获取 200。

## 7. 禁用账号对运行时的约束

- 新增 `UserStatusGuard.requireActive(userId)`，在 `MessageAcceptanceService`、`GoalTaskCreationService`、`CommandClaimService`（或等价领取安全点）、`ExecutionAuthorizationService`、`ModelBudgetMiddleware`、工具/沙箱实际派发前统一验证。
- `DISABLED` 阻止新模型调用/工具外发，不删除会话数据，不直接将活跃写入标记 CANCELLED；进行中的 `IN_FLIGHT` 经账本收尾或 UNKNOWN 核对。账号恢复后必须通过受控重试/恢复重新授权，不自动重放外部写操作。
- 管理员用户操作不包含读取他人消息的路径；Admin APIs 必须从受信任身份获取角色，测试必须验证普通用户调用失败。

## 8. API 合同（写入 `api/openapi.json`）

- 保留 `POST /api/v1/auth/login`、`GET /api/v1/auth/me`、`POST /api/v1/auth/logout`，返回扩展字段 `userId, username, roles, csrfToken, mustChangePassword`；具体 DTO 先以现有 `AuthView` 为基础演进，避免并行造另一套结构。
- `POST /api/v1/auth/password`：`{ "currentPassword": "...", "newPassword": "..." }`；成功 204，撤销旧会话（客户端回登录）。
- `GET /api/v1/admin/users?cursor=&limit=...`：用户摘要分页，最多 100 条。
- `POST /api/v1/admin/users`：`{ "username":"user_a", "displayName":"用户A", "role":"USER" }`；201 `{ "userId":"uuid", "temporaryPassword":"仅一次" }`。
- `PATCH /api/v1/admin/users/{userId}`：`{ "expectedVersion":0, "displayName":"...", "role":"USER", "status":"ACTIVE" }`；CAS 409 冲突；角色/状态变更撤销会话。
- `POST /api/v1/admin/users/{userId}/reset-password`：一次性临时密码响应 + 旧会话失效；管理员操作审计。
- 错误：401 未认证/会话失效；403 已认证但无权限；404 他人资源不可见；409 状态版本冲突；429 速率限制（可选 Retry-After）；不返回密码摘要、原始 Session、完整 IP、SQL 内部信息。

## 9. 事务、并发与测试

- 账户创建：唯一键冲突映射 409；管理员禁用/角色改动/最后管理员限制在事务内锁行；会话撤销与 authVersion 更新同事务。
- 会话验证：`token_hash` 走唯一索引；读取 account.status/authVersion，每次受权请求都验证，不能只检查 token 存在；并发登出/改密下，验证请求可能先于撤销——执行安全点再校验状态，并测试此窗口。
- Gateway Reactor 不能直接在 event loop 执行 JDBC/BCrypt：通过专用 service 在 `Schedulers.boundedElastic()` 执行阻塞操作，配置线程/连接池上限；在压力测试中验证无 Netty event-loop 阻塞。
- 新增网关与业务 E2E：两个真实用户、两个网关实例、会话跨实例、CSRF、伪造头、重放/过期断言、账号停用、密码重置、最后管理员保护、审计脱敏、验证码/登录节流防绕过。

## 10. 回滚与门槛

- C1 仅在隔离库实现、测试，不自动发布生产。正式上线前保存迁移前快照并演练回滚。DDL **只前向增加**；回滚应用版本可短期保留新表，不能直接 DROP。
- 可回滚代码，不允许把老式不验证身份的业务接口暴露给公网。过渡期旧本地登录仅可在显式 `local` profile 使用且默认关闭；生产启动必须检测并拒绝 insecure 兼容开关。
- 交付报告：记录 commit、schema 版本、E2E 结果、失败/跳过用例、已知限制；未经演练不能宣称生产可用。
