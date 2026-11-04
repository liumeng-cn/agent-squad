# 新项目协作约定

- 本项目由用户个人独立研发。架构、代码、数据结构、开发环境和验证方式可按目标主动调整，不需为既有实现保留无价值的兼容层；重要变更仍应说明影响并验证。
- 必须保留现有远程 Git 关联；不得删除、覆盖或重置远程仓库配置。涉及远程地址、仓库迁移或解绑时，先由用户明确决定。
- 设计阶段已结束，用户授权按 D0–D6 分阶段实现；每个阶段验证后提交。尚未实现的能力如实标记，不以测试探针冒充正式运行链路。
- 项目定位为多智能体运行技术底座；不得再次将云效、车辆、Skill 页面或普通聊天工作台作为架构中心。
- 设计细化到用例步骤、请求响应字段、数据结构、状态转换、事务与故障恢复；不能只列组件名。
- openspec/specs/ 定义能力与行为（R01–R12），api/openapi.json 定义传输结构，server/agent-squad-server/src/main/resources/db/migration/ 定义实际存储。变更时同步检查，不复制多套现行方案。
- 旧项目只读参考；没有兼容义务。不自动迁入其服务、迁移、界面、私有配置或未提交改动。
- Java 采用可读的 Controller → Service → Repository/Mapper 分层，DTO 与持久对象分离；功能类和关键方法写中文职责注释，四空格缩进，控制语句有花括号，不用通配符导入。
- 表与字段有中文注释，业务 ID 与存储主键分离，公共审计字段统一；不因当前为开发期就自动清理任何实际数据库。本地开发库可直接修改表结构，修改后须同步 `schema.sql`；生产与共享数据库仍由用户独立管理。
- 本地实现与验证已获授权；真实模型、外部发布和生产系统操作仍按对应任务范围执行。

- 整体技术基线、目标源码与部署结构以 openspec/config.yaml 的 context 约束与 openspec/specs/ 为准；目前均为待验证的设计，不提前创建应用。

- Java + AgentScope 路线以 openspec/config.yaml 的 AgentScope 固定版本约束为依据；模块、OpenAPI、DDL 已按v0.2重映射，仍不得因设计文件齐全自动生成应用。保持 R01–R12，不混用稳定版与预发布 API。

- 运行权威、对象映射与故障窗口以 openspec/specs/runtime 与 openspec/config.yaml 的运行权威约束为准；设计检查覆盖不等于行为/生产验证。

- Web工作台是一期交付，路由/交互/API对应以 openspec/specs/web-console 和 api/ui-manifest.json 为准；不与暂缓的Skill生成页面混淆。开发顺序见 openspec/changes/（d3–d6）。
- 提交代码时，必须写简体中文的注释信息

## Spring代码约定（用户明确要求）

- Spring类不声明内部类，包括内部DTO、嵌套record、配置辅助类；拆成职责明确的独立文件。
- 不使用构造方法注入，也不通过Lombok的@RequiredArgsConstructor/@AllArgsConstructor隐式生成注入构造器。依赖统一采用jakarta.annotation.Resource字段注入；业务对象自身的正常构造不受这条注入规则限制。
- Java 模块统一引入 Lombok；需要日志的类使用 `@Slf4j` 和 `log`，不手写 `LoggerFactory` 字段。简单不可变请求、响应和持久投影继续使用 Java `record`，不为使用 Lombok 而改写数据语义。
- 功能类注释说明职责、所属模块与关键边界；核心方法注释说明业务步骤、事务/副作用、幂等及失败恢复要点。避免只翻译方法名，不为机械访问器堆注释。
- 关键日志说明操作、阶段、结果及原因，按需带traceId/taskId/jobId/operationId/commandId；异常保留必要堆栈，避免各层重复记录同一异常。
- 不输出密钥、Cookie、密码、完整Prompt/上下文、模型内部推理；入参和工具结果只记录允许字段、摘要及大小。正常等待/取消不是ERROR，恢复重试要有次数和原因，避免轮询/续租刷屏。

## 数据访问统一约定（用户明确要求）

- 项目生产代码的数据库读写统一使用 MyBatis Mapper 接口与 Mapper XML，业务服务、Repository 和配置校验不得直接使用 JDBC、JdbcTemplate、NamedParameterJdbcTemplate、DriverManager、Connection、Statement 或 ResultSet 执行 SQL。
- SQL 只放在对应模块 Mapper XML，Service 负责业务步骤和事务；不得同时保留 JDBC 与 MyBatis 两套现行实现。
- 官方 AgentScope 持久化只通过其公开 SDK 接口接入；向 SDK 提供 DataSource 不代表允许项目自行编写 JDBC SQL。测试建库、DDL执行及结构对比夹具与生产访问链路分开。
- 登录账号以 sq_user 为权威，密码只存 BCrypt 摘要；配置文件中的数据库连接账号不是网页登录账号。预置测试账号通过显式 SQL 或账号管理服务写入数据库，不在正常启动时反复覆盖。
- 数据结构变更先同步最终 V1__baseline.sql、Mapper XML 和验证。用户要求提供刷库 SQL 时，只生成、校验文件；未经用户明确要求，不执行真实库的删除、重建或清空。

## 枚举与有限值约定

- 业务上取值有限的字段（角色、状态、类型等）在 Java 后端使用枚举，在 TypeScript 前端使用字面量联合类型，不直接用魔法字符串比较或传递。
- 跨模块共享的枚举、异常、错误码统一放在 `agent-squad-common` 模块；仅模块内部使用的枚举可留在原模块。
- `api/openapi.json` 中对应字段使用 `enum` 约束，与 Java/TypeScript 枚举取值保持一致；新增取值时三端同步修改。

## HTTP 响应约定

- 所有业务 JSON 接口统一返回 `ApiResult<T>`（`code`、`data`、`msg`），成功码为 `0`，错误码取自 `ApiCode` 并沿用 HTTP 状态码。
- 控制器直接返回 `ApiResult.success(data)`，异常处理返回 `ApiResult.error(ApiCode..., msg)`；不使用 `ResponseBodyAdvice` 自动包装，避免遗漏与重复包装。
- 仅 SSE 事件流与文件下载保持原始结构；新增业务接口不得直接返回裸 DTO、临时 `Map` 或无内容响应。
- 前端统一通过 `shared/api/http.ts` 解包 `data`，按 `code` 判断成功与失败，业务代码不直接读取响应包装字段。
