# http-response-format Specification

## Purpose
统一所有 HTTP 接口的响应结构，使前端能够以一致方式读取业务状态码、数据载荷与提示信息，并简化错误处理与日志记录。

## Requirements

### Requirement: 所有成功响应使用统一包装结构
系统 SHALL 对所有成功的 HTTP 业务响应使用统一包装结构，HTTP 状态码保持 200，包装结构包含业务码、数据载荷与提示信息。

#### Scenario: 正常返回业务对象
- **WHEN** 调用任意业务接口并成功处理
- **THEN** HTTP 状态码为 200，响应体包含 `code`（int）、`data`、`msg` 三个字段，`data` 为接口原本应返回的业务对象

#### Scenario: 正常返回列表
- **WHEN** 调用列表接口并成功处理
- **THEN** HTTP 状态码为 200，`data` 字段为列表或分页对象，结构与原先保持一致

### Requirement: 所有错误响应使用统一包装结构
系统 SHALL 对所有错误响应使用统一包装结构，保留原有 HTTP 状态码语义，响应体包含业务码与提示信息。

#### Scenario: 请求参数错误
- **WHEN** 调用接口并提交非法参数
- **THEN** HTTP 状态码为 400，响应体包含 `code`（int）、`data`（可为 null）、`msg` 字段

#### Scenario: 未认证访问
- **WHEN** 未携带有效凭证访问受保护接口
- **THEN** HTTP 状态码为 401，响应体包含 `code` 与 `msg`，提示需要登录

#### Scenario: 无权限访问
- **WHEN** 已登录用户访问无权限资源
- **THEN** HTTP 状态码为 403，响应体包含 `code` 与 `msg`

#### Scenario: 服务端异常
- **WHEN** 服务端发生未预期异常
- **THEN** HTTP 状态码为 500，响应体包含 `code` 与 `msg`，不暴露内部异常细节

### Requirement: 业务成功码保持唯一
系统 SHALL 定义统一的业务成功码，所有正常响应的 `code` 字段均使用该成功码 `0`；错误码与 HTTP 状态码一致并由 `ApiCode` 枚举集中定义。

#### Scenario: 成功创建会话
- **WHEN** 调用创建会话接口成功
- **THEN** 响应体 `code` 字段为约定的业务成功码 `0`

#### Scenario: 成功获取任务详情
- **WHEN** 调用任务详情接口成功
- **THEN** 响应体 `code` 字段与创建会话接口一致

### Requirement: 前端统一解析响应结构
系统 SHALL 在前端 API 层统一解析响应结构，根据 `code` 与 `msg` 进行错误提示，并将 `data` 返回给业务代码。

#### Scenario: 业务成功
- **WHEN** 前端请求返回且 `code` 为成功码
- **THEN** 前端将 `data` 交给调用方，不额外处理 `message`

#### Scenario: 业务失败
- **WHEN** 前端请求返回且 `code` 为非成功码
- **THEN** 前端使用 `msg` 进行错误提示，并拒绝本次调用
