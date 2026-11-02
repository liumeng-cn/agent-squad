# C1.0.2 身份数据流与威胁模型

基线：`main@23c057bf5d138f12d772edf4b673a16b4533f0c5`（C1.0.1 创建的
`feature/multiuser-c1-identity` 分支起点）。本文只描述现状与 C1 控制项，
不把计划能力当成已实现证据。

## 1. 现状数据流

```text
Browser (Vite :5173 / 生产同源)
  |  Cookie SQUAD_SESSION + X-CSRF-Token
  v
Gateway :18081 (Spring Cloud Gateway WebFlux)
  |  AuthService: 配置单账号, ConcurrentHashMap 会话
  |  GatewayAuthenticationFilter:
  |    校验 Cookie/CSRF -> 覆盖 X-User-Id/X-User-Role/X-User-Name
  |    移除 Cookie、X-CSRF-Token
  v
Business :18080 (Spring Boot MVC)
  |  UserContextInterceptor: 直接读取 X-User-Id / X-User-Role
  v
Service / MyBatis / MySQL agent_squad（44 表，无账号表）
```

关键代码位置：

| 环节 | 代码 | 现状 |
| --- | --- | --- |
| 登录与凭据 | `server/agent-squad-gateway/src/main/java/com/agentsquad/squad/gateway/auth/AuthService.java:38` | 配置用户名/密码明文 `equals` 比较；无哈希、无账号表、无限流 |
| 会话存储 | 同文件 `:33` | JVM 内 `ConcurrentHashMap`，重启即失效，多实例不共享，无 TTL |
| 会话令牌 | 同文件 `:43` | `UUID.randomUUID()`，非显式 256-bit CSPRNG，无空闲/绝对过期 |
| CSRF 比较 | 同文件 `:68` | 普通 `String.equals`；令牌随 `/me` 返回 |
| Cookie | `.../auth/AuthController.java:40` | `HttpOnly; SameSite=Lax; Path=/`，无 `Secure`，无 `Cache-Control: no-store` |
| 网关身份头 | `.../filter/GatewayAuthenticationFilter.java:35` | 仅覆盖三个头；其他 `X-User-*`、`X-Squad-*` 会透传 |
| 路由 | `.../config/GatewayRoutingConfiguration.java:20` | `/api/**`（除 `/api/v1/auth/**`）全部转发，未限制业务端口来源 |
| 业务身份 | `server/agent-squad-server/src/main/java/com/agentsquad/squad/identity/UserContextInterceptor.java:22` | 无签名、无受众/路径/时间校验；直接信任裸头 |
| CORS | `server/agent-squad-server/src/main/java/com/agentsquad/squad/config/WebConfig.java:29` | `localhost:*` 通配 + `allowedHeaders("*")`；生产仍需同源收敛 |
| 业务数据归属 | 44 表多数含 `user_id` | 归属依赖外部传入的 userId，身份被伪造时隔离失效 |

## 2. 信任边界与攻击面

1. **浏览器 → 网关**：唯一公开入口。Cookie 可被同站 XSS/恶意扩展窃取；
   CSRF 由 `X-CSRF-Token` + SameSite 双重约束。C1 前 Cookie 无 `Secure`。
2. **网关 → 业务服务**：当前只有网络位置与头部约定，业务无密码学验证。
   能在 18080 直连或能写请求头的本机/内网进程可冒充任意用户。
3. **业务服务 → MySQL**：单库账号 `squad`，生产无低权限拆分与迁移版本校验。
4. **运行时 → 外部模型/工具**：模型调用与工具派发前没有账号状态检查，
   禁用账号在会话有效期内仍可产生新的副作用。

## 3. 威胁清单与 C1 控制

| ID | 威胁 | 当前可行性 | C1 控制 | 验证 |
| --- | --- | --- | --- | --- |
| T1 | 直连 18080 伪造 `X-User-Id` 读取他人数据 | 可行 | 网关签发 HMAC 断言；业务只接受已验证断言，生产拒绝裸头 | C1.3.1/1.3.2 真实 HTTP 负例 |
| T2 | 伪造/重放过期断言 | 断言尚不存在，但裸头可无限重放 | `iat/exp<=30s`、`aud/iss/method/path`、`kid`、常量时间比较；允许 <=5s 偏差 | C1.3.2 超时/错 path/重复头 |
| T3 | 会话令牌窃取 | Cookie 无 `Secure`；令牌存内存无 TTL | 会话/CSRF 仅存 SHA256 摘要；空闲 24h + 绝对 7d；生产 `Secure` | C1.2.2 |
| T4 | 登录爆破/撞库 | 无任何限流，错误响应无延迟差异控制 | 用户名+IP 不可逆 bucket 原子窗口计数、渐进锁定、IP 总限流 | C1.1.3 并发登录/锁定测试 |
| T5 | 账号停用后继续执行 | 无账号表与状态，无法停用 | `UserStatusGuard` 接入受理/领取/模型/工具/沙箱；禁用即停新工作 | C1.3.3 竞态测试 |
| T6 | 改密/重置后旧会话存活 | 无改密、无跨实例会话 | 原子 `auth_version+1`，每次受权请求校验；多实例同库 | C1.2.2/C1.4.2 两网关 |
| T7 | 最后一个管理员被禁用 | 无账号管理 | 事务内锁行 + 计数校验；CAS 冲突 409 | C1.2.1 |
| T8 | 管理员读取他人对话 | 单账号时代无路径 | Admin API 只暴露账号摘要；服务按登录 userId 限定归属 | C1.2.3 越权测试 |
| T9 | 首次登录使用临时密码长期有效 | 无 `must_change_password` | 未改密会话仅允许 `/me`、`/password`、`/logout` | C1.2.4 UI + API |
| T10 | 网关 event-loop 被 JDBC/BCrypt 阻塞 | 当前无 JDBC，C1 新增 | 阻塞操作全部 `Schedulers.boundedElastic()`，线程/连接池可配 | C1.1.3 压测 |

## 4. 停用账号的运行安全点

| 安全点 | 代码 | C1 接入方式 |
| --- | --- | --- |
| 消息受理 | `server/agent-squad-server/src/main/java/com/agentsquad/squad/conversation/service/MessageAcceptanceService.java:47` | 事务前 `UserStatusGuard.requireActive` |
| 目标创建 | `.../conversation/service/GoalTaskCreationService.java:93` | 同上 |
| 命令领取 | `.../conversation/service/CommandClaimService.java:25` | 领取事务内校验状态 |
| 执行授权 | `.../runtime/service/ExecutionAuthorizationService.java:19` | 受权判定前校验 |
| 模型调用 | `.../integration/agentscope/ModelBudgetMiddleware.java:41` | 派发前校验，禁用则不新发 |
| 工具/沙箱派发 | Worker 工具执行边界（C1.3.3 定位具体调用点） | 派发前校验；已进入 `IN_FLIGHT` 的按账本收尾 |

## 5. 明确不在 C1 范围

- 30 秒内同端点重放：短时断言不提供一次性 `jti`，依赖网络隔离 + CSRF + 业务幂等。
- mTLS/服务网格：C1 只做共享密钥断言，生产建议由部署层补 mTLS。
- 外部 IdP、OAuth2/OIDC、Redis Session：不引入新基础设施。
- 生产 DDL、部署、推送线上服务：C1 只在隔离库验证，迁移脚本仅前向增量。
- 既有开发数据的 `user_id` 归并：不自动改写；映射脚本只做预检查输出。
