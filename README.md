# Agent Squad

Agent Squad 是以 Web 会话为入口的多智能体运行底座。Master 可以直接回答，也可以创建目标任务、发布执行计划并委派 Worker。平台负责持久受理、权限隔离、工具授权、预算、执行恢复和产物交付；AgentScope Harness 提供原生 Session、Turn、Run 执行事实。

## 当前能力与边界

- 连续会话、普通回复与后台目标任务；支持补充要求、追问、审批、取消和受控重试。
- Worker 能力发现、任务计划、依赖调度和结果汇合。
- Skill 版本与授权、显式记忆、连接密钥保险库、操作账本和沙箱产物交付。
- MySQL 持久账号与会话、管理员管理、网关签名身份和用户数据隔离。
- SSE 事件通知、快照恢复、Markdown 展示及按用户隔离的草稿。
- 同源请求最终交付权仲裁，避免普通回复与委派任务重复发布终稿。

当前仍为本地验证阶段，**尚未批准生产发布**。持久运行容量控制默认关闭；真实 SDK 跨进程接管、完整多用户安全矩阵、真实模型流式、容量压测及完整灾备演练仍未验收。当前支持的部署边界为单业务执行实例，不能把本地测试通过视为多副本或生产可用证明。

在途工作见 [OpenSpec 变更](openspec/changes/)，发布验证范围见 [发布门槛](openspec/changes/multiuser-04-release-readiness/evidence/release-gate.md)。已归档的 OpenSpec 文档已从工作区删除，历史内容可通过 Git 查阅。

## 技术栈

| 部分 | 配置 |
|---|---|
| Java | JDK 27 构建与运行验证，编译目标 21；Java 21 运行兼容性待验证 |
| 后端 | Spring Boot、Spring MVC、MyBatis、MySQL 8.4 |
| 网关 | Spring Cloud Gateway、持久会话、HMAC 内部身份断言 |
| Agent | AgentScope Harness `2.1.0-BETA1`，固定版本，不跟随 main |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router；Node.js 22 |

Java 服务和页面可在本机运行，Docker 提供 MySQL 与受控沙箱依赖。Python 仅用于静态设计检查，不是业务服务。

## 本地启动

### 1. 配置数据库与环境

准备 JDK 27、Maven、Node.js 22 和 MySQL 8.4。默认数据库地址为 `127.0.0.1:13306/agent_squad`，需预建数据库并配置可访问账号。已有数据库不得为启动应用而清空或重建。

```bash
cp .env.example .env.local
```

在 `.env.local` 配置数据库连接，并设置：

- `SQUAD_AUTH_MODE=database`：使用持久账号。
- `SQUAD_IDENTITY_HMAC_KEY`：不少于 32 字节，Gateway 与 Server 保持一致。
- `SQUAD_IDENTITY_STRICT=true`、`SQUAD_IDENTITY_ENFORCE_USER_STATUS=true`：校验网关身份及账号状态。
- `SQUAD_CONNECTION_SECRET_KEY`：连接密钥保险库主密钥；未设置时不能保存连接密钥。
- `SQUAD_DELIVERY_ENABLED=false`：仅持久受理，不调用模型。启用执行时同时配置模型的 API Key、Base URL 和模型名。

`.env.local` 已忽略，不提交密钥、密码或令牌。以下服务终端分别执行 `set -a; source ./.env.local; set +a` 加载环境。

### 2. 启动业务服务并初始化管理员

```bash
mvn -f server/agent-squad-server/pom.xml -B -ntp spring-boot:run
```

开发环境启动时重放最终 baseline，补齐缺失表，不更新已有表的结构。已有本地表需要显式调整并同步 baseline；生产环境禁止应用启动执行 DDL。

首次使用时，在另一个已加载环境的终端构建并运行一次性管理员命令：

```bash
mvn -f server/agent-squad-gateway/pom.xml -B -ntp package -DskipTests
java -jar server/agent-squad-gateway/target/agent-squad-gateway-0.1.0-SNAPSHOT-bootstrap-admin.jar
```

执行前通过终端环境设置 `SQUAD_BOOTSTRAP_ADMIN_USERNAME` 和 `SQUAD_BOOTSTRAP_ADMIN_PASSWORD`。账号表非空时拒绝再次初始化；成功后移除临时环境变量，首次登录须改密。这里的 `-DskipTests` 仅用于构建初始化命令，不代表完成验证。

### 3. 启动网关与前端

网关终端：

```bash
mvn -f server/agent-squad-gateway/pom.xml -B -ntp spring-boot:run
```

前端终端：

```bash
npm --prefix web/workbench ci
npm --prefix web/workbench run dev
```

访问 [本地登录页](http://127.0.0.1:5173/login)。默认链路为页面 `5173` → Gateway `18081` → Server `18080`。`VITE_API_TARGET` 可覆盖前端代理目标，`SQUAD_BUSINESS_SERVICE_URL` 可覆盖业务服务地址。

## 验证

在未加载 `.env.local` 的独立终端执行。MySQL 集成测试需要显式设置 `SQUAD_TEST_MYSQL_ADMIN_URL`（指向专用测试实例的 `mysql` 系统库）、`SQUAD_TEST_MYSQL_USER` 和 `SQUAD_TEST_MYSQL_PASSWORD`。测试创建自己的 `as_test_` schema，不能指向生产或共享数据库。

```bash
mvn -f server/pom.xml -B -ntp test
npm --prefix web/workbench test
npm --prefix web/workbench run build
python3 tools/check_design.py
python3 tools/check_runtime_safety.py
python3 tools/audit_test_reports.py
```

设计检查依赖 Python `jsonschema`。专项 MySQL 与真实沙箱测试还需要独立环境变量和固定镜像摘要；未配置时会跳过，报告审计脚本将阻断发布。CI 在隔离 MySQL 与 Docker 环境执行全量验证，配置见 [CI 工作流](.github/workflows/ci.yml)。远程工作流未实际运行前不能宣称 CI 验收通过。

## 目录与权威来源

```text
server/
  agent-squad-gateway/   认证、账号管理与业务代理
  agent-squad-server/    会话、任务、Worker、工具及运行恢复
web/workbench/          Web 工作台
openspec/specs/         R01–R12 能力与行为规格
openspec/changes/       当前未完成变更、任务与验证证据
api/                    OpenAPI、JSON Schema 和界面映射
deploy/                 镜像、staging、受控迁移与操作手册
tools/                  设计、运行安全与测试报告检查
```

传输结构以 [OpenAPI](api/openapi.json) 为准；最终数据库结构以 [V1 baseline](server/agent-squad-server/src/main/resources/db/migration/V1__baseline.sql) 为准。生产迁移链位于 [deploy/db/migrations](deploy/db/migrations/)，必须与 baseline 通过结构一致性校验。

Java 修改遵循 [项目约定](AGENTS.md)：Controller → Service → Mapper 分层、DTO 与持久对象分离、`@Resource` 字段注入、中文职责注释和简体中文提交信息。

## 部署

[Staging 手册](deploy/runbooks/staging.md) 与 [发布验证及回退](deploy/runbooks/release-verification.md) 描述部署边界。生产必须使用 HTTPS、安全 Cookie、可信网关及私网业务端口；密钥从部署环境注入，业务账号不具备 DDL 权限。

V5 引入最终交付权。旧在途请求、历史失败重试和答案修订必须先审计并确定回填策略，不能自动猜测历史终稿归属。未经迁移核验、完整恢复演练和用户批准，不执行生产迁移或部署。
