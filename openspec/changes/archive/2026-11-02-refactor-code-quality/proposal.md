# Proposal: 四维代码质量重构

## Why

当前 agent-squad 已完成 D0–D6 全阶段本地验收（138 项测试 0 失败），功能链路可用，但代码质量存在结构性问题，随用户量和需求增长会加剧职责混乱：

1. **认证散布**：认证逻辑散布在 11 个 controller、约 40+ 方法，每个接口各自注入 `AuthService` 并调用 `authenticateWrite(token, csrf)`，字段命名三种不一致（`authService`/`auth`/`authentication`）。认证是横切关注点，应在统一入口处理。
2. **持久层全注解**：33 个 Mapper 接口全部用 MyBatis 注解（183 @Select / 42 @Insert / 112 @Update / 2 @Delete），0 个 XML 文件，复杂 SQL（多行文本块、动态 `<script>`、多表 JOIN）直接写在注解里，可读性差。
3. **SQL 迁移分散**：Flyway 迁移 V1–V18（含 2 个 Java 迁移 V8/V14），结构分散在多文件，不利于维护和审查。
4. **包结构不一致**：`identity/controlle` 拼写错误、artifact/governance/worker 顶层散落、conversation 巨型包（82 文件）、空目录、Tool 归类混乱。

本次重构聚焦代码质量，不改变外部行为契约（API、认证语义、数据约束不变）。认证行为已由 access spec（R01）定义，网关服务只是将其集中到独立服务实现，不引入新的 spec 级行为。

## What Changes

按用户确认的顺序分四阶段实施，每阶段独立提交、验证后推进：

### 阶段1 分包轻活（低风险）
- 修正 `identity/controlle` → `identity/controller` 拼写
- artifact 顶层散落归位：ArtifactController → controller/、ArtifactMapper → repository/、ArtifactRow → domain/、ArtifactService → service/、ArtifactView → dto/
- governance：BudgetService → service/、BudgetExceededException → common/exception/
- worker：WorkerDefinitionService → service/
- 删除 `sandbox/agent` 空目录
- `memory/agent/WriteExplicitMemoryTool` → `agent/tool/`
- 验证：`mvn test` 通过

### 阶段2 网关服务（核心改造）
- 使用 server/pom.xml 主 pom（packaging=pom，modules=[agent-squad-gateway, agent-squad-server]），统一依赖版本
- 新建 `../../../server/agent-squad-gateway` Maven 模块（独立 Spring Boot 服务，端口 18081）
- 迁移 AuthService/AuthController/AuthSession/AuthView/LoginRequest 到 gateway
- 网关使用 Spring Cloud Gateway Server WebFlux 内置路由转发 `/api/**`（排除 auth）；GlobalFilter 校验 token + CSRF 并覆盖可信用户头，框架处理 HTTP 与 SSE 流式传输，不编写代理 Controller/Service
- 业务服务：删除 AuthService，加 UserContextInterceptor 读 `X-User-Id`，11 个 controller 改用 UserContext（机械改动）
- 前端 VITE_API_TARGET 指向网关
- 网关无 DB（认证用内存会话 + 配置预置用户，预留后续用户表扩展位）
- 测试拆分：网关测认证/CSRF，业务服务测试直接设 `X-User-Id` 绕过网关
- 验证：双服务启动 + 端到端登录→发消息→SSE 链路通

### 阶段3 MyBatis XML（中等风险）
- 复杂 SQL（多行文本块、动态 `<script>`、多表 JOIN）转 XML 文件
- 配置 `mapper-locations: classpath*:mapper/**/*.xml`
- 按包渐进转换（简单→中等→复杂），每包转换后跑测试
- 移除接口 SQL 注解，保留 `@Mapper` 和 `@Param`
- 验证：`mvn test` 通过

### 阶段4 SQL 统一（中等风险）
- V1–V18 合并为单一 `V1__baseline.sql`（FK 顺序、审计字段口径、中文注释）
- Java 迁移 V8/V14（数据回填）处理：全新库场景删除，或保留为 baseline 之后的回填迁移
- 删除 V2–V18 SQL 文件
- 重建开发库 agent-squad-mysql，Flyway 应用合并后 baseline
- 验证：Flyway 校验 + 测试库重建 + `mvn test` + MySQL 端到端

## Capabilities

本变更为纯代码重构，不改变 spec 级行为：
- 认证行为已由 `access` spec（R01）定义，网关服务只是将其集中到独立服务实现
- 持久层、SQL 迁移、包结构是实现细节，不改变外部行为契约
- 因此设置 `skip_specs: true`，不创建 spec delta 文件

## Impact

**代码影响**：
- 新增：`../../../server/agent-squad-gateway` Maven 模块（约 6 个 Java 文件 + pom + 配置）
- 修改：11 个 controller（移除 AuthService 注入，改用 UserContext）
- 修改：WebConfig（注册拦截器/网关配置）
- 移动：AuthService/AuthController/AuthSession/AuthView/LoginRequest 从 identity → gateway
- 移动：约 90+ 文件分包调整（阶段1 + 阶段3）
- 删除：V2–V18 SQL 迁移文件（阶段4）

**部署影响**：
- 从单服务（18080）变为双服务（gateway 18081 + business 18080）
- 前端指向网关
- 数据库：合并迁移后需重建开发库

**测试影响**：
- 138 项测试：认证相关测试拆分（网关测认证，业务测设 X-User-Id）
- 阶段1/3/4 验证靠 `mvn test`
- 阶段2 需双服务端到端验证

**风险控制**：
- 每阶段独立提交，验证后推进
- 阶段2 网关/业务测试分层，避免全量测试耦合
- 阶段4 SQL 合并用 check_design.py + Flyway 校验 + 表清单 diff 三重验证
- 执行时拉新分支，不污染当前 main
