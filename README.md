# Agent Squad

Agent Squad 是以 Web 会话为入口的多智能体运行底座。Master 可以直接回答，也可以创建目标任务、发布执行计划并委派 Worker。平台负责持久受理、权限隔离、工具授权、预算、执行恢复和产物交付；AgentScope Harness 提供原生 Session、Turn、Run 执行事实。

## 日志追踪与 Agent 测评设计

- [全链路日志追踪与 Agent 测评方案](docs/agent-observability-design.md)：从用户请求、Master/Worker、模型和工具，到后台恢复与答案交付，预留以后的评测。
- [具体开发任务](docs/agent-observability-tasks.md)：分阶段落地；目前是设计，尚未实现全部追踪功能。
- [项目术语说明](docs/terminology-guide.md)：用容易理解的描述解释“事件序号”“后台接手命令”“Agent 执行事件”等概念。

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

### 1. 本地配置

准备 JDK 27、Maven、Node.js 22 和 MySQL 8.4。本机验证参数已直接填写在两个模块的 `src/main/resources/application-local.yml`：数据库连接账号密码、内部身份密钥和模型参数，不使用环境变量占位表达式。

- [Server 本地配置](server/agent-squad-server/src/main/resources/application-local.yml)：数据库、模型、Worker、密钥保险库与投递开关。
- [Gateway 本地配置](server/agent-squad-gateway/src/main/resources/application-local.yml)：数据库连接和内部身份配置。
- `deploy/compose.local.yml`：本机 MySQL 参数，仅本地保存。

两个服务默认使用 `local` profile，直接执行下方启动命令即可，不需要加载 `.env.local`。已有数据库不得为启动应用而清空或重建。测试使用独立配置；其他环境后续再配置 Nacos 或其他来源。

### 2. 先启动 MySQL

先启动 Docker Desktop，再启动本机已有的数据库容器：

```bash
docker start squad-mysql
docker ps --filter name=squad-mysql
```

确认容器处于运行状态，`13306` 端口可用后再启动 Java 服务。日常重启复用该容器及原有数据，不重复创建数据库或切换数据卷。

全新环境才使用下面的本地 Compose 创建数据库；已有 `squad-mysql` 时不要同时运行两套 MySQL，以免争用 `13306` 端口：

```bash
docker compose -f deploy/compose.local.yml up -d db
```

### 3. 导入本地表结构并启动业务服务

需要重新刷本地库时，在数据库客户端手动执行 [完整重建 SQL](deploy/db/local-rebuild.sql)。脚本会删除并重建51张项目表，导入最终 baseline，并创建两个测试账号；执行前停止 Server 和 Gateway，确认目标库与需要保留的数据。平时重启服务不执行此脚本。

| 登录账号 | 测试密码 | 角色 |
|---|---|---|
| admin | admin-password | 管理员 |
| demo | demo-password | 普通用户 |

以上密码在执行重建 SQL 后生效，原库现有账号不会被服务启动自动重置。密码在数据库中以 BCrypt 摘要存储。

```bash
mvn -f server/agent-squad-server/pom.xml -B -ntp spring-boot:run
```

开发环境启动时重放最终 baseline，补齐缺失表，不更新已有表的结构。已有本地表需要显式调整并同步 baseline；生产环境禁止应用启动执行 DDL。

登录账号存放在数据库 `sq_user`，密码以 BCrypt 摘要保存，不从配置文件读取登录密码。`admin` 为管理员，`demo` 为本地普通测试账号；不要将数据库连接账号与网页登录账号混淆。

空白数据库的首个管理员仍通过独立 `bootstrap-admin` 初始化命令建立；已有账号时不重复初始化，也不在服务启动时自动覆盖账号密码。

### 4. 启动网关与前端

网关终端：

```bash
mvn -f server/agent-squad-gateway/pom.xml -B -ntp spring-boot:run
```

前端终端：

```bash
npm --prefix web/workbench ci
npm --prefix web/workbench run dev
```

访问 [本地登录页](http://127.0.0.1:5173/login)。默认链路为页面 `5173` → Gateway `18081` → Server `18080`。前端代理目标直接修改 `web/workbench/vite.config.ts`，业务服务地址直接修改网关的 `application-local.yml` 中 `gateway.business-service-url`。

### 停止本地服务

在前端、网关和业务服务各自的终端按 `Ctrl+C`，待进程退出后停止数据库：

```bash
docker stop squad-mysql
```

如果使用本地 Compose 启动数据库，则执行 `docker compose -f deploy/compose.local.yml stop db`。停止服务保留数据，下次按 MySQL → Server → Gateway → 前端的顺序启动。

### 启动报错排查

出现 `dataSourceScriptDatabaseInitializer` / `Failed to execute database script` 时，先确认数据库已启动，再查看异常末尾的 `Caused by`。这两行是初始化失败的外层异常，不能单凭它们认定 SQL 脚本有问题。

- 连接拒绝或通信失败：检查 Docker、MySQL 容器及 `13306` 端口。
- `Access denied`：核对两个 `application-local.yml` 中的数据库账号密码。
- 数据库不存在：核对 JDBC URL 与实际库名；已有数据时不要删除或重建数据库。
- 具体 SQL 执行失败：根据最内层异常定位语句和结构差异，修复前核对现有数据。

## 验证

测试不启用 `local` profile，不加载本地账号和模型配置。MySQL 集成测试需要显式设置 `SQUAD_TEST_MYSQL_ADMIN_URL`（指向专用测试实例的 `mysql` 系统库）、`SQUAD_TEST_MYSQL_USER` 和 `SQUAD_TEST_MYSQL_PASSWORD`。测试创建自己的 `as_test_` schema，不能指向生产或共享数据库。

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

Mapper XML 按能力放在 `server/agent-squad-server/src/main/resources/mapper/<模块>/` 下，不增加 `repository/` 中间目录；XML namespace 对应 Java Mapper 接口的完整包名。
传输结构以 [OpenAPI](api/openapi.json) 为准；最终数据库结构以 [V1 baseline](server/agent-squad-server/src/main/resources/db/migration/V1__baseline.sql) 为准。生产迁移链位于 [deploy/db/migrations](deploy/db/migrations/)，必须与 baseline 通过结构一致性校验。

[Staging 手册](deploy/runbooks/staging.md) 与 [发布验证及回退](deploy/runbooks/release-verification.md) 描述部署边界。生产必须使用 HTTPS、安全 Cookie、可信网关及私网业务端口；密钥从部署环境注入，业务账号不具备 DDL 权限。

V5 引入最终交付权。旧在途请求、历史失败重试和答案修订必须先审计并确定回填策略，不能自动猜测历史终稿归属。未经迁移核验、完整恢复演练和用户批准，不执行生产迁移或部署。
