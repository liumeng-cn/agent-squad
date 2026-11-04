# Tasks

## 1. 扫描生产代码中的 JDBC 使用

- [ ] 1.1 使用 `grep` 扫描 `server/agent-squad-server/src/main/java` 与 `server/agent-squad-gateway/src/main/java`，查找 `JdbcTemplate`、`NamedParameterJdbcTemplate`、`DriverManager`、`java.sql.Connection`、`java.sql.Statement`、`java.sql.ResultSet` 等关键字，输出扫描报告。
- [ ] 1.2 对扫描结果逐条确认：若为生产代码直接调用，则改为 MyBatis Mapper/XML；若为 import 但未实际使用，则清理 import。
- [ ] 1.3 确认 `spring-boot-starter-jdbc` 在 gateway 中是否仅被测试或配置使用；必要时保留，但禁止业务代码使用。

## 2. 增加静态门禁测试

- [ ] 2.1 在 `agent-squad-server` 的测试依赖中加入 `archunit-junit5`。
- [ ] 2.2 在 `agent-squad-gateway` 的测试依赖中加入 `archunit-junit5`。
- [ ] 2.3 编写 `MyBatisOnlyAccessTest`，断言 `com.agentsquad.squad..` 生产包不得依赖 `java.sql.Connection`、`java.sql.Statement`、`java.sql.ResultSet`、`org.springframework.jdbc.core.JdbcTemplate`、`org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate`。
- [ ] 2.4 允许 `javax.sql.DataSource`、MyBatis 类型处理器、`org.apache.ibatis..` 以及 AgentScope SDK 公开接口的依赖。
- [ ] 2.5 运行新增测试，确认当前生产代码通过门禁。

## 3. 评估 MyBatis-Plus

- [ ] 3.1 统计当前项目中样板 CRUD Mapper 的数量与复杂度。
- [ ] 3.2 评估引入 MyBatis-Plus 对现有复杂 SQL、Service 分层、事务控制的影响，形成书面结论。
- [ ] 3.3 若结论为暂不引入，在 `design.md` 与 `AGENTS.md` 中记录原因；若结论为引入，则创建后续变更任务并在本变更中不实施。

## 4. 规范补充与验收

- [ ] 4.1 在 `AGENTS.md` 约定中补充：生产代码仅通过 MyBatis Mapper/XML 访问数据库；测试夹具、DDL 执行与结构对比工具可例外使用 JDBC。
- [ ] 4.2 运行 `mvn -pl server/agent-squad-server,server/agent-squad-gateway test` 验证新增 ArchUnit 测试与既有测试均通过。
- [ ] 4.3 运行 `mvn -pl server verify`（或等效命令）验证无回归。

## Workflow follow-up

- 全部任务完成后提交代码并归档本变更。
