# Tasks

## 1. 分包轻活（低风险，先行）

- [x] 1.1 修正 identity/controlle → identity/controller 拼写：移动 AuthController.java 到 controller 子包，更新所有 import。验证：`mvn -f server/pom.xml -B -ntp test-compile` 通过，无缺失 import。
- [x] 1.2 artifact 顶层散落归位：ArtifactController → controller/、ArtifactMapper → repository/、ArtifactRow → domain/、ArtifactService → service/、ArtifactView → dto/。验证：`mvn test` 通过，artifact 包内子包结构统一。
- [x] 1.3 governance 归位：BudgetService.java → service/、BudgetExceededException.java → common/exception/。验证：`mvn test` 通过，governance 包内 service 子包存在。
- [x] 1.4 worker 归位：WorkerDefinitionService.java → service/。验证：`mvn test` 通过。
- [x] 1.5 清理空目录与 Tool 归类：删除 sandbox/agent 空目录；memory/agent/WriteExplicitMemoryTool.java → agent/tool/。验证：`find server/src/main/java -type d -empty` 无 sandbox/agent；`mvn test` 通过。
- [x] 1.6 阶段1验证：全量测试 + 前端构建。验证：`mvn -f server/pom.xml -B -ntp test`（0 失败）+ `npm --prefix web/console run build` 通过。提交阶段1。

## 2. 网关服务（核心改造）

- [x] 2.1 新建主 pom：server/pom.xml（packaging=pom，modules=[agent-squad-gateway, agent-squad-server]，dependencyManagement 统一版本）。验证：`mvn -f server/pom.xml -q validate` 通过，根 pom 识别两个模块。
- [x] 2.2 新建 gateway 模块骨架：server/agent-squad-gateway/pom.xml（Spring Cloud Gateway Server WebFlux + lombok）、server/agent-squad-gateway/src/main/java/.../GatewayApplication.java、server/agent-squad-gateway/src/main/resources/application.yml（端口 18081，business-service-url=http://127.0.0.1:18080，agentsquad.auth.* 配置）。验证：`mvn -f server/agent-squad-gateway/pom.xml -B -ntp package` 通过，gateway jar 可启动。
- [x] 2.3 迁移认证代码到 gateway：AuthService/AuthSession/AuthView/LoginRequest/AuthController 从 identity 迁到 gateway/auth 包，修正包名。验证：`mvn -f server/agent-squad-gateway/pom.xml -B -ntp package` 通过，gateway 独立启动后 `/api/v1/auth/login` 可用。
- [x] 2.4 网关实现反向代理：配置 Gateway 内置路由 `/api/**`（排除 `/api/v1/auth/**`），GlobalFilter 读 SQUAD_SESSION Cookie 验 token、写请求验 X-CSRF-Token，覆盖 X-User-Id/X-User-Name/X-User-Role 头后交给框架转发，保留 SSE 流式；不编写代理 Controller/Service。验证：网关启动后，带 Cookie 访问业务端点返回正确结果；SSE 端点流式不缓冲。
- [x] 2.5 业务服务改造：删除 AuthService，新增 UserContextInterceptor 读 X-User-Id/X-User-Role 放进 request attribute。验证：`mvn -f server/pom.xml -B -ntp test-compile` 通过，业务服务启动后拦截器生效。
- [x] 2.6 改造 11 个 controller：移除 AuthService 注入与 cookie/csrf 参数，改用 UserContext.current(request).userId()。验证：逐 controller grep `@CookieValue.*SQUAD_SESSION` 无残留；`mvn test` 通过。
- [x] 2.7 前端指向网关：web/console VITE_API_TARGET 改为 http://127.0.0.1:18081。验证：`npm --prefix web/console run build` 通过，前端请求走网关。
- [x] 2.8 测试拆分：网关测认证/CSRF（login/me/logout/401/CSRF 拒绝）；业务服务测试直接设 X-User-Id 绕过网关。验证：网关与业务服务各自的测试套件通过。
- [x] 2.9 阶段2验证：双服务启动 + 端到端。验证：gateway(18081)+business(18080) 同时启动，前端登录→发消息→SSE 链路通；`mvn -f server/pom.xml -B -ntp test` 通过。提交阶段2。

