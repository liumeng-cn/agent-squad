# Tasks

## 1. 创建 Maven common 模块

- [x] 1.1 在 `server/agent-squad-common` 创建 `pom.xml`，继承 `agent-squad` 父模块，仅依赖 Lombok（provided），不引入 Spring Boot/MyBatis/AgentScope。
- [x] 1.2 在 `server/pom.xml` 的 `<modules>` 中加入 `agent-squad-common`，并确保父 POM 的 `lombok.version` 对 common 生效。
- [x] 1.3 在 `agent-squad-gateway` 与 `agent-squad-server` 的 `pom.xml` 中添加对 `agent-squad-common` 的依赖。
- [x] 1.4 运行 `mvn -pl server/agent-squad-common compile` 验证新模块能独立编译。

## 2. 迁移跨模块枚举到 common

- [x] 2.1 在 `agent-squad-common` 中创建 `com.agentsquad.squad.common.identity.AccountRole` 枚举，取值 `ADMIN`、`USER`。
- [x] 2.2 在 `agent-squad-common` 中创建 `com.agentsquad.squad.common.identity.AccountStatus` 枚举，取值 `ACTIVE`、`DISABLED`。
- [x] 2.3 修改 `agent-squad-gateway` 中引用角色/状态的类（如 `AdminUserView`、`AdminUserCreateRequest`、`AdminUserUpdateRequest`、`UserAccountRow`），改为引用 common 枚举。
- [x] 2.4 修改 `agent-squad-server` 中引用角色/状态的类（如相关 DTO/视图），改为引用 common 枚举（当前 server 无相关 DTO，已确认无需修改）。
- [x] 2.5 运行 `mvn -pl server/agent-squad-gateway,server/agent-squad-server,server/agent-squad-common compile` 验证跨模块编译通过。

## 3. 提供统一响应包装类

- [x] 3.1 在 `agent-squad-common` 中创建 `com.agentsquad.squad.common.web.ApiResult<T>`，包含 `code`、`data`、`message` 字段，并提供 `success(T data)` 与 `error(String code, String message)` 工厂方法。
- [x] 3.2 编写单元测试验证 `ApiResult` 的序列化结果包含 `code`、`data`、`message`。
- [x] 3.3 运行 `mvn -pl server/agent-squad-common test` 验证测试通过。

## Workflow follow-up

- 本变更完成后，`refactor-magic-strings-to-enums` 与 `unify-http-response-format` 可引用 common 模块中的类型。
- 全部任务完成后提交代码并归档本变更。
