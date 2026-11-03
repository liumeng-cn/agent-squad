# 隔离 staging 操作手册（未验收）

## 状态
本文是部署文件示例，不表示已运行镜像、数据库或 HTTPS 验证。

1. 使用独立且已授权的 staging MySQL 8.4，绝不使用个人/生产数据库。
2. 将完整 50 表 baseline（44 表基础、4 表身份、2 表运行准入） 手动导入**空白** staging 数据库；应用使用 prod profile 时不会自动执行建表 SQL。
3. 配置 `SQUAD_DB_PASSWORD`、`SQUAD_DB_ROOT_PASSWORD`、`SQUAD_IDENTITY_HMAC_KEY`（至少32字节）和 `SQUAD_CONNECTION_SECRET_KEY`，不得写入 Git。
4. 在项目根目录先执行 `mvn -f server/pom.xml package -DskipTests` 生成两个 jar（本次未运行）；`docker compose -f deploy/compose.staging.yml build` 后自行启动。
5. 只绑定 localhost 的 Gateway，业务 Server 和 DB 没有宿主机公开端口。
6. Gateway Cookie `Secure=true`，因此浏览器使用必须经**外部 HTTPS TLS 代理**；HTTP localhost 仅作为服务端诊断端点，不应尝试通过非 HTTPS Cookie 登录。
7. 管理员 bootstrap 必须由操作员在隔离环境**显式**短时开启，账号库非空时拒绝再次初始化；不要在正常服务的环境中长期保留 bootstrap 密码。
8. 上线前必须完成 HMAC 验证、多用户隔离、SDK 恢复、性能压测、备份还原及容器健康检查；不允许凭 compose 文件就声明生产可用。

9. C1 `SchemaVersionGate` 依赖 `flyway_schema_history`；目前 Compose 未自动执行或登记迁移。隔离 staging 必须先核对版本记录，否则 prod profile 会拒绝启动。本文件不是可直接上线的部署证明。
10. C2 运行准入默认关闭，启用前必须完成 V3/V4、SDK 和多实例恢复专项验收。
