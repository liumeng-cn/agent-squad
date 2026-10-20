package com.agentsquad.squad.runtime.d1;

import com.agentsquad.squad.runtime.command.CommandStatus;
import com.agentsquad.squad.runtime.command.DurableCommand;
import com.agentsquad.squad.runtime.command.FileCommandStore;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D1 离线运行存储，集中维护身份、会话、目标和命令的最小关联。
 *
 * <p>它只用于让 HTTP 纵向切片可运行；D1 接入 MyBatis 后，事务边界必须迁移到数据库，
 * 并在同一事务内写入任务、绑定、命令和首个事件。</p>
 */
@Component
public class D1RuntimeStore {

    private final Map<String, D1Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, D1Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<D1TaskEvent>> events = new ConcurrentHashMap<>();
    private final FileCommandStore commandStore;

    public D1RuntimeStore() {
        this(Path.of("target/d1-commands.log"));
    }

    D1RuntimeStore(Path commandFile) {
        this.commandStore = new FileCommandStore(commandFile);
    }

    /** 创建归属于用户的会话。 */
    public D1Conversation createConversation(String userId, String title) {
        D1Conversation conversation = new D1Conversation(UUID.randomUUID().toString(), userId, title);
        conversations.put(conversation.id(), conversation);
        return conversation;
    }

    /** 创建一个用户目标并持久受理其 SUBMIT 命令。 */
    public synchronized D1Task createTask(String userId, String conversationId, String message) throws IOException {
        D1Conversation conversation = requireConversation(userId, conversationId);
        if (conversation.activeTaskId() != null) {
            throw new IllegalStateException("会话已有未完成目标");
        }
        String taskId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();
        DurableCommand command = commandStore.accept(commandId, message);
        D1Task task = new D1Task(taskId, conversationId, userId, message, command.commandId(), "QUEUED");
        tasks.put(taskId, task);
        conversation.setActiveTaskId(taskId);
        events.put(taskId, new ArrayList<>(List.of(
                new D1TaskEvent(1, taskId, "TASK_ACCEPTED", "目标已持久受理", Instant.now())
        )));
        return task;
    }

    /** 按用户归属读取任务，避免仅凭 taskId 越权访问。 */
    public D1Task requireTask(String userId, String taskId) {
        D1Task task = tasks.get(taskId);
        if (task == null || !task.userId().equals(userId)) {
            throw new IllegalArgumentException("任务不存在");
        }
        return task;
    }

    /** 按任务归属返回稳定顺序的公开事件快照。 */
    public List<D1TaskEvent> events(String userId, String taskId) {
        requireTask(userId, taskId);
        return List.copyOf(events.getOrDefault(taskId, List.of()));
    }

    /** 返回命令收据，D1 中会改为查询 sq_command。 */
    public DurableCommand requireCommand(String commandId) throws IOException {
        return commandStore.pending().stream()
                .filter(command -> command.commandId().equals(commandId))
                .findFirst()
                .orElse(new DurableCommand(commandId, "", CommandStatus.EXECUTED));
    }

    private D1Conversation requireConversation(String userId, String conversationId) {
        D1Conversation conversation = conversations.get(conversationId);
        if (conversation == null || !conversation.userId().equals(userId)) {
            throw new IllegalArgumentException("会话不存在");
        }
        return conversation;
    }
}
