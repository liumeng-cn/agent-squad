# Design: 四维代码质量重构

## Context

当前 agent-squad 已完成 D0–D6 全阶段本地验收（138 项测试 0 失败），为 Java 21 + Spring Boot + MyBatis + MySQL + AgentScope Harness 的模块化单体。本次重构基于最新代码（d4-d5-continue 分支），动机见 proposal.md - Why。

**当前架构约束**（来自代码与 config.yaml）：
- 认证：`identity.AuthService` 内存会话（ConcurrentHashMap），token 制，CSRF 明文等值比较；散布在 11 个 controller、约 40+ 方法
- 持久层：33 个 Mapper 接口全部注解驱动（@Select 183 / @Insert 42 / @Update 112 / @Delete 0），0 个 XML；复杂 SQL 集中在约 20 个 mapper（JOIN/FOR UPDATE/CASE WHEN/子查询/`<script>` 动态）
- SQL 迁移：Flyway V1–V18（V1 baseline 仅 15 张表，不含 V2-V18 内容）；V8/V14 是 Java 数据回填迁移
- 包结构：`identity/controlle` 拼写错误、artifact/governance/worker 顶层散落、conversation 巨型包（82 文件）
- 单 Spring Boot 应用（server/pom.xml），单 MySQL（agent-squad-mysql 容器，127.0.0.1:13306）

**运行权威约束**：GoalTask/WorkerJob/Command 持久化；平台事务与 SDK journal 不原子；业务 ID CHAR(36) 与存储主键 BIGINT 分离；统一审计字段。

## Goals / Non-Goals

**Goals:**
- 认证收敛到独立网关服务，业务服务不再碰认证
- 持久层复杂 SQL 转 XML，提升可读性与可维护性
- SQL 迁移合并为单一 baseline，消除分散
- 包结构按业务能力统一分层，修正拼写与散落
- 不改变外部行为契约（API、认证语义、数据约束不变）

**Non-Goals:**
- 不拆散业务逻辑到微服务（仅抽出认证网关，其余保持单体）
- 不引入用户表与用户注册（网关暂用预置用户，预留扩展位）
- 不改 AgentScope SDK 嵌入方式与 Master/Worker 执行链路
- 不做真实模型/生产系统联调（仍用 loopback 桩）

## Decisions

### 决策1：使用 Spring Cloud Gateway 内置路由转发

**选择**：`server/agent-squad-gateway` 为独立 Spring Cloud Gateway Server WebFlux 应用，端口 18081。使用框架路由将 `/api/**`（排除 `/api/v1/auth/**`）转发到业务服务 18080；不编写代理 Controller、传输 Service 或自建 WebClient 转发循环。业务服务继续使用 Spring MVC。

**认证边界**：登录、当前会话和注销由网关本地认证接口处理，保持原 Cookie、CSRF 和内存会话语义。自定义 GlobalFilter 仅校验会话及写请求 CSRF，清除客户端传入的身份头，并注入可信 `X-User-Id`、`X-User-Name`、`X-User-Role` 后交给框架过滤器链。业务服务的 UserContextInterceptor 从可信网关请求建立身份上下文；业务端口仅供内部访问。

**理由**：路由、HTTP 传输、连接生命周期和流式转发属于网关框架职责。用户明确要求使用网关自带能力，移除手写代理。

### 决策2：框架承担流式转发，真实 HTTP 验证边界

**选择**：通过 Gateway Server WebFlux 的路由链转发原始请求及响应，业务层不手动聚合响应内容。网关独立使用与 Spring Boot 3.5 兼容的 Spring Cloud 4.3 系列组件；不引入 MVC starter。

**验证**：真实 HTTP 测试覆盖登录/me/注销、401、CSRF 拒绝、伪造身份头覆盖、请求体与上游状态/响应头保留、multipart 原始字节和 SSE 首帧。SSE 上游由屏障保持未完成，首帧必须在结束前到达，避免完整缓冲被普通请求测试掩盖。随后执行真实网关 + 业务服务 + 隔离 MySQL 的消息受理和事件读取。

### 决策3：主子 pom + 两个应用

**选择**：server/pom.xml 为主 pom（packaging=pom，modules=[agent-squad-gateway, agent-squad-server]），统一依赖版本管理；gateway 和 server 各自独立 pom 与 Spring Boot 启动类。

**理由**：用户要求"一个 pom 管理多个项目"；多模块结构使两个服务独立打包部署，主 pom 统一 BOM。

**替代方案**：同一 pom 多启动类（不拆模块）。未采用——无法独立打包部署，违背"独立服务"目标。

### 决策4：网关无 DB，认证用内存会话 + 配置预置用户

**选择**：网关不引入数据库，认证沿用内存会话（ConcurrentHashMap）+ 配置预置用户（`agentsquad.auth.*`）。AuthService/AuthController/AuthSession/AuthView/LoginRequest 迁到 gateway。预留后续用户表扩展位（接口设计不阻塞）。

