# 2026-11-03 多用户分支整合记录

## 分支与策略
- 集成分支：`feature/multiuser-integration-20261103`
- C1 基础：`caed48ab62889a19b08236f4a281a69a625bbae6`
- C2–C4 来源：`8c66b1b1d2b7213a460fa5bfaf58026884bf1bea`
- 保留 C1 的 MyBatis 身份、跨网关会话、HMAC 断言安全实现及 `web/workbench`；整合 C2 公平扫描、持久运行容量与 C4 监控和 staging 文件。

## 冲突取舍
- 认证服务、SignedIdentityVerifier、UserContextInterceptor、UserStatusGuard 及安全回归测试保留 C1 版本，不恢复旧的 JDBC 用户状态查询。
- 保留可兼容 C1 的 UserContextInterceptorTest；旧 C4 UserStatusGuardTest 依赖已经移除的 jdbc 字段，因此不引入该测试，使用 C1 MyBatis 相关验收。
- C2 新增两张运行容量表；开发 baseline 50 表，生产 V1/V2 不变，V3/V4 为新迁移。SchemaParityTest 已增加 V3/V4 结构对齐断言。
- Gateway 引入 Actuator 与 Prometheus 依赖；沿用 C1 的 `SQUAD_AUTH_SECURE_COOKIE` 配置名称；修正 Compose 环境变量及工作流前端路径。
- `SQUAD_ADMISSION_ENABLED=false` 为默认值；本次不启用真实运行占位，不操作生产数据。

## 未完成验收
- 整合代码仅完成 Git 对象级合并和静态契约核对；Maven、MySQL、npm、真实 SDK、双网关、Docker、staging 均 **NOT_RUN**。
- 原 C1 和 C2–C4 的历史测试不自动转化为整合分支验收记录。
- staging 需要版本迁移权威（`flyway_schema_history`）及独立 TLS 等准入校验。

## 本地验收命令
```bash
mvn -f server/pom.xml -B -ntp test
npm --prefix web/workbench ci
npm --prefix web/workbench run build
python3 tools/check_design.py
```
先隔离 MySQL 验证 V1→V4 parity、账户安全回归和持久 Slot 多实例恢复，再评估是否合并 main 或上线。
