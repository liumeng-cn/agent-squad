# Spec Delta

## Purpose
为新的用户服务提供独立手机号注册及资料管理，并由网关统一执行JWT认证、Redis会话撤销和可信身份转发，支持多个服务实例共享用户身份及保持资源归属隔离。

## ADDED Requirements

### Requirement: 用户注册与资料
系统 SHALL 将注册匿名转发 user-server，以固定验证码8888校验手机号并生成唯一业务ID和用户名，昵称为空展示用户名。
#### Scenario: 并发注册
- **WHEN** 同一手机号并发注册
- **THEN** 只创建一个用户，其他返回重复业务拒绝
#### Scenario: 昵称清空
- **WHEN** 当前用户按正确版本清空昵称
- **THEN** 展示名称回退用户名，旧版本更新拒绝

### Requirement: 分布式认证
Gateway SHALL 验证RS256 JWT、Redis会话、当前账号状态及认证版本，并对写请求验证CSRF。
#### Scenario: 跨实例退出
- **WHEN** 在实例A登录并在实例B退出
- **THEN** 之后任意实例拒绝该会话
#### Scenario: 依赖故障
- **WHEN** Redis或用户查询不可用
- **THEN** 返回503并拒绝继续转发

### Requirement: 可信用户转发
系统 SHALL 清除外部身份头，按路由权限签发内部身份，下游只接受验签身份并校验资源归属。
#### Scenario: 伪造用户头
- **WHEN** 客户端伪造X-User-Id或绕过网关直连
- **THEN** 无有效身份的请求不能访问用户数据

### Requirement: 本地启停
系统 SHALL 提供启动、停止、状态及显式建表命令，保留现有数据。
#### Scenario: 重复启动
- **WHEN** 已有服务或端口被占用
- **THEN** 不重复启动或终止未知进程，返回明确原因