**理由**：用户明确"网关不用新表""先用预置用户"；当前开发期单账号，引入用户表会改变认证模型并影响全部测试。内存会话保留现有认证语义，零行为变更。

**替代方案**：网关引入 `sq_user` 表 + 用户注册。未采用——超出本次范围，且与"不引新表"矛盾。

### 决策5：MyBatis XML 按 mapper 整体渐进迁移

**选择**：按 mapper 整体迁移（一个 mapper 要么全注解、要么全 XML），按复杂度从低到高推进：tools → sandbox → worker → artifact → governance → memory → interaction → presentation → runtime → conversation（最后，最复杂）。配置 `mybatis.mapper-locations: classpath*:/mapper/**/*.xml`。

**理由**（MyBatis 3.5.17 源码验证）：同一 statement id（namespace.methodName）不能同时存在于 XML 和注解上，否则启动时 `StrictMap.put` 抛 `IllegalArgumentException`。按 mapper 整体迁移避免同接口两种风格。渐进迁移每批 diff 小、可独立 review、失败定位容易。接口方法签名不变，service 层零改动。

**替代方案**：一次性全转。未采用——337 条 SQL 全改 diff 巨大，回滚困难。

### 决策6：SQL 合并保留 Java 迁移 V8/V14 为 baseline 之后的回填迁移

**选择**：V1–V18 结构变更合并为单一 `V1__baseline.sql`；V8/V14 是数据回填迁移（无 schema 变更），保留为 baseline 之后的 Java 迁移（版本号调整到 baseline 之后），全新库空跑，已有库保留回填能力。

**理由**：V8/V14 含跨 8 张表的读取-投影-写入编排、稳定 UUID 生成、JSON 解析、幂等去重，纯 SQL 无法表达；保留 Java 迁移对已有开发库有意义，新库空跑无性能问题。

**替代方案**：删除 V8/V14（仅面向全新库）。未采用——会丢失对已有开发库的回填能力，且 AGENTS.md 要求不自动清理实际数据库。

### 决策7：分包轻活先行，低风险快赢

**选择**：阶段1 先做拼写修正 + 顶层散落归位 + 空目录清理（约 10 文件），验证后推进。conversation 巨型包拆分（82 文件）放在后续阶段或单独处理。

**理由**：低风险机械改动快速收敛可读性问题，且不阻塞后续阶段。conversation 拆分风险高，单独评估。

**替代方案**：一次性全量分包。未采用——82 文件移动 diff 巨大，回归风险高。

## Risks / Trade-offs

- [网关路由破坏 SSE 流式] → Gateway 内置路由保持流式；网关/业务测试分层验证 SSE 链路
- [网关与业务服务部署复杂性] → 本地 dev 双服务启动；README 更新启动步骤
- [11 个 controller 改造遗漏] → 逐 controller grep `@CookieValue.*SQUAD_SESSION` 确保全部替换；mvn test 覆盖
- [MyBatis XML 迁移遗漏语句] → 每批迁移后 grep 接口无残留 `@Select/@Insert/@Update`；启动期 "Invalid bound statement" 暴露遗漏
- [SQL 合并 FK 顺序错误] → 合并清单 diff 校验；check_design.py + Flyway 校验 + 表清单 diff 三重验证
- [SQL 合并校验和漂移] → 开发库 drop 重建；测试库每次全新 schema
- [分包改动影响 import] → 机械改动，mvn 编译暴露缺失 import；Spring 组件扫描基于根包自动覆盖
- [认证行为变更破坏测试] → 网关保持 Cookie 名 `SQUAD_SESSION`、CSRF header `X-CSRF-Token`、401 语义不变；HTTP 测试兼容
- [用户量增长后网关内存会话不扩展] → 预留用户表扩展位，阶段二按需引入

## Migration Plan

**部署步骤**：
1. 阶段1-4 在 `refactor-code-quality` 分支完成，每阶段独立提交
2. 阶段2 完成后，本地 dev 启动双服务：gateway(18081) + business(18080)，前端 VITE_API_TARGET 指向网关
3. 阶段4 完成后，重建开发库 agent-squad-mysql，Flyway 应用合并后 baseline
4. 全阶段验证通过后合并到主分支

**回滚策略**：
- 每阶段独立提交，可按阶段回滚
- 阶段2 网关：回滚到单服务部署（删 gateway 模块，恢复 AuthService 在业务服务）
- 阶段4 SQL：合并前备份 V2-V18 文件，回滚时恢复
- 阶段3 XML：保留注解版本（git 历史），回滚时恢复注解

## Open Questions

- conversation 巨型包（82 文件）是否在本次拆分？建议单独评估，不阻塞本次重构
- 用户表引入的具体时机？阶段二按需，接口设计预留扩展位
