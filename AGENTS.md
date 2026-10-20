# 新项目协作约定

- 当前阶段只做模块详细设计。不得因设计已经列出接口、DDL、实现步骤而自动开始编写应用、安装依赖或部署。
- 项目定位为多智能体运行技术底座；不得再次将云效、车辆、Skill 页面或普通聊天工作台作为架构中心。
- 设计细化到用例步骤、请求响应字段、数据结构、状态转换、事务与故障恢复；不能只列组件名。
- docs/overview.md 定义范围，docs/modules/ 定义行为，contracts/openapi.json 定义传输结构，data/schema.sql 定义拟议存储。变更时同步检查，不复制多套现行方案。
- 旧项目只读参考；没有兼容义务。不自动迁入其服务、迁移、界面、私有配置或未提交改动。
- Java 采用可读的 Controller → Service → Repository/Mapper 分层，DTO 与持久对象分离；功能类和关键方法写中文职责注释，四空格缩进，控制语句有花括号，不用通配符导入。
- 表与字段有中文注释，业务 ID 与存储主键分离，公共审计字段统一；不因当前为开发期就自动清理任何实际数据库。
- 仅在用户明确安排实现后进入应用编码；不自行启动 Java 或真实模型、发布操作。

- 整体技术基线以 docs/architecture.md 为准，目标源码与部署结构以 docs/project-structure.md 为准；目前均为待验证的设计，不提前创建应用。

- Java + AgentScope 路线以 decisions/002-agentscope-java.md 和 docs/research/agentscope-java.md 为依据；模块、OpenAPI、DDL 已按v0.2重映射，仍不得因设计文件齐全自动生成应用。保持 R01–R12，不混用稳定版与预发布 API。

- 运行权威、对象映射与故障窗口以 docs/runtime-contract.md 和 ADR-003 为准；设计检查覆盖不等于行为/生产验证。

- Web工作台是一期交付，路由/交互/API对应以docs/web-console.md和contracts/ui-manifest.json为准；不与暂缓的Skill生成页面混淆。开发顺序见docs/predevelopment.md。

## Spring代码约定（用户明确要求）

- Spring类不声明内部类，包括内部DTO、嵌套record、配置辅助类；拆成职责明确的独立文件。
- 不使用构造方法注入，也不通过Lombok的@RequiredArgsConstructor/@AllArgsConstructor隐式生成注入构造器。依赖统一采用jakarta.annotation.Resource字段注入；业务对象自身的正常构造不受这条注入规则限制。
- 功能类注释说明职责、所属模块与关键边界；核心方法注释说明业务步骤、事务/副作用、幂等及失败恢复要点。避免只翻译方法名，不为机械访问器堆注释。
- 关键日志说明操作、阶段、结果及原因，按需带traceId/taskId/jobId/operationId/commandId；异常保留必要堆栈，避免各层重复记录同一异常。
- 不输出密钥、Cookie、密码、完整Prompt/上下文、模型内部推理；入参和工具结果只记录允许字段、摘要及大小。正常等待/取消不是ERROR，恢复重试要有次数和原因，避免轮询/续租刷屏。
