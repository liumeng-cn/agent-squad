# Session 原生数据结构

研究对象为 Maven `2.1.0-BETA1` Source Jar。以下路径相对于解压目录 `/tmp/agentscope-storage-2.1.0-BETA1`，不是本机 v2.0.4 Git 工作树。全部代码段直接摘录目标源码，未生成虚构 JSON；已运行本地无模型 Demo，结果见 [runtime-samples.json](runtime-samples.json)。

## 身份与格式

【源码确认】SessionKey.storagePath 使用 `agents/s_<agentId的UTF8无填充URL Base64>/sessions/s_<userId编码>/s_<sessionId编码>`，userId=null 按空串编码。逻辑身份与 filesystem namespace 是两层隔离；不能仅根据 sessionId 判断相同会话。

### SessionKey

【源码确认】`agentscope-core/io/agentscope/core/session/SessionKey.java:23-46`

```java
public record SessionKey(String userId, String agentId, String sessionId) {
    public SessionKey {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (agentId.isBlank() || sessionId.isBlank())
            throw new IllegalArgumentException("Empty session identity");
    }

    public String storagePath() {
        return "agents/"
                + encode(agentId)
                + "/sessions/"
                + encode(userId == null ? "" : userId)
                + "/"
                + encode(sessionId);
    }

    private static String encode(String value) {
        return "s_"
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
```

### SessionEvent

【源码确认】`agentscope-core/io/agentscope/core/session/SessionEvent.java:23-50`

```java
public record SessionEvent(
        int schemaVersion,
        String eventId,
        long seq,
        long occurredAt,
        String type,
        String executionRunId,
        String turnId,
        boolean required,
        String payloadJson) {
    public SessionEvent {
        if (schemaVersion != 1 || seq < 1)
            throw new IllegalArgumentException("Unsupported event envelope");
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(payloadJson);
        JsonUtils.getJsonCodec().fromJson(payloadJson, Object.class);
    }

    public <T> T payload(Class<T> type) {
        return JsonUtils.getJsonCodec().fromJson(payloadJson, type);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> data() {
        return payload(Map.class);
    }
}
```

### SessionHeader

【源码确认】`agentscope-core/io/agentscope/core/session/SessionHeader.java:19-30`

```java
public record SessionHeader(
        int formatVersion, String authority, String replayCoverage, long createdAt) {
    public static SessionHeader current() {
        return new SessionHeader(1, "event_log", "adapter_chunks", System.currentTimeMillis());
    }

    public void validate() {
        if (formatVersion != 1 || !"event_log".equals(authority))
            throw new SessionLogException("Unsupported session format/authority");
    }
}
```

### SessionLog

【源码确认】`agentscope-core/io/agentscope/core/session/SessionLog.java:29-31`

```java
    record Head(long seq, long epoch, String owner, long leaseUntil, String commitId) {}

    record Writer(String owner, long epoch) {}
```

### JournalSessionLog

【源码确认】`agentscope-core/io/agentscope/core/session/JournalSessionLog.java:41-48`

```java
    public record Commit(
            String batchId,
            String parent,
            long firstSeq,
            long lastSeq,
            String hash,
            List<SessionEvent> events,
            Map<String, String> payloadBlobs) {}
```

### SessionInbox

【源码确认】`agentscope-core/io/agentscope/core/session/SessionInbox.java:33-52`

```java
    public record Command(
            String id, String kind, String turnId, List<Msg> messages, List<String> requestIds) {
        public Command {
            Objects.requireNonNull(id);
            Objects.requireNonNull(kind);
            messages = List.copyOf(messages);
            requestIds = List.copyOf(requestIds);
        }

        public boolean execution() {
            return kind.equals("submit") || kind.equals("resume") || kind.equals("respond");
        }
    }

    public record Snapshot(
            List<Command> commands,
            String turnId,
            String runId,
            Map<String, String> rejected,
            Set<String> handled) {}
```

## Journal 物理对象

【源码确认】`JournalSessionLog.path/bytes/parse`（60–75），所有控制对象通过 JsonUtils.getJsonCodec().toJson 转 UTF-8；payloadJson 是事件字段内的 JSON 字符串，而非自动展开的对象。

|相对于 SessionKey 路径|内容|更新|
|---|---|---|
|session.json|SessionHeader|expectedVersion=0 创建|
|head.json|SessionLog.Head|CAS 覆盖|
|commits/<SHA256(batchId UTF8)>.json|Commit|不可变 创建时CAS|
|blobs/<SHA256(payload UTF8)>|原始 payload UTF8 bytes|不可变 创建时CAS|
|writer-seals/<SHA256(owner UTF8)>.json|owner 对象|不可变创建|
|exports/<SHA256(sink UTF8)>.json|JSON long 游标|CAS 单调推进|
|inbox/ 下的同类对象|独立输入日志|独立 Head Writer seq|

【源码确认】Commit 的 parent 是上一 commitId；hash 是原始完整 events 的序列化 SHA256。payload UTF8 大于 65536 字节才提取为 blob，Commit 内事件 payloadJson 替换为 `{}`，payloadBlobs 以 eventId 映射 blob hash。读取时补全并校验 blob 和完整事件 hash。证据：JournalSessionLog.commit/readCommit:193–304。

