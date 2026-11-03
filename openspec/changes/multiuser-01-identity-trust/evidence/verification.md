# C1 身份体系：本次远程增量记录

- 基线：`main@23c057bf5d138f12d772edf4b673a16b4533f0c5`
- 目标分支：`feature/multiuser-c1-identity`
- 状态：**IN PROGRESS；不具备多用户正式上线能力**
- 本次提交性质：C1 前置认证 HTTP 安全修正，并非 C1 任务验收完成。

## 已提交的代码

1. `AuthController`：在登录、查看会话和登出响应上设置 `Cache-Control: no-store`，降低会话信息被缓存风险。
2. 登录及登出 `SQUAD_SESSION` Cookie 统一依据 `agentsquad.auth.cookie-secure` 设置 `Secure`，默认 `false` 仅为了保持本地 HTTP 联调行为。**HTTPS/生产必须显式设 `SQUAD_AUTH_COOKIE_SECURE=true`。**
3. `GatewayProxyHttpTest`：增加真实 HTTP 响应 `no-store` 校验，并保留已有登录、CSRF、退出和代理行为测试。

## 验证

- 已通过 GitHub connector 读取并写入实际源码，提交在目标 feature 分支。
- **尚未执行 Maven / MySQL / 真实 HTTP 集成测试**：当前远程编辑环境无 Maven，也无法联网克隆仓库。GitHub 写入成功不代表测试通过。
- 本地执行命令：`mvn -f server/pom.xml -B -ntp test`，以及 `npm --prefix web/console run build`。如存在 skipped，按数量与原因记录。

## 尚未完成的主要 C1 内容

- 账号表、用户管理、密码哈希与持久 Session；目前仍是配置预置单账号和 JVM 内存会话。
- Gateway→Server 签名内部身份断言；目前业务侧仍接受可信头，生产不可暴露业务端口。
- 用户禁用/改密撤销、运行安全点、两用户访问隔离 E2E。
- 生产 Schema 迁移、schema parity 和 OpenAPI 契约更新。

## 发布限制

不得将本次增量宣称为 C1 已完成、可生产上线，也不得在公网开放 Server。不得以本文件替代实际构建、测试及安全验收证据。
