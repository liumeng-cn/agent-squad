# api-contract-enums Specification

## Purpose
约束 HTTP 接口中有限取值字段必须通过后端枚举进行序列化与反序列化，防止非法值进入业务层，并为前端提供可枚举的契约。

## Requirements

### Requirement: 账号角色字段使用枚举
系统 SHALL 将账号角色字段定义为枚举类型，仅允许 `ADMIN` 与 `USER` 两个取值。

#### Scenario: 创建管理员账号
- **WHEN** 调用管理员创建接口并传入角色 `ADMIN`
- **THEN** 接口成功创建角色为 `ADMIN` 的账号

#### Scenario: 传入非法角色
- **WHEN** 调用管理员创建接口并传入角色 `SUPER`
- **THEN** 接口返回 400，提示请求参数错误

### Requirement: 账号状态字段使用枚举
系统 SHALL 将账号状态字段定义为枚举类型，仅允许 `ACTIVE` 与 `DISABLED` 两个取值。

#### Scenario: 禁用账号
- **WHEN** 调用账号更新接口并将状态设为 `DISABLED`
- **THEN** 账号状态被更新为 `DISABLED`，且该账号无法登录

#### Scenario: 传入非法状态
- **WHEN** 调用账号更新接口并传入状态 `LOCKED`
- **THEN** 接口返回 400，提示请求参数错误

### Requirement: Worker 定义状态字段使用枚举
系统 SHALL 将 Worker 定义状态字段定义为枚举类型，仅允许 `ENABLED`、`DISABLED` 与 `REVOKED` 三个取值。

#### Scenario: 查询可用 Worker
- **WHEN** 调用 Worker 列表接口
- **THEN** 返回结果中每个 Worker 的 `status` 字段只可能是 `ENABLED`、`DISABLED` 或 `REVOKED`

#### Scenario: 注册 Worker 时传入非法状态
- **WHEN** 调用 Worker 注册接口并传入状态 `DRAFT`
- **THEN** 接口返回 400，提示请求参数错误

### Requirement: 枚举序列化保持字符串形式
系统 SHALL 在 JSON 序列化与数据库持久化时保持枚举的字符串名称，不改变现有存储值大小写。

#### Scenario: 序列化响应
- **WHEN** 接口返回包含角色或状态的响应体
- **THEN** JSON 中对应字段值为大写字符串，如 `"ADMIN"`、`"ACTIVE"`、`"ENABLED"`

#### Scenario: 持久化到数据库
- **WHEN** 系统将枚举值写入数据库
- **THEN** 数据库中存储的仍是原字符串值，无需数据迁移
