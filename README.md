# 小队 · Agent Squad

通用多智能体运行底座。2026-10-20 独立重建；当前正在打通单 Master 正向链路，后续沿设计实现受控工具与多 Worker 协作。

Master 负责目标理解、全局上下文、规划、Worker 委派与结果聚合。Worker 负责任务局部执行，自动识别使用 Skill，通过受控工具访问外部能力。计算实例无状态，任务与恢复状态外置持久化。

当前方向：Java + AgentScope Harness SDK，固定依赖版本为 2.1.0-BETA1。MyBatis 受理事务曾在 H2 文件库重启和项目专用 MySQL 8.4 验证。命令领取、`AgentSession.submit` 桥接、运行生命周期投影和直接回答的完成候选裁决已接入。Harness 使用官方 `agentscope-extensions-jdbc`，直接接入 Spring 的 `DataSource`；原生日志通过 SDK 的共享 Workspace 存储。`SQUAD_DELIVERY_ENABLED` 是投递功能开关；桥接默认关闭，启用时还需配置 `SQUAD_MODEL_API_KEY`、`SQUAD_MODEL_BASE_URL`、`SQUAD_MODEL_NAME`。Master 调用 `propose_completion` 仅保存候选；同一原生 Turn 成功结束并被持久投影后，平台才在一个事务中写最终答案事件、任务终态并释放对话。缺少候选时持久排入一次纠正，仍缺失则明确失败。2026-10-22 使用全新隔离 H2 库和 DeepSeek 完成了一次登录、提交目标、模型回答、终态投影及快照读取的正向验证；全量测试 13 项中 12 项通过、1 项跳过。现有工作台已展示目标历史与最终答案，尚未做浏览器端人工验收。详见[框架适配调研](docs/research/agentscope-java.md)、[D0 验证记录](docs/d0-evidence.md)与[D1 验证记录](docs/d1-evidence.md)。

Java 模块统一使用 Lombok；需要日志的类使用 `@Slf4j`，请求、响应和持久投影等不可变数据保留 Java `record`。

异常回合的 D1 状态收口已补齐：非成功原生回合进入可诊断的 `RECOVERY_BLOCKED`，取消优先；恢复命令明确拒绝或多次失败时不能伪装成功。该异常分支目前由隔离持久库测试验证，真实 SDK 故障注入和多 Worker 恢复仍是后续工作。

## 设计入口

1. [整体技术架构与选型](docs/architecture.md)、[项目结构与依赖规则](docs/project-structure.md)。
2. [需求、场景与模块边界](docs/overview.md)、[对象与恢复协议](docs/runtime-contract.md)。
3. [模块详细设计](docs/modules/README.md)：逐模块定义用例、接口、数据、事务、异常和验收。
4. [HTTP 契约](contracts/openapi.json)：请求、响应、必填字段、限制及错误码。
5. [数据字典与关系](data/README.md)、[MySQL 设计 DDL](data/schema.sql)：仅设计，不是迁移或已创建数据库。
6. [选型与复用决策](decisions/001-rebuild.md)：旧项目中可参考的资产与必须重写的部分。
7. [页面与交互设计](docs/web-console.md)、[静态视觉稿](docs/ui/README.md)、[开发准备及交付顺序](docs/predevelopment.md)、[配置基线](docs/configuration-baseline.md)。
8. [设计评审清单](docs/review.md)、[主线验收场景](docs/acceptance-cases.md)：未验证项与实现前门槛。

旧项目 the previous project 不作为本项目依赖。未复制业务代码、环境文件、密钥、数据库迁移、前端或 Git 历史。

本项目不继承旧接口、旧表和旧行为兼容义务。文档、接口和 DDL 必须同步评审；一份旧代码只有符合这里的职责与验收规则，才允许迁入。

## 设计自检

在具备 jsonschema 的 Python 3.10+ 工具环境运行 `python tools/check_design.py`。仅检查文档与契约，不启动服务、不访问数据库。具体检查范围与待评审项见设计评审清单。