【源码确认】Head.seq 是已提交事件序号；epoch 是 Writer fencing 代际；leaseUntil 为毫秒截止时间；commitId 是可见链头。AtomicSessionStorage.Value.version 是底层 CAS 版本，与 epoch 和 seq 不同。证据：SessionLog.Head、AtomicSessionStorage.Value，JournalSessionLog.acquire:106–142。

## Inbox 和状态内容

【源码确认】SessionInbox.snapshot:64–96 扫描独立日志，折叠 accepted/opened/closed/rejected/handled；没有单独的数字消费游标，消费依据来自事件集合。Transaction.accept:134–145 按 command.id 幂等，相同 ID 不同输入报错；比较时忽略 message.timestamp。每个 Inbox 事件立即提交，独立 lease 为两分钟，获取冲突最多重试 100 次、间隔 10ms。证据 SessionInbox.acquire/Transaction:108–210。

【源码确认】SessionRecorder.captureState:320–387 持久化 message/user、message/assistant、message/system、tool/result，工具调用另外产生 tool/requested。完整 AgentState.toJson() 放入 state/checkpoint 的 stateJson 字符串，带 projectorVersion=1、reason。任务、计划、权限变化还产生 task/changed、plan/changed、permission/changed。这是 Session Log 里的 checkpoint，不等于调用 AgentStateStore。

【源码确认】上述 AtomicSessionStorage 接口只有 read/listPaths/compareAndSet，没有删除能力；SessionLog 没有 delete；不能声称 SDK 原生日志支持级联删除。WorkspaceSessionLogStore.list:54–72 按 session.json 标记发现（也识别仅有 inbox 标记的会话），按调用 userId 过滤，SESSION namespace 的发现范围受限。open:90–93 只是构造 JournalSessionLog，不写 header；第一次 acquire 才落地标记。

【源码确认】AgentStateStore、Snapshot 与 Workspace 普通文件的字段和目录在其他专题核对，不应从 Journal 的对象布局推断。

## 真实运行数据

本次无模型 Demo 的真实 AgentState.toJson 和 Journal 对象见 [runtime-samples.json](runtime-samples.json)。同 batch 重试得到 idempotentSeq=1，重新构造存储后得到 reopenedEvents=1。journalObjects 中 storageVersion 是本地对象八字节 long 前缀，value 是其后 JSON 的解析结果；展示包装不是 Journal 原生字段。AgentStateStore、Snapshot 与 Workspace 的结构见 [扩展专题](storage-extension.md)，默认装配见 [架构专题](storage-architecture.md)。后续已补SIGKILL与三个独立JVM实验、JDBC/H2官方测试和本地Snapshot往返，见 [运行证据](validation-evidence.md)。未运行断电、远程服务故障和当前HTTP模型存储全链路。

## 非Journal数据族与真实样例

| 数据族 | 物理值与寻址 | 序列化及恢复 | 更新与并发 | 删除边界 |
|---|---|---|---|---|
| 独立AgentState | 文件实现user/session/key.json；JDBC State表slotId/state_key/item_index；Redis独立slot键 | AgentState.toJson/fromJsonString使用JsonUtils；字段见下方真实样例 | 单值全量；版本支持因实现而异 | 各StateStore删除范围不同，不等于删除Journal |
| Workspace普通远程文件 | BaseStore(namespace,filePath)，FileData映射content/encoding/created_at/modified_at | RemoteFilesystem文件值转换，读取按编码还原 | write创建CAS0；edit版本CAS；upload覆盖 | 目录按前缀逐项删除，move复制后删，无全目录事务 |
| SandboxSnapshot | local basePath/id.tar；远程client按snapshotId映射 | descriptor是JSON；内容是调用方提供的tar流 | 全量归档，不是增量文件日志 | 通用接口无删除/TTL，具体Redis客户端可配TTL |
| Admin SnapshotStore | 内存sessionId→undo/redo Deque<String> | AgentState JSON字符串，无独立JSON信封 | 本JVM同步与容量裁剪 | clear清除会话；重启自然丢失 |

【源码确认】精确代码路径、方法行号与各实现差异见 [扩展逐题核对](storage-extension-question-audit.md) 和 [后端实现清单](storage-backend-inventory.md)。上述JDBC slotId采用user/session组合编码，不应把存储列session_id误当不含用户的原始sessionId。

【运行验证】完整AgentState实际JSON见 [runtime-samples.json](runtime-samples.json) 的agentState：session_id(String)、user_id(String)、summary(String)、context(List<Msg>)、reply_id(String)、cur_iter(int)、shutdown_interrupted(boolean)、permission_context(PermissionContextState)、tool_context(ToolContextState)、tasks_context(TaskContextState)、plan_mode_context(PlanModeContextState)。嵌套对象的实际全部默认字段保留在样例中，样例不是任意版本的schema承诺。源码为`agentscope-core/src/main/java/io/agentscope/core/state/AgentState.java:45-148`及其引用的各State类。

【运行验证】Snapshot实际descriptor JSON和tar条目见 [snapshot-probe-results.json](snapshot-probe-results.json)；Inbox完整Command、消息块及提交对象见 [inbox-probe-results.json](inbox-probe-results.json)。样例中的JSON包装字段仅供展示，不是SDK新增格式。
