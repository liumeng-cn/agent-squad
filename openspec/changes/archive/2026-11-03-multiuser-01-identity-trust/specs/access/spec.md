# Spec Delta: Access / R01

## MODIFIED Requirements

### Requirement: 会话认证与 CSRF 防护

系统 MUST 对真实持久账号进行密码哈希验证、登录限流和会话生命周期管理；登录产生 HttpOnly 会话与绑定 CSRF 令牌，写请求必须同时携带 Cookie 与 X-CSRF-Token；账号禁用、密码重置、授权版本变化使旧会话失效，令牌原文不得持久存储。

#### Scenario: 缺会话或 CSRF 拒绝
- **WHEN** 写请求缺 Cookie、缺 CSRF、CSRF 不匹配或 Session 失效
- **THEN** 返回 401；不改变任何业务状态

#### Scenario: 账号禁用或版本变化失效
- **WHEN** 用户被禁用、修改密码、管理员重置密码或角色调整
- **THEN** 旧 Cookie 会话在任一网关实例均失效，新的 Agent 模型/工具派发被拒绝；未知外部写入仍须核对

#### Scenario: 密码与令牌存储
- **WHEN** 登录、持久化会话或审计认证事件
- **THEN** 仅保存带盐密码哈希及随机 Session/CSRF 的摘要；不记录明文密码、原始 Token、完整认证头

## ADDED Requirements

### Requirement: 多用户账号生命周期（R01）

系统 MUST 支持管理员创建和管理多个独立账号，稳定 userId 不得由浏览器或模型自报。默认邀请制；普通用户仅能修改自己的密码，管理员不能隐式访问他人的个人对话。

#### Scenario: 两个用户隔离
- **WHEN** 用户 A 和 B 以不同账号登录，尝试访问彼此的会话、任务、文件、记忆和 Skill
- **THEN** 所有个人数据接口按登录 userId 限定归属，跨用户读取统一 NOT_FOUND，越权写入拒绝且不产生副作用

#### Scenario: 初次改密
- **WHEN** 新用户使用临时密码首次登录
- **THEN** 仅允许修改密码/登出等受限操作；改密后旧会话失效并需重新登录

#### Scenario: 防止失去全部管理员
- **WHEN** 试图禁用最后一个活动管理员
- **THEN** 事务内拒绝，不因并发修改失去全部管理员

### Requirement: 网关到业务服务的可信身份（R01）

系统 MUST 从网关签名的短时内部身份断言建立服务端身份；业务端直接拒绝无签名、过期、篡改、错误受众/路径/方法的请求；内部服务不得直接使用未验证的 X-User-Id 作为主体。

#### Scenario: 冒充身份
- **WHEN** 客户端伪造 X-User-Id/X-User-Role，或直接访问业务服务不携带有效内部断言
- **THEN** 返回 401，不产生数据访问/修改

#### Scenario: 断言校验与跨实例
- **WHEN** Gateway A 或 B 为同一有效登录 Session 转发业务请求
- **THEN** 业务服务以同一用户身份授权，且账号停用后两网关与业务执行安全点均禁止新的受控行为
