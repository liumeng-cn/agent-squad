# Spec Delta: Web Console / R10

## MODIFIED Requirements

### Requirement: 信息架构与权限

页面 MUST 以对话为默认入口，包含登录、对话、技能、连接设置、记忆设置、账号管理与管理员能力设置；首次登录使用临时密码时必须先改密。普通用户完成回复、工作控制、追问、审批、核对与下载不依赖任务详情路由。角色只控制导航与体验，服务端授权决定访问权。路由模板：`/login`、`/app/conversations/:conversationId?`、`/app/tasks/:taskId`（定位所属对话）、`/app/skills`、`/app/settings/connections`、`/app/settings/memory`、`/app/admin/users`、`/app/admin/workers`。

#### Scenario: 未登录转登录（B01）
- **WHEN** 进入页面先取 currentUser，未登录或登录失效
- **THEN** 转登录并返回原地址；旧内容和订阅清除，不反复创建新会话

#### Scenario: 管理员不默认越权（B14）
- **WHEN** 普通用户直达管理员能力设置页或越权调用其接口
- **THEN** 页面无操作入口，直接请求仍被后端拒绝；管理员也不隐式获得读取其他用户内容权限

#### Scenario: 首次登录强制改密（B16）
- **WHEN** 用户以临时密码登录且 `mustChangePassword` 为真
- **THEN** 仅允许改密、查看当前主体与登出；改密成功后旧会话失效并要求重新登录