## 3. MyBatis XML（中等风险，渐进迁移）

- [x] 3.1 配置 mapper-locations：application.yml 新增 `mybatis.mapper-locations: classpath*:/mapper/**/*.xml` 与 `mybatis.configuration.map-underscore-to-camel-case: false`。验证：`mvn test` 通过，XML 目录被扫描。
- [x] 3.2 首批 tools 包迁移：ConnectionMapper/OperationMapper/MockTicketMapper/OperationAccessMapper/SecretVaultMapper 转 XML（含 3 个 `<script>` 动态 SQL），删除接口注解。验证：`mvn test` 通过；grep 接口无残留 @Select/@Insert/@Update。
- [x] 3.3 sandbox/worker/artifact 包迁移：SandboxMapper/WorkerDefinitionMapper/ArtifactMapper/ArtifactDeliveryMapper 转 XML。验证：`mvn test` 通过。
- [x] 3.4 governance/memory/interaction/presentation 包迁移：BudgetMapper/MemoryMapper/InteractionMapper/ConversationReadMapper 转 XML。验证：`mvn test` 通过。
- [x] 3.5 runtime 包迁移：CompletionMapper/ContextSnapshotMapper/ExecutionFailureMapper/ExecutionRestrictionMapper/PlanMapper/ProjectionMapper/WorkerDispatchMapper 转 XML。验证：`mvn test` 通过。
- [x] 3.6 conversation 包迁移（最后，最复杂）：13 个 mapper 转 XML，重点核对 FOR UPDATE/JOIN/CASE WHEN/子查询。验证：`mvn test` 通过；全仓 grep 接口无残留 SQL 注解。
- [x] 3.7 阶段3验证：全量测试 + 开发库冒烟。验证：`mvn -f server/pom.xml -B -ntp test`（0 失败）；启动业务服务冒烟 BudgetMapper.lockAccount FOR UPDATE 与 ConnectionMapper 乐观锁。提交阶段3。

## 4. SQL 统一（中等风险）

- [x] 4.1 生成合并清单：以 V1 baseline（15 张表）为基础，逐文件应用 V2-V18 DDL（CREATE TABLE + ALTER），生成表/列/索引/约束/审计字段完整清单。验证：清单含 V1 15 张 + V2-V18 新增 29 张 = 44 张表（含 3 张 agentscope 框架表）。
- [ ] 4.2 合并到单一 baseline：V1-V18 结构变更合并为 V1__baseline.sql（FK 顺序、审计字段口径、中文注释），删除 V2-V18 SQL 文件。验证：`python3 tools/check_design.py` 通过；`grep -l "sq_plan\|sq_worker_job\|sq_artifact\|sq_message" V1__baseline.sql` 命中。
- [ ] 4.3 处理 Java 迁移：V8/V14 数据回填迁移保留为 baseline 之后的 Java 迁移（版本号调整），删除旧 V8/V14 文件位置，辅助类 LegacyConversationBackfill 等归位。验证：`mvn -f server/pom.xml -B -ntp test` 通过，Flyway 历史表仅含合并后 baseline + 回填迁移。
- [ ] 4.4 重建开发库：drop 并重建 agent_squad 库（agent-squad-mysql 容器），启动服务让 Flyway 应用合并后 baseline。验证：Flyway 成功、无校验和错误、`SHOW TABLES FROM agent_squad` 与 V1 baseline 表清单一致。
- [ ] 4.5 阶段4验证：全量测试 + MySQL 端到端。验证：`mvn -f server/pom.xml -B -ntp test`（0 失败）；隔离 MySQL 端到端验证迁移与产物链路。提交阶段4。

## Workflow follow-up

- 四阶段全部完成后，复核并归档本 change。
- 归档前同步 README 包结构与部署说明（双服务启动步骤）。
- 归档前确认 conversation 巨型包拆分是否纳入后续独立 change。
