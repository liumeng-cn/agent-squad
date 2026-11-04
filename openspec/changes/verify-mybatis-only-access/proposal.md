# Proposal

## Why

项目已约定生产代码统一使用 MyBatis Mapper/XML 访问数据库，避免 JDBC/JdbcTemplate 直接写 SQL 导致 SQL 分散、事务边界不一致。当前生产代码基本符合约定，但需要系统性验证并建立持续约束，防止后续回归；同时评估引入 MyBatis-Plus 是否能合理减少样板代码而不破坏现有分层。

## What Changes

- 扫描 `agent-squad-gateway` 与 `agent-squad-server` 生产源码，确认无 `JdbcTemplate`、`NamedParameterJdbcTemplate`、`DriverManager`、`java.sql.Connection`、`Statement`、`ResultSet` 等直接 JDBC 使用。
- 对生产源码新增静态检查（ArchUnit 或 AST 扫描测试），禁止业务 Service/Repository/Controller 引入 JDBC 相关类；测试夹具与 DDL 工具明确排除在外。
- 评估 MyBatis-Plus 在本项目的适用场景：若仅用于简化通用 CRUD 且不侵入 Service 层，则在必要模块引入；若与现有 MyBatis Mapper 风格冲突或收益有限，则记录评估结论暂不引入。
- 更新 `AGENTS.md` 约定，明确生产代码仅通过 MyBatis Mapper/XML 访问数据库，测试代码的 JDBC 使用范围仅限于测试夹具与结构校验。

## Capabilities

本变更为内部约定与质量门禁，不引入或修改外部可观测行为，因此设置 `skip_specs: true`。

## Impact

- 构建依赖：可能新增 ArchUnit 或 MyBatis-Plus 依赖。
- 测试结构：新增一个用于扫描生产包的静态检查测试。
- 开发规范：`AGENTS.md` 章节需要补充说明。
