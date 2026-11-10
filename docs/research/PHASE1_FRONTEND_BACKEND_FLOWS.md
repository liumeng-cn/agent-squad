# AgentSquad 一期客服前后端交互流程

> 目的：先明确用户操作、前端请求和后端处理的先后关系，为后续接口清单和 OpenAPI 设计提供依据。
>
> 范围：一期客服主流程。只画前端 Web 与 AgentSquad 后端两条泳道；AgentScope 作为后端内部运行能力，不额外拆成第三条泳道。
>
> 原则：不同场景分图表达，不把页面初始化、消息执行、事件推送和中断恢复塞进同一张图。当前不定义具体请求/响应字段，也不预设 AgentScope 尚未核实的行为。

## 1. 页面初始化与会话列表

用户进入客服页面后，前端获取当前用户可访问的会话列表。后端负责身份校验和数据查询。

```mermaid
flowchart LR
    subgraph FE["前端 Web"]
        direction TB
        A["进入客服页面"]
        B["请求会话列表"]
        C["展示会话列表"]
        A --> B
        B --> C
    end

    subgraph BE["AgentSquad 后端"]
        direction TB
        D["接收会话列表请求"]
        E["校验用户身份与访问权限"]
        F["查询当前用户的会话"]
        G["返回会话列表"]
        D --> E --> F --> G
    end

    B --> D
    G --> C
```

## 2. 创建会话或打开已有会话

创建会话与打开已有会话是两种不同操作，因此在同一张图中用分支表示。会话属于 AgentSquad 产品数据；AgentScope 的 AgentSession 如何初始化或关联，按框架原生生命周期处理，具体时机后续核实。

```mermaid
flowchart LR
    subgraph FE["前端 Web"]
        direction TB
        A["用户选择操作"]
        B["请求创建会话"]
        C["请求会话详情与历史消息"]
        D["展示会话并进入聊天"]
        A --> B
        A --> C
        B --> D
        C --> D
    end

    subgraph BE["AgentSquad 后端"]
        direction TB
        E["接收创建会话请求"]
        F["校验身份并创建产品会话记录"]
        G["返回新会话信息"]
        H["接收会话详情请求"]
        I["校验会话归属"]
        J["查询会话与历史消息"]
        K["返回会话详情和消息"]
        E --> F --> G
        H --> I --> J --> K
    end

    B --> E
    G --> D
    C --> H
    K --> D
```

## 3. 发送消息与提交 AgentScope 执行

这张图只描述一次用户消息如何进入 AgentScope。Agent Definition 是 AgentSquad 维护的配置定义；实际 Agent、AgentSession 和本轮执行由 AgentScope 运行时负责。

```mermaid
flowchart LR
    subgraph FE["前端 Web"]
        direction TB
        A["用户输入并发送消息"]
        B["提交消息请求"]
        C["收到受理结果"]
        D["准备订阅本轮执行事件"]
        A --> B --> C --> D
    end

    subgraph BE["AgentSquad 后端"]
        direction TB
        E["接收消息请求"]
        F["校验用户与会话权限"]
        G["保存用户消息"]
        H["读取会话绑定的 Agent Definition"]
        I["按原生生命周期获取或初始化 AgentSession"]
        J["调用 AgentScope 提交本轮消息"]
        K["返回受理结果及执行关联信息"]
        E --> F --> G --> H --> I --> J --> K
    end

    B --> E
    K --> C
```

**边界说明**

- AgentSquad 保存 Agent Definition，不自行维护一套 Agent Runtime。
- Master 创建 SubAgent、调用工具、推理和执行，优先交给 AgentScope。
- 本轮执行标识优先使用 AgentScope 原生任务标识；是否需要额外的产品关联记录，后续根据查询、权限和消息关联需求决定。
- 消息提交成功只表示后端已受理，不代表 Agent 已经执行完成。

## 4. SSE 事件订阅与消息展示

消息提交和事件订阅分开。前端收到受理结果后订阅本轮事件；后端从 AgentScope 获取运行事件，转换为前端可消费的产品事件。

