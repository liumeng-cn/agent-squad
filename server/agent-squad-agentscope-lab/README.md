# AgentScope Java 学习实验室

这是 AgentSquad 的独立 Maven 子模块，位于 `server/agent-squad-agentscope-lab`。它继承 `server/pom.xml` 的统一依赖版本与 Java 编译配置，但不依赖 Spring 容器、业务数据库或 AgentSquad 业务服务，也不会被打进正式服务运行包。

## 版本基线

- Java 编译目标：继承父工程配置（Java 21）
- AgentScope：继承父工程的 `2.1.0-BETA1`
- 模型接口：OpenAI 兼容协议，默认示例指向 DashScope 兼容接口
- 运行数据：`server/agent-squad-agentscope-lab/.agentscope-lab/workspace`

官方文档持续更新，部分页面描述的是后续 2.0.x 版本。学习时先以本模块固定依赖的实际编译结果为准，遇到差异再对照官方版本记录，不直接把新版本 API 当成当前版本 API。

## 1. 环境准备

需要 JDK 21 和 Maven 3.9+。

macOS / zsh 示例：

```bash
export AGENTSCOPE_API_KEY='你的模型 API Key'
export AGENTSCOPE_BASE_URL='https://dashscope.aliyuncs.com/compatible-mode/v1'
export AGENTSCOPE_MODEL='qwen-plus'
```

也可以使用其他 OpenAI 兼容模型服务，分别设置 API Key、Base URL 和模型名称。密钥只从环境变量读取，不写入代码或配置文件。

## 2. 运行课程

在仓库根目录执行。

### 第 1 课：创建 Agent 并流式接收事件

```bash
mvn -f server/pom.xml -pl agent-squad-agentscope-lab compile exec:java \
  -Dexec.mainClass=com.agentsquad.squad.lab.agentscope.QuickstartLesson
```

可以追加问题作为命令行参数：

```bash
mvn -f server/pom.xml -pl agent-squad-agentscope-lab compile exec:java \
  -Dexec.mainClass=com.agentsquad.squad.lab.agentscope.QuickstartLesson \
  -Dexec.args="用 Java 解释什么是 ReAct 循环"
```

重点观察：

- `HarnessAgent.builder()` 如何组合 Agent 配置
- `build()` 创建出来的 Agent 实例如何使用和关闭
- `RuntimeContext` 如何提供 userId / sessionId
- `streamEvents()` 如何返回事件流，文本增量和工具事件如何区分

### 第 2 课：注册 Java Tool

```bash
mvn -f server/pom.xml -pl agent-squad-agentscope-lab compile exec:java \
  -Dexec.mainClass=com.agentsquad.squad.lab.agentscope.ToolCallingLesson
```

让模型调用本地的 `add_numbers` 工具。重点看 `@Tool`、`@ToolParam`、`Toolkit.registerTool()` 三者的关系，以及工具描述如何交给模型。

### 第 3 课：跨 Agent 实例延续同一会话

```bash
mvn -f server/pom.xml -pl agent-squad-agentscope-lab compile exec:java \
  -Dexec.mainClass=com.agentsquad.squad.lab.agentscope.SessionPersistenceLesson
```

这个例子会先创建一个 Agent 完成第一轮对话、关闭实例，再创建另一个 Agent，使用相同的 agentId、userId、sessionId 和 Workspace 发起第二轮对话。

可以换一个 sessionId 做隔离实验：

```bash
mvn -f server/pom.xml -pl agent-squad-agentscope-lab compile exec:java \
  -Dexec.mainClass=com.agentsquad.squad.lab.agentscope.SessionPersistenceLesson \
  -Dexec.args="session-demo-002"
```

注意：这个实验验证的是同一会话的历史延续，不等同于完整验证进程崩溃恢复、跨机器接管或长期记忆提取。

## 3. 先建立几个关键概念

### Agent、ReActAgent、HarnessAgent

- `Agent` 是统一的执行抽象。
- `ReActAgent` 实现基础的“推理 → 工具调用 → 根据结果继续推理”循环。
- `HarnessAgent` 在基础执行循环外增加工作区、记忆、上下文压缩、Skill、SubAgent、沙箱、会话日志和恢复等工程能力。

学习顺序建议先理解 ReAct 循环，再看 Harness 增加了什么，不要一开始就把 Harness 当成一个黑盒。

### Builder、Agent 实例和 RuntimeContext

- Builder 保存相对稳定的模型、提示词、工具和工作区配置。
- 一次直接请求创建一个 Agent 实例，执行结束后关闭。
- `agentId` 标识 Agent；`userId` 和 `sessionId` 标识本次调用属于谁、哪段会话。
- 会话是否延续，不能只看 Java 对象是否复用，还要看身份和持久化后端是否一致。

### Workspace、Filesystem 和 Session Log

Workspace 是 Agent 使用的一组逻辑工作目录和能力资源，不等于必须把数据写在 Web 服务本机磁盘。

需要分开理解：

1. Workspace 里的人格说明、知识、Skill、计划、记忆和文件等资源。
2. Filesystem 决定这些工作区路径实际读写到哪里，可以是本地文件系统，也可以接入共享存储或沙箱文件系统。
3. 原生 Session Log 保存执行事实和状态检查点；默认配置与显式 SessionLogStore 的选择，需要结合当前 SDK 版本核实。
4. 共享后端不仅要能读写文件，还要满足原生日志提交所需的并发一致性要求，不能把普通 KV 的 set/get 直接当成完整分布式日志实现。

### 会话历史和长期记忆不是一回事

- 会话历史解决“当前这段对话之前说过什么”。
- AgentState / Session Log 负责保存和恢复执行状态。
- 长期记忆解决“哪些信息值得跨会话保留，并在后续调用时重新提供给 Agent”。
- 不要把旧版 `Memory` 接口、会话历史和 Harness 的长期记忆机制混为一谈；先确认当前依赖版本的默认行为和扩展点。

## 4. 后续学习顺序

| 顺序 | 主题 | 要回答的问题 |
|---|---|---|
| 1 | Agent / ReActAgent / HarnessAgent | 框架的核心执行循环是什么，Harness 增加了哪些能力？ |
| 2 | Model / Toolkit / Tool | 模型如何选择工具，工具参数如何定义和执行？ |
| 3 | RuntimeContext / Session | 多用户、多会话如何隔离，为什么可以每次创建新 Agent？ |
| 4 | Workspace / Filesystem | 工作区目录如何映射到本地、共享存储或沙箱？ |
| 5 | Session Log / State | 保存了什么，加载发生在什么时机，如何恢复？ |
| 6 | Memory / Compaction | 会话上下文与长期记忆如何分工，记忆如何形成和加载？ |
| 7 | Middleware / Events / Permission | 如何插入观测、控制工具调用、审批和中断？ |
| 8 | SubAgent / AgentSession | 子 Agent 如何创建、通信、等待、恢复和回传事件？ |

每一课先阅读官方实现和文档，再补一个能运行的小实验。没有核对过的能力不提前写成项目架构结论。

## 5. 官方资料

- [AgentScope Java 2.0 中文文档](https://github.com/agentscope-ai/agentscope-java/tree/main/docs/v2/zh/docs)
- [快速开始](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/quickstart.md)
- [Agent 与 ReActAgent](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/building-blocks/agent.md)
- [Tool 与 Toolkit](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/building-blocks/tool.md)
- [上下文与状态](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/building-blocks/context.md)
- [Harness 工作区](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/harness/workspace.md)
- [Harness 文件系统](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/harness/filesystem.md)
- [Middleware](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/building-blocks/middleware.md)
- [版本记录](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/others/release-notes.md)
