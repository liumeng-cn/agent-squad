# 新项目协作约定

- 本项目由用户个人独立研发。架构、代码、数据结构、开发环境和验证方式可按目标主动调整，不需为既有实现保留无价值的兼容层；重要变更仍应说明影响并验证。
- 必须保留现有远程 Git 关联；不得删除、覆盖或重置远程仓库配置。涉及远程地址、仓库迁移或解绑时，先由用户明确决定。
- 项目定位为多智能体运行技术底座；
- 设计细化到用例步骤、请求响应字段、数据结构、状态转换、事务与故障恢复；不能只列组件名。
- openspec/specs，api/openapi.json 定义传输结构
- Java 采用可读的 Controller → Service → Mapper 的 MVC 贫血模式，Service 负责业务规则和事务，Mapper 负责持久化；功能类和关键方法写中文职责注释，四空格缩进，控制语句有花括号，不用通配符导入。
- 表与字段有中文注释，业务 ID 与存储主键分离，公共审计字段统一；不因当前为开发期就自动清理任何实际数据库。本地开发库可直接修改表结构，修改后须同步 `schema.sql`；生产与共享数据库仍由用户独立管理。
- 本地实现与验证已获授权；真实模型、外部发布和生产系统操作仍按对应任务范围执行。
- 整体技术基线、目标源码与部署结构以 openspec/config.yaml 的 context 约束与 openspec/specs/ 为准；目前均为待验证的设计，不提前创建应用。
- Java + AgentScope 路线以 openspec/config.yaml 的 AgentScope 固定版本约束为依据；
- 提交代码时，必须写简体中文的注释信息，类标题不使用句号

## Spring代码约定（用户明确要求）

- Spring类不声明内部类，包括内部DTO、嵌套record、配置辅助类；拆成职责明确的独立文件。
- 不使用构造方法注入，也不通过Lombok的@RequiredArgsConstructor/@AllArgsConstructor隐式生成注入构造器。依赖统一采用jakarta.annotation.Resource字段注入；业务对象自身的正常构造不受这条注入规则限制。
- Java 模块统一引入 Lombok；需要日志的类使用 `@Slf4j` 和 `log`，不手写 `LoggerFactory` 字段。HTTP 请求和响应模型统一使用带中文注释的 Java class。
- 功能类注释说明职责、所属模块与关键边界；核心方法注释说明业务步骤、事务/副作用、幂等及失败恢复要点。避免只翻译方法名，不为机械访问器堆注释，也别写太抽象，简单易懂。
- 关键日志说明操作、阶段、结果及原因，按需带traceId/taskId/jobId/operationId/commandId；异常保留必要堆栈，避免各层重复记录同一异常。
- 不输出密钥、Cookie、密码、完整Prompt/上下文、模型内部推理；入参和工具结果只记录允许字段、摘要及大小。正常等待/取消不是ERROR，恢复重试要有次数和原因，避免轮询/续租刷屏。
- 方法参数超过5个时，使用对象传递

## 统一约定（用户明确要求）

- 项目生产代码通过 Mapper 访问数据库。简单单表 CRUD 使用 MyBatis-Plus BaseMapper，自定义 SQL 写在对应 Mapper XML。加锁、条件状态迁移、联表和批量操作保留显式 SQL。
- 官方 AgentScope 持久化只通过其公开 SDK 接口接入；向 SDK 提供 DataSource 不代表允许项目自行编写 JDBC SQL。测试建库、DDL执行及结构对比夹具与生产访问链路分开。
- 登录账号以 sq_user 为权威，密码只存 BCrypt 摘要；配置文件中的数据库连接账号不是网页登录账号。预置测试账号通过显式 SQL 或账号管理服务写入数据库，不在正常启动时反复覆盖。
- 数据结构变更先同步最终 V1__baseline.sql、Mapper XML 和验证。用户要求提供刷库 SQL 时，只生成、校验文件；未经用户明确要求，不执行真实库的删除、重建或清空。

## 模型与包结构

- Java 源码统一以 `com.agentsquad.squad` 为根，按技术职责使用直属包。仅在 Agent 工具、MCP 等确有子职责时继续分包。

- 表映射放在 `po` 包并以 PO 结尾。HTTP 入参放在 `dto` 包并以 DTO 结尾，返参放在 `vo` 包并以 VO 结尾。业务内部传递模型放在 `bo` 包并以 BO 结尾。模型用中文注释说明含义和使用边界。
- 固定结构的业务数据使用明确的模型。动态工具参数、扩展配置和协议载荷等开放结构可在边界使用 Map 或 JSON。
- 职责暂未确认的类统一放在 `pending` 包，明确用途后迁入对应职责包。
- PO、BO、DTO、VO 之间需要独立转换时，使用 `converter` 包下的 MapStruct 转换器。业务判断和外部调用由 Service 处理。
- 工具类、枚举、异常、配置和配置属性分别放在 `utils`、`enums`、`exceptions`、`config`、`properties`。观察器和转换器分别放在所属模块的 `observer` 和 `converter`。
- 独立定时任务入口放在 `scheduler`，后台轮询入口放在 `poller`，启动任务放在 `runner`。独立的定时触发入口类统一以 `Task` 结尾。 调度配置统一使用 `agentsquad.scheduler` 前缀，配置属性集中维护在 `config.properties.TaskScheduleProperties`，配置属性类统一放在 `config.properties`。业务操作和事务留在对应 Service，组件内部的连接重试和订阅生命周期由所属组件维护。
- 配置映射类以 `Properties` 结尾，放在所属应用的 `config.properties`，为每个字段写中文备注。使用 Spring Boot 配置绑定和自动注册，避免按 Bean 名引用。应用私有配置由所属应用维护，`common` 用于跨项目共用的类型和工具。仅对运行中可安全重新读取的配置启用刷新。
- Agent 定义、工具、MCP 和 SDK 扩展归入 `agent` 及其子包。业务流程在所属 Service 中调用 AgentScope 公开 API，SDK 回调和协议转换按职责归属，不设置通用 AgentScope 集成层。

## 枚举与有限值约定

- 业务上取值有限的字段（角色、状态、类型等）在 Java 后端使用枚举，在 TypeScript 前端使用字面量联合类型，不直接用魔法字符串比较或传递。
- 跨模块共享的枚举、异常、错误码统一放在 `agent-squad-common` 模块；仅模块内部使用的枚举可留在原模块。
- `api/openapi.json` 中对应字段使用 `enum` 约束，与 Java/TypeScript 枚举取值保持一致；新增取值时三端同步修改。

## HTTP 响应约定

- 所有业务 JSON 接口统一返回 `ApiResult<T>`（`code`、`data`、`msg`），成功码为 `0`，业务拒绝统一使用 HTTP 200，错误码取自 `ApiCode`，前端通过 `code` 判断业务结果；HTTP 错误状态用于协议或服务故障。
- 控制器直接返回 `ApiResult.success(data)`，异常处理返回 `ApiResult.error(ApiCode..., msg)`；不使用 `ResponseBodyAdvice` 自动包装，避免遗漏与重复包装。
- 仅 SSE 事件流与文件下载保持原始结构；新增业务接口不得直接返回裸 DTO、临时 `Map` 或无内容响应。
- 前端统一通过 `shared/api/http.ts` 解包 `data`，按 `code` 判断成功与失败，业务代码不直接读取响应包装字段。
