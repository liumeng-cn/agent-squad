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

## 2. IntelliJ IDEA 调试（推荐）

本模块已经提交了 3 个共享 Run Configuration，拉取分支并在 IDEA 中重新加载 Maven 后即可使用：

- `01 - AgentScope 快速开始`
- `02 - AgentScope 工具调用`
- `03 - AgentScope 会话延续`

### 第一次导入

1. 用 IDEA 打开仓库根目录。
2. 打开 Maven 工具窗口，找到 `server/pom.xml`，点击 Reload All Maven Projects。
3. 确认 Project SDK 和模块 SDK 使用 JDK 21（File → Project Structure → Project）。
4. 等待 Maven 依赖下载完成。
5. 打开右上角运行配置下拉框，选择上述任意一个 `AgentScope` 配置。

### 配置模型 API Key

共享运行配置不会保存密钥。第一次运行前，在 IDEA 中打开 Run → Edit Configurations，分别选中这三个配置，在 Environment variables 中添加：

```text
AGENTSCOPE_API_KEY=你的模型API_KEY
AGENTSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AGENTSCOPE_MODEL=qwen-plus
```

如果使用其他 OpenAI 兼容模型服务，替换 Base URL、模型名和 API Key 即可。不要把真实 API Key 提交到 Git。

### 开始调试

1. 打开对应课程的 Java 文件。
2. 在下面列出的断点位置点击行号左侧，打上红色断点。
3. 选择对应的运行配置，点击 Debug（小虫子图标），不要点击普通 Run。
4. 程序停住后，使用 Step Over（F8）、Step Into（F7）、Resume（F9），在 Variables 查看局部变量。

推荐断点：

| 课程 | 建议断点 | 重点观察 |
|---|---|---|
| 快速开始 | `QuickstartLesson.main()` 中 `build()` 前后 | Builder 如何构造 Agent，Agent 实例何时创建和关闭 |
| 快速开始 | `AgentScopeLabSupport.stream()` 的 `streamEvents()` 和 `doOnNext()` | 事件流何时订阅、每个事件的类型和内容 |
| 工具调用 | `ToolCallingLesson.main()` 的 `registerTool()` | 工具如何注册到 Toolkit |
| 工具调用 | `CalculatorTools.addNumbers()` 方法第一行 | 模型发起工具调用后，Java 方法如何被执行、参数是什么 |
| 会话延续 | `SessionPersistenceLesson.runTurn()` 的 Agent 创建处 | 两轮是否创建了不同 Agent 实例，sessionId 是否相同 |

### 调试 AgentScope 框架源码

1. 在 IDEA 的 Maven 工具窗口中确认依赖已下载。
2. 在 External Libraries 中找到 `agentscope-harness` 或 `agentscope-core`。
3. 展开对应类，打开 `HarnessAgent`、`ReActAgent`、`Toolkit` 等源码。
4. 如果当前只有反编译视图，在 Maven 工具窗口执行 Download Sources（或在依赖上选择 Download Sources）。
5. 从本模块调用处按住 Ctrl / Cmd 点击方法名，进入 AgentScope 源码；也可以在源码中直接打断点。

### 关于流式调试

`streamEvents()` 返回的是 Reactor 流。调用 `blockLast()` 后当前线程会等待事件流完成；事件处理逻辑在 `doOnNext()` 回调中执行。断点可能在回调执行时命中，而不一定紧挨着调用语句。调试时重点看事件对象的 `getType()`，以及文本增量、工具调用开始等不同事件。

如果程序停在网络请求上，先检查 API Key、Base URL、模型名称和网络连接；不要把等待模型响应误认为 IDEA 卡死。

---

## 3. 运行课程

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

## 4. 先建立几个关键概念

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

## 5. 后续学习顺序

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

## 6. 官方资料

- [AgentScope Java 2.0 中文文档](https://github.com/agentscope-ai/agentscope-java/tree/main/docs/v2/zh/docs)
- [快速开始](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/quickstart.md)
- [Agent 与 ReActAgent](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/building-blocks/agent.md)
- [Tool 与 Toolkit](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/building-blocks/tool.md)
- [上下文与状态](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/building-blocks/context.md)
- [Harness 工作区](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/harness/workspace.md)
- [Harness 文件系统](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/harness/filesystem.md)
- [Middleware](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/building-blocks/middleware.md)
- [版本记录](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/others/release-notes.md)
