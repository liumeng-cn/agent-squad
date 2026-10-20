# 项目结构与依赖规则

2026-10-20 修订，Java + AgentScope 路线。下列源码和构建文件是目标结构，当前没有创建应用或安装依赖；原 Python/LangGraph 结构取消。

## 1. 仓库结构

先一个 Maven 应用、按业务包组织，不拆成大量空模块。隔离执行环境独立部署，不预设再写一个 Runner 服务。

```text
agent-squad/
├── README.md
├── AGENTS.md
├── decisions/
├── docs/
│   ├── overview.md
│   ├── architecture.md
│   ├── project-structure.md
│   ├── research/agentscope-java.md
│   └── modules/                       # 现行模块详细设计
├── contracts/                         # 公共 HTTP、工具 DTO、事件与追踪
├── data/                              # 业务 DDL 设计快照，尚未执行
├── server/                            # 后续 Java Maven 应用
│   ├── pom.xml
│   └── src/
│       ├── main/java/.../squad/
│       │   ├── identity/              # 登录、可信主体
│       │   ├── conversation/          # 会话归属、任务入口、查询
│       │   ├── agent/                 # Agent 定义、能力注册、Master/Worker 组装
│       │   ├── runtime/               # 会话生命周期、恢复协调、标识映射
│       │   ├── skills/                # 包校验、发布、版本、可见范围
│       │   ├── memory/                # 显式记忆的写入、纠正、删除
│       │   ├── tools/                 # 业务工具、连接、授权、操作账本
│       │   ├── interaction/           # 追问/审批的产品约束
│       │   ├── presentation/          # 持久事件读投影、快照、SSE
│       │   ├── artifacts/             # 上传、归属、交付和下载
│       │   ├── governance/            # 配额、预算和审计
│       │   ├── integration/agentscope/# 框架适配及固定版本配置
│       │   ├── config/
│       │   └── common/                # 少量真正共享类型
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── mapper/
│       │   └── db/migration/          # 业务表与框架表的受控迁移
│       └── test/
├── web/console/                       # 一期工作台，随运行切片同步实现
├── examples/
│   ├── workers/                       # 无业务依赖的示例能力
│   └── skills/
├── deploy/
│   ├── compose/                       # 开发环境，显式启动
│   └── sandbox/                       # 固定镜像、资源、网络与挂载策略
└── tools/                             # 设计与契约检查工具
```

不创建必需的 python/agent-runtime 或 python/sandbox-runner。未来某个工具需要 Python 时，把脚本及依赖放入该工具的隔离执行包，不改变整体主语言。

## 2. Java 模块内部层次

按实际需要使用 controller、dto、service、domain、repository、mapper；调用链为 Controller → Service → Repository → Mapper。Agent 定义和框架适配不是另一个 MVC 层，不机械复制所有子目录。

- Controller 处理协议、参数与当前身份，不执行模型循环或直接访问 Mapper。
- Service 编排用例、授权与短事务；模型调用、等待审批、沙箱和外部网络不占用数据库事务。
- 框架适配隔离 HarnessAgent、RuntimeContext、Session 等具体 API，但不把框架每个方法包成空壳接口。
- 业务工具入口再次校验真实主体、连接和资源；模型参数不携带可任意提升的身份。
- MyBatis 管业务表；AgentScope JDBC 适配管理框架状态格式。读模型不得变成第二个任务写入权威。
- 所有者生命周期明确：后台 Agent 不随提交 HTTP 返回而 close；应用关闭时停止调度并释放资源。

功能类及关键方法用中文说明职责、流程和生命周期；四空格、控制语句花括号、DTO 与持久对象分开。不要先生成空 Controller CRUD 作为模块完成证明。

## 3. 构建与边界

Maven 固定 AgentScope 同一版本及兼容 Boot/BOM；依赖树验证后再写应用构建文件。测试、契约检查、迁移校验、隔离恢复实验分别记录证据。

公开 HTTP 契约与 AgentScope 内部会话对象之间明确映射；框架 API 可以变化，但产品用户、逻辑任务、审批和文件归属语义要稳定。远程 Worker 以后通过独立适配，不强行将同 JVM 方法变为远程接口。

配置样例不含密钥；框架存储、Skill 发布与模型可写 Workspace 分别控制权限。生产挂载、数据库与快照备份不采用写死的开发机路径。

## 4. Web源码目标分层

web/console/src按app（路由与布局）、features（auth/chat/tasks/skills/connections/memory/admin）、shared/api、shared/ui、shared/lib组织。页面布局、状态、入口和API映射见[Web设计](web-console.md)；与后端切片共同验收，不作为最后补的演示页。此目录仍未创建应用。

## 5. Spring实现约束

用户已明确：Spring类不写内部类，DTO、record和配置辅助类独立文件；不使用构造方法注入，统一jakarta.annotation.Resource字段注入，不用Lombok生成注入构造器。此要求优先于后续技能或模板中的默认注入风格。

功能类和核心方法有中文职责/流程注释，关键日志说明操作、阶段、结果及原因，并携带适用的链路与业务ID。事务、外部副作用、幂等和恢复边界要解释清楚；不记录密钥、完整上下文或模型内部推理。详细规则见[协作约定](../AGENTS.md)。
