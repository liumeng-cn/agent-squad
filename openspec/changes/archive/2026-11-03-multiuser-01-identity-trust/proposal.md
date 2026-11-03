# Proposal：C1 多用户身份、持久会话与可信网关

## Why

当前 `agent-squad-gateway/auth/AuthService.java` 通过环境变量配置单账号，使用进程内 Map 保存会话；业务服务 `identity/UserContextInterceptor.java` 直接信任 `X-User-*`。这是面向本机开发的边界，不满足多用户正式在线产品的身份、停用、密码管理、跨实例会话和可信身份传递要求。现有 OpenSpec `access` 规范中“账号禁用或 authVersion 变更导致旧会话失效”尚未实现。

## 产品范围与决定

- 产品：**ChatGPT 式 Web 连续对话 + Master/Worker 后台协作**，面向多用户正式在线产品，不是 AI Coding 客户端。
- C1 **只实现邀请/管理员创建账号**，默认 `INVITE_ONLY`；不做开放注册、邮箱验证、社交登录、付费、组织空间。公开注册作为独立后续 change，避免在没有邮件/反滥用设施时误开放入口。
- 所有用户拥有稳定 `user_id`（UUID char(36)），普通用户不能访问其他用户资源；`ADMIN` 只可管理账号/配置，不自动拥有他人的会话、记忆或附件读取权。
- 保持 `SQUAD_SESSION` HttpOnly Cookie、`X-CSRF-Token` 和现有 `AuthView` 对前端的语义；必要时仅新增字段。登录失败统一反馈，密码和原始 Token 不落库、不记录日志。

## What Changes

1. 网关加入 MySQL 持久账户/会话、密码哈希、登录节流与账号管理；阻塞 JDBC 操作不得运行在 WebFlux event loop。
2. 网关到业务服务改为**有签名的短时内部身份断言**（HMAC SHA-256 + 限定 issuer/audience/请求方法/路径/时效）；业务端弃用未签名 `X-User-*` 作为认证依据。
3. 业务运行前与执行安全点增加账号状态检查：禁用后拒绝新提交、新模型请求、新工具/沙箱派发；已经 IN_FLIGHT 的写入必须进入原有完成或 UNKNOWN 核对流程，不得冒充“回滚成功”。
4. 新增账号管理与修改密码页面；首次登录需修改临时密码；更新 OpenAPI、access spec、文档与测试。
5. 新增生产数据库变更脚本与环境分层，禁止生产启动自动重放 baseline；详见 design.md 的迁移约束。

## Non-goals

- 不改造 Master/Worker 的任务逻辑、不引入微服务按 Agent 角色拆分。
- 不做用户共享工作区、组织租户、OAuth、公开注册、支付系统。
- 不修改历史业务 ID，且不删除/重建用户开发库，不修改真实部署环境。

## Impact / Dependencies

- 主要代码：`server/agent-squad-gateway/auth/`、`filter/`、`gateway/pom.xml`；`server/agent-squad-server/identity/`、`runtime/service/ExecutionAuthorizationService.java`、模型与工具执行入口；`web/workbench/src/features/auth` 和新管理页；`api/openapi.json`、`V1__baseline.sql`、部署 SQL。
- R01、B01/B14、R08/R11；回归 A01/A02/A03/A07/A11/A12。
- 前置：锁定 Git 基线，完成测试基线和迁移备份方案；不允许生产数据库操作。
- 完成门槛：两个真实不同账号登录、会话隔离、过期/停用/改密立即失效、未经网关的伪造身份请求被拒绝；两套网关实例共享会话存储且行为一致。
