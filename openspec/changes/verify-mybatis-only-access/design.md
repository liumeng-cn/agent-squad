# Design

## Context

项目已约定生产代码仅通过 MyBatis Mapper/XML 访问数据库。此前扫描发现，JDBC 相关类仅在测试夹具（如 `MySqlTestDatabase`、`GatewayTestDatabase`、`SchemaParityTest`）中使用，生产源码未出现直接 JDBC 调用。但需要建立持续约束，防止后续回归，并评估 MyBatis-Plus 的引入价值。参见 `proposal.md`。

## Goals / Non-Goals

**Goals：**
- 确认并验证生产代码无直接 JDBC 使用。
- 在构建阶段增加静态检查，禁止生产包引入 JDBC 核心类。
- 评估 MyBatis-Plus 是否适合当前项目，并给出结论。
- 更新 `AGENTS.md` 约定。

**Non-Goals：**
- 不改造测试夹具中的 JDBC 使用（测试建库、DDL 校验属于合法场景）。
- 不强制要求所有模块立刻引入 MyBatis-Plus。
- 不修改现有 MyBatis Mapper 的 SQL 风格。

## Decisions

### 使用 ArchUnit 扫描生产包禁止 JDBC 类
- **理由**：ArchUnit 能精确按包名断言，失败信息清晰，可集成到 `mvn test`。
- **替代方案**：使用 Checkstyle 或 SpotBugs。Checkstyle 对类引用检查较弱，SpotBugs 规则编写复杂；ArchUnit 最贴合“某包不能依赖某类”的需求。

### 扫描范围限定 `src/main/java` 下的业务包
- **理由**：排除测试代码与第三方 SDK 内部实现，避免误报。
- **替代方案**：扫描整个 classpath。不采纳，AgentScope SDK 内部可能使用 JDBC，误报不可控。

### 暂不引入 MyBatis-Plus
- **理由**：当前项目已有完整的 Mapper/XML 模式，且 Service 层职责清晰。引入 MyBatis-Plus 的收益主要是减少简单 CRUD 的样板代码，但当前项目复杂 SQL 较多，Plus 的 Lambda 查询与代码生成收益有限；同时会增加学习成本与依赖风险。
- **替代方案**：引入 MyBatis-Plus 并替换部分通用 CRUD。作为可选后续评估，本次先记录结论，不强制引入。

### 在 AGENTS.md 中明确测试代码例外
- **理由**：现有测试夹具需要 JDBC 执行 DDL 与结构校验，必须在规范中明确区分。
- **替代方案**：要求测试也全部改为 MyBatis。不采纳，结构校验与测试建库不适合用 Mapper 表达。

## Risks / Trade-offs

- **[Risk]** ArchUnit 规则编写过宽导致正常类被误报 → 通过白名单允许 `javax.sql.DataSource` 等配置类，仅禁止 `java.sql.*`、`JdbcTemplate` 等执行类。
- **[Risk]** 第三方库通过反射使用 JDBC，ArchUnit 无法检测 → 作为持续约束，定期人工 review 仍不可替代。
- **[Trade-off]** 不引入 MyBatis-Plus 意味着样板代码继续存在。当前项目复杂查询占主导，此 trade-off 可接受。

## Migration Plan

1. 在 `agent-squad-server` 与 `agent-squad-gateway` 的测试依赖中加入 ArchUnit。
2. 编写 `MyBatisOnlyAccessTest`，断言生产包（`com.agentsquad.squad..`）不依赖 `java.sql.Connection`、`java.sql.Statement`、`java.sql.ResultSet`、`org.springframework.jdbc.core.JdbcTemplate`、`org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate`。
3. 允许 `javax.sql.DataSource`、MyBatis 类型处理器及 SDK 公开接口的依赖。
4. 运行测试确认当前代码通过。
5. 在 `AGENTS.md` 中补充约定条目，明确生产代码仅通过 MyBatis Mapper/XML，测试夹具可例外。
6. 在 `verify-mybatis-only-access` 的任务中记录 MyBatis-Plus 评估结论。
