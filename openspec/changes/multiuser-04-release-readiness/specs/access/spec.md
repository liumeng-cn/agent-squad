# Spec Delta: Access / R01

## ADDED Requirements

### Requirement: 正式部署认证安全门槛

生产环境 MUST 使用 HTTPS、可信网关与内部签名身份、受保护的持久会话、严格同源策略与受控密钥管理；生产不能启用开发预置单账号或未经验证的身份头透传。

#### Scenario: 生产误配置
- **WHEN** 生产配置缺少签名密钥、使用本地开发认证开关、允许不受限 CORS 或业务端口直接暴露外网
- **THEN** 部署门槛阻止发布，能在启动期检测的安全错误直接 fail-closed

#### Scenario: 账号隔离和日志隐私
- **WHEN** 用户在多端登录、管理员操作或普通 Agent 执行
- **THEN** 每个资源按认证主体授权，日志不含明文凭据、原始 Cookie/CSRF、完整用户 Prompt 或原始模型内部推理
