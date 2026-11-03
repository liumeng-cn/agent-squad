# C1.1 数据库与持久身份证据

验证时间：2026-11-02；环境：本地 MySQL 8.4，隔离库 `as_test_*`（JVM 退出自动删除），
本地开发库 `agent_squad` 未删除、未重建。

## 1. 表结构与迁移（C1.1.1）

- 开发 baseline `server/agent-squad-server/src/main/resources/db/migration/V1__baseline.sql`
  追加 `sq_user`、`sq_auth_session`、`sq_auth_rate_bucket`、`sq_auth_audit`，
  空隔离库为 48 张表；四表均含中文注释、公共审计字段与必要唯一/查询索引。
- 生产迁移：`deploy/db/migrations/V1__initial_44_tables.sql`（改动前 44 表冻结快照）
  + `V2__multiuser_identity.sql`（仅前向 `CREATE TABLE`，无 DROP/DELETE）。
- 结构一致性测试 `SchemaParityTest`：分别用开发 baseline 与生产 V1+V2 建两个隔离库，
  断言 48 表且列、索引完全一致。

真实结果：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- SchemaParityTest
```

## 2. 配置区分与生产闸门（C1.1.2）

- `application.yml` 只保留公共配置，`spring.profiles.default: local`。
- `application-local.yml`：本地数据源默认值与 `spring.sql.init.mode: always`（回放 baseline）。
- `application-prod.yml`：数据源必须由环境变量提供，`spring.sql.init.mode: never`。
- `SchemaVersionGate`（仅 `prod` profile）启动时校验 `flyway_schema_history` 成功版本
  不小于期望值且四张认证表存在，否则拒绝启动；不会写 DDL。

真实结果：

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- SchemaVersionGateTest
```

其中“空库直接以 prod 启动”用例按预期失败并输出“生产启动要求已执行版本化迁移”；
日志中的 `Application run failed` 是该负例的预期输出。

## 3. 网关持久认证（C1.1.3）

按用户要求全部使用 MyBatis，不使用 `JdbcTemplate`/`TransactionTemplate`：

- `AuthMapper` + `mapper/auth/AuthMapper.xml`：账号查询、会话插入/续租/撤销/批量撤销、
  限流窗口行锁累加、审计插入、过期会话与旧限流桶分批删除。
- `AuthRepository`：`@Transactional` 承载“锁行 + 计数 + 锁定”与“会话写入”事务。
- `AuthService`：Reactor 入口，所有阻塞 SQL 与 BCrypt 经
  `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` 执行。
- 登录失败按“规范化用户名 + 来源 IP”派生 SHA-256 bucket key 双维度计数；
  会话令牌 256 bit CSPRNG，数据库只存 SHA-256(token)。
- CSRF：由会话令牌经 HMAC-SHA256 派生，`/login` 与 `/me` 返回同一值，
  数据库仍只存摘要；避免刷新或多标签页重复取 `/me` 时互相失效。
  （设计 §5 已同步该实现方式。）

真实 MySQL 集成测试：

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- GatewayProxyHttpTest
```

覆盖：登录/`/me`/退出；Cookie `HttpOnly` 与本地非 `Secure`；无 CSRF 写请求 401；
伪造 `X-User-Id`/`X-Squad-Assertion` 被清除；multipart 原始字节保真；
SSE 上游结束前首帧可见；临时密码未改业务 403；连续失败 5 次锁定 429；
数据库仅保存 token/CSRF 摘要；`/me` 刷新返回同一 CSRF 且可用于后续写请求。

## 4. 未覆盖/限制

- 本地开发库未执行新增四表（只更新 baseline 与测试隔离库）；需在后续本地启动时
  由 `application-local.yml` 回放 baseline，生产由用户执行 V2。
- 双网关实例共享会话与并发登录锁定的多实例压测在 C1.4.2 验收中继续验证。