```mermaid
flowchart LR
    subgraph FE["前端 Web"]
        direction TB
        A["建立本轮 SSE 连接"]
        B["接收产品事件"]
        C["更新消息内容与执行状态"]
        D["展示最终回复"]
        A --> B --> C --> D
    end

    subgraph BE["AgentSquad 后端"]
        direction TB
        E["校验订阅权限"]
        F["订阅 AgentScope 执行事件"]
        G["转换为产品事件"]
        H["按产品需要记录事件及游标"]
        I["通过 SSE 持续推送"]
        E --> F --> G --> H --> I
    end

    A --> E
    I --> B
```

**边界说明**

- SSE 连接只负责事件传输，不拥有 Agent 执行生命周期。
- AgentScope 原生事件不直接暴露给前端；由后端转换为稳定的产品事件。
- 事件是否全部持久化、如何生成游标，需要结合 AgentScope Session Log 能力和断线续传要求进一步确定。

## 5. 用户插话与中断

插话和中断是两种不同意图：插话是向正在执行的 Agent 补充信息或调整要求；中断是要求当前执行停止。两者分别调用 AgentScope 对应的原生能力，具体语义和适用状态需要按当前依赖版本核实。

```mermaid
flowchart LR
    subgraph FE["前端 Web"]
        direction TB
        A["用户选择操作"]
        B["提交插话/补充信息"]
        C["提交停止请求"]
        D["继续接收执行事件并更新界面"]
        A --> B
        A --> C
        B --> D
        C --> D
    end

    subgraph BE["AgentSquad 后端"]
        direction TB
        E["校验用户、会话及当前执行权限"]
        F["调用 AgentScope 对应的 steer / inject 能力"]
        G["调用 AgentScope interrupt 能力"]
        H["返回操作受理结果"]
        E --> F --> H
        E --> G --> H
    end

    B --> E
    C --> E
    H --> D
```

## 6. SSE 断线重连与事件补发

浏览器断线不应导致 Agent 执行自动终止。前端重新连接时携带已接收位置，后端补发缺失事件，再继续推送实时事件。

```mermaid
flowchart LR
    subgraph FE["前端 Web"]
        direction TB
        A["检测到 SSE 断开"]
        B["携带最后接收位置重新连接"]
        C["接收补发事件"]
        D["继续接收实时事件"]
        A --> B --> C --> D
    end

    subgraph BE["AgentSquad 后端"]
        direction TB
        E["校验用户与执行访问权限"]
        F["查询断点之后已保存的事件"]
        G["按顺序补发事件"]
        H["衔接实时事件订阅"]
        E --> F --> G --> H
    end

    B --> E
    H --> C
    H --> D
```

**需要保证**：历史补发与实时订阅之间不能出现事件丢失；重复事件需要能够识别和去重。具体实现方式待结合事件存储和 AgentScope 原生日志能力确定。

## 7. 当前流程图暂未确定的设计问题

| 问题 | 当前处理原则 | 后续需要核实 |
|---|---|---|
| AgentSession 初始化时机 | 交给 AgentScope 生命周期管理 | 创建产品会话时初始化，还是首次消息时初始化 |
| AgentSession 与产品会话关系 | AgentSquad 维护必要关联，不复制框架状态 | 原生 Session 标识及恢复方式 |
| 本轮执行标识 | 优先使用 AgentScope 原生任务标识 | 标识是否稳定、能否查询和控制 |
| 消息与执行关联 | AgentSquad 维护产品消息关系 | 一条用户消息与执行任务的关联规则 |
| 事件持久化 | 优先复用 AgentScope Session Log | 产品事件是否需要单独存储 |
| SSE 断线续传 | 由产品后端保证 | 原生日志能否提供可靠游标和补发 |
| 插话与中断 | 调用原生能力 | 当前依赖版本的具体语义和限制 |

## 8. 本文不包含的内容

- Agent Definition 的具体字段和配置 JSON。
- API 请求、响应字段及错误码。
- 数据库表结构。
- AgentScope 内部 Master / SubAgent 推理细节。
- 复杂任务工作台和平台接入能力。

以上内容应在前后端主交互流程确认后，再分别展开设计。
