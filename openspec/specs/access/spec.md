# 接入与身份（Access）

## Purpose

接入与身份能力负责登录、会话主体、资源归属校验与对话删除屏障，确保用户只能访问自己的数据，且服务不接受模型或客户端自报身份。（M01）

## Requirements

### Requirement: 用户数据访问隔离

系统 MUST 遵守以下约束：平台必须确保用户只能访问自己的会话、任务、记忆与产物；服务不接受模型自报身份，身份只能由服务端认证主体建立。（R01）

#### Scenario: 伪造身份拒绝
- **WHEN** 请求伪造用户标识或自定义 org/user/session 命名空间
- **THEN** 服务端以已认证主体解析，不采纳伪造值

#### Scenario: 跨用户串数据拒绝
- **WHEN** 两个用户使用相同显示标题，或伪造 conversationId/frameworkSessionId 访问
- **THEN** 查询按 userId 复合归属过滤，不存在与无权统一返回 NOT_FOUND

#### Scenario: 管理员不默认越权
- **WHEN** 管理员角色访问其他用户的资源内容
- **THEN** 管理员权限不隐式等于可读所有用户数据，敏感运维核对需单独授权并审计

### Requirement: 会话认证与 CSRF 防护

系统 MUST 遵守以下约束：登录产生 HttpOnly 会话与绑定 CSRF 令牌，写请求必须同时携带 Cookie 与 X-CSRF-Token；账号禁用或授权版本变化使旧会话失效。

#### Scenario: 缺会话或 CSRF 拒绝
- **WHEN** 写请求缺 Cookie 或 X-CSRF-Token
- **THEN** 返回 401

#### Scenario: 账号禁用或版本变化失效
- **WHEN** 账号被禁用或 authVersion 递增
- **THEN** 旧 Cookie 会话失效，需重新登录

### Requirement: 对话删除屏障

系统 MUST 遵守以下约束：删除对话校验 If-Match，事务内递增 generation、置 DELETING、撤销在途目标并写取消与清理命令；立即禁止读取与继续，仅停止与核对可执行。

#### Scenario: 删除与受理并发
- **WHEN** 删除对话与提交新目标并发
- **THEN** 锁定对话行校验 generation/status，仅一方成功，删除后不能创建新任务

#### Scenario: 逻辑删除不立即物理删除
- **WHEN** 对话进入 DELETING
- **THEN** 立即禁读与禁续，但逻辑删除不意味着文件与备份已物理删除，UNKNOWN 证据不立即清除
