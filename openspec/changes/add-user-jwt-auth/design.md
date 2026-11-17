# Design

## Context
当前 user-api/user-server 为骨架，Gateway 为 WebFlux；用户要求 PostgreSQL、Redis、JWT，固定测试验证码 8888。当前约定优先于旧 OpenSpec 中 MySQL 基线。

## Goals / Non-Goals
完成手机号注册、登录、退出、昵称编辑、可信身份及一键启停。不接真实短信、不做 Refresh Token，不迁移旧账号。

## Decisions
- 注册 POST /api/v2/users/register（phone、verificationCode）匿名转 user-server，成功返回 UserVO 并进入登录。登录 POST /api/v2/auth/login 由 Gateway 校验验证码并经 UserClient 查询账号事实。
- GET /api/v2/auth/me 返回 user、csrfToken、expiresAt；POST logout 撤销会话；GET/PATCH /api/v2/users/me 获取/更新昵称（displayName、version）。业务响应 ApiResult，认证失败 401、权限不足 403、依赖失败 503。
- PostgreSQL squad_user.sq_user 使用独立 identity 主键、sequence 业务ID，username=user_业务ID；phone/user_id/username 唯一。昵称默认空；status ACTIVE/DISABLED，auth_version/version 默认0，统一审计字段 timestamptz。不建数据库登录会话表；DDL 明确执行，不在启动时刷库。
- Gateway 使用 RS256 JWT（sub、jti、iss、aud、iat、exp、authVersion），Cookie HttpOnly SameSite=Lax，7天固定到期。私钥仅 Gateway 持有，多实例使用相同配置。Redis squad:auth:session:jti 保存 userId/authVersion/csrfToken/过期时间，TTL 与 JWT 对齐；不存 JWT。CSRF 随机值仅在会话查询返回，写请求校验。
- 每次认证查询 Redis 及 user-server 当前状态/认证版本，不缓存权限事实，失效及时而代价是一次内部查询。Redis 丢失需重登；依赖失败返回503。退出删除当前会话，多设备独立。
- user-api 只放 Feign UserClient 及 DTO/VO；user-server 主包com.agentsquad.squad.user.server，Controller→Service→Mapper，独立转换用MapStruct；Gateway 无关系库依赖，Feign 在 boundedElastic 调用并设置超时。
- Gateway 使用路径/方法谓词和显式匿名标记。默认受保护；清理伪造身份头；下游身份断言使用独立 HMAC 密钥绑定用户、目标、方法、路径和30秒时效，下游验签取用户，不信任 X-User-Id。内部查询不使用X-Service-Key，通过仅本机/内网监听及不配置外部路由隔离。当前只有登录用户权限，不新增RBAC表；资源归属仍在业务Service。
- 注册 PG 事务不跨Redis，注册后单独登录；编辑昵称用乐观锁，清空回退username。前端独立v2认证状态和路由，保持现有白色/灰绿色圆角样式。
- 启动脚本检查PG/Redis、生成本地密钥、构建、启动服务并等健康检查；PID与日志保存在.local，不杀未知进程。DDL单独init-db命令只创建本模块结构。

## Risks / Trade-offs
固定验证码仅当前测试流程。Redis恢复可能恢复已删除的会话，要求使用持久化和副本并说明灾备边界。已有SSE在建立时认证，退出不取消后台执行；重新连接必须重新认证。

## Migration Plan
新增独立schema不改旧表。显式执行schema.sql后启动。移除v2本地默认用户，前端统一经Gateway。回退停止新服务，不删除用户数据。

## Open Questions
无阻塞问题；不声明Agent执行多副本已验收。
