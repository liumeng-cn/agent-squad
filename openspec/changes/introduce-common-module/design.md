# Design

## Context

当前 Maven 结构在 `server/pom.xml` 下只有两个子模块：`agent-squad-gateway` 与 `agent-squad-server`。两者都需要表达账号角色、账号状态等基础概念，且即将统一使用的 `ApiResult<T>` 响应包装类不应隶属于某个业务模块，否则会导致循环依赖或重复定义。参见 `proposal.md` 中的变更动机。

## Goals / Non-Goals

**Goals：**
- 提供一个无业务逻辑的轻量共享模块，承载跨模块类型与工具类。
- 将 `AccountRole`、`AccountStatus` 等跨模块枚举迁移到 common。
- 为 `unify-http-response-format` 提供 `ApiResult<T>` 载体。

**Non-Goals：**
- 不在 common 模块中引入 Spring Boot、MyBatis、AgentScope 等重型依赖。
- 不将仅在一个模块内使用的业务枚举（如 `GoalTaskStatus`）移入 common。
- common 模块不直接对外暴露 HTTP 接口或数据库访问。

## Decisions

### 模块命名为 `agent-squad-common`
- **理由**：与现有 `agent-squad-gateway`、`agent-squad-server` 命名风格一致，语义清晰。
- **替代方案**：`agent-squad-shared`。保留原命名，因为 `common` 在 Maven 多模块中更常见，且能明确表示“通用基础”。

### common 模块不依赖 Spring Boot 起步器
- **理由**：若引入 Spring Boot 起步器，会导致 gateway 与 server 被动引入重复或冲突的传递依赖。`ApiResult<T>` 仅使用基础 JDK/Lombok 即可完成。
- **替代方案**：引入 `spring-boot-starter` 以使用部分注解。不采纳，避免破坏模块边界。

### `ApiResult<T>` 放在 common，但响应包装逻辑放在各业务模块
- **理由**：`ResponseBodyAdvice` 与 `ExceptionHandler` 需要 Spring Web 上下文，common 不应绑定 Spring MVC。common 只提供数据载体。
- **替代方案**：在 common 中提供通用 Advice。不采纳，会导致 common 依赖 Spring Web 并侵入业务模块的异常处理顺序。

### 枚举序列化保持默认名称
- **理由**：Java 枚举默认 `toString()` 与 `name()` 均返回大写名称，与当前数据库存储值一致，无需自定义 `@JsonValue` 或 MyBatis 类型处理器。
- **替代方案**：使用 `@JsonValue` 显式标注。暂不采纳，保持简单；若后续需要大小写转换再补充。

## Risks / Trade-offs

- **[Risk]** 新增模块会增加 Maven 构建复杂度 → 通过父 POM 统一管理版本号与 Lombok 依赖来缓解。
- **[Risk]** 过度膨胀 common 模块 → 在 `AGENTS.md` 中明确 common 仅放跨模块类型与无状态工具，业务代码禁止迁入。
- **[Trade-off]** 枚举放在 common 后，原模块的 Mapper XML 中字面量仍为字符串。优点是不改变数据库；缺点是 XML 中仍存在魔法字符串，需结合 `refactor-magic-strings-to-enums` 中的规范与代码审查逐步收敛。

## Migration Plan

1. 在 `server/agent-squad-common` 创建模块目录与 `pom.xml`。
2. 在 `server/pom.xml` 的 `<modules>` 中加入 `agent-squad-common`。
3. `agent-squad-gateway` 与 `agent-squad-server` 的 `pom.xml` 添加对 `agent-squad-common` 的依赖。
4. 将 `AccountRole`、`AccountStatus`（若存在）与 `ApiResult<T>` 移入 common 对应包。
5. 批量修改引用类的 `import` 路径。
6. 运行 `mvn clean compile` 验证模块依赖与编译。
