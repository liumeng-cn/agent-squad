# Proposal

## Why

当前 `agent-squad-gateway` 与 `agent-squad-server` 各自维护账号角色、状态等类型定义，且前后端接口统一响应格式 `ApiResult<T>` 需要被多个模块复用。通过抽取一个轻量 Maven `common` 模块，可以消除重复类型、避免循环依赖，为后续枚举化与响应格式统一提供共享载体。

## What Changes

- 新增 Maven 模块 `agent-squad-common`，作为 `agent-squad-gateway` 与 `agent-squad-server` 的公共依赖。
- 将跨模块使用的 `AccountRole`、`AccountStatus` 等枚举迁移到 `agent-squad-common`。
- 在 `agent-squad-common` 中提供统一响应包装类 `ApiResult<T>`，供 `unify-http-response-format` 变更复用。
- 调整 `server/pom.xml` 子模块列表与 `gateway`/`server` 模块的依赖关系。
- 保持各模块自身业务枚举（如 `GoalTaskStatus`、`WorkerDefinitionStatus`）仍留在原模块，不扩大 common 模块职责。

## Capabilities

本变更为纯基础设施重构，不引入或修改外部可观测行为，因此设置 `skip_specs: true`。

## Impact

- Maven 构建结构：新增子模块，gateway 与 server 均依赖 common。
- Java 导入路径：引用 `AccountRole`/`AccountStatus`/`ApiResult` 的类需要更新 import。
- OpenAPI 生成：共享类型在 openapi.json 中可能以统一 schema 引用形式出现。
