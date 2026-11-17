# 用户注册与网关认证

用户核心业务由 `server/user/user-server` 实现，主包为 `com.agentsquad.squad.user.server`。`user-api` 只包含跨服务接口与其请求响应模型；Gateway 不访问 PostgreSQL。注册通过显式匿名路由转发用户服务，验证码固定 `8888`，成功后进入登录页。

## 本地启动

已有 PostgreSQL（15432）、Redis（16379）需先启动。首次运行会生成 `.local/auth/config.json`（数据库用户名、密码、Redis密码）及 RSA 密钥；本地文件不提交。可先运行 `./tools/services.sh status` 查看端口，然后编辑配置。

```bash
# 仅新增用户schema、sequence、table及注释，无删除/清空操作
./tools/services.sh init-db
# 构建并启动 user-server、v2-server、Gateway、Vite
./tools/services.sh start
./tools/services.sh status
./tools/services.sh stop
./tools/services.sh restart
```

默认端口分别为18083、18082、18081、5173。页面入口：http://127.0.0.1:5173/v2/login 。脚本不会终止未知端口进程。日志/PID保存在 `.local/auth`；`--skip-build` 可复用现有jar。

端口占用时可通过 `SQUAD_USER_PORT`、`SQUAD_V2_PORT`、`SQUAD_GATEWAY_PORT`、`SQUAD_WEB_PORT` 指定独立端口；`SQUAD_STATE_DIR` 指定独立日志与本地密钥目录。直接IDE启动时需提供配置中的JWT路径、身份密钥、PG/Redis连接环境变量；多Gateway实例必须使用相同JWT密钥与Redis。

## 接口与事务

- POST `/api/v2/users/register`：phone、verificationCode；返回用户。user-server校验验证码并在PG事务内发号、建档，手机号唯一约束裁决并发。
- POST `/api/v2/auth/login`：相同参数；Gateway查询账号、验证状态、签发JWT并写Redis，写入成功才设置Cookie。
- GET `/api/v2/auth/me`：返回user、csrfToken、expiresAt，刷新后重新取得CSRF。
- POST `/api/v2/auth/logout`：校验CSRF并删除当前Redis会话，再清除Cookie。已失效会话重复退出成功。
- GET/PATCH `/api/v2/users/me`：本人资料；更新请求displayName、version，旧版本返回409业务码。

所有业务JSON使用ApiResult。认证401、CSRF/禁用403、依赖故障503；普通业务拒绝HTTP200。服务间查询仅由user-api定义，内部路由不对外开放；按用户约定不使用X-Service-Key，服务只监听本机或可信内网。

## 身份与持久化

PG `squad_user.sq_user` 保存用户，`user_id`由独立sequence生成，username为`user_`+业务ID。display_name为空时回退用户名。主键id与业务ID分离，user_id、username、phone唯一。应用启动不执行DDL，baseline与schema.sql保持一致。

JWT固定RS256，包含sub/jti/iss/aud/iat/exp/authVersion；仅HttpOnly、SameSite=Lax Cookie持有。默认7天，不滑动续期。Redis `squad:auth:session:{jti}`保存用户ID、认证版本、CSRF与期限，TTL与JWT对齐；不保存JWT。Redis是会话权威，丢失需重登；灾备恢复可能恢复已撤销会话，必须配合Redis持久化/副本策略，当前未声明灾备已验收。

每次认证检查Redis和user-server当前状态及auth_version，不使用进程本地登录缓存。Gateway清除外部身份头，短时签名绑定userId/sessionId/目标服务/HTTP方法/路径；user-server和v2-server验签后取当前用户，聊天仍由业务Service按用户归属定位。异步流程显式传userId。退出不取消已受理Agent任务，已建立SSE在重连时重新认证。

## 验证

```bash
mvn -f server/pom.xml -pl gateway,user/user-server -am test -Daether.syncContext.named.factory=noop
npm --prefix web/workbench run build
npm --prefix web/workbench test
SQUAD_AUTH_TEST_URL=http://127.0.0.1:18081 SQUAD_USER_TEST_URL=http://127.0.0.1:18083 python3 tools/verification/auth_smoke.py
```

冒烟测试创建随机测试手机号，不删除现有数据；指定 `SQUAD_SECOND_GATEWAY_URL` 可验证跨实例退出。
