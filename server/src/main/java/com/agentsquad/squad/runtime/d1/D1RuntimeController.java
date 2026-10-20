package com.agentsquad.squad.runtime.d1;

import com.agentsquad.squad.runtime.command.DurableCommand;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * D1 运行入口，仅负责 HTTP 协议和身份头解析，不编排模型循环。
 *
 * <p>当前用 X-D1-User 作为离线探针身份；正式实现替换为登录会话校验，并保持 Service 层边界。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class D1RuntimeController {

    @Resource
    private D1RuntimeStore runtimeStore;

    /** 创建会话，返回后续任务归属所需的 conversationId。 */
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public D1Conversation createConversation(
            @RequestHeader("X-D1-User") String userId,
            @RequestBody D1Request request) {
        return runtimeStore.createConversation(userId, request.title());
    }

    /** 先持久受理目标，再返回 202 语义的任务收据。 */
    @PostMapping("/conversations/{conversationId}/tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public D1Task createTask(
            @RequestHeader("X-D1-User") String userId,
            @PathVariable String conversationId,
            @RequestBody D1Request request) throws IOException {
        return runtimeStore.createTask(userId, conversationId, request.message());
    }

    /** 查询任务，读取时再次校验用户归属。 */
    @GetMapping("/tasks/{taskId}")
    public D1Task getTask(@RequestHeader("X-D1-User") String userId, @PathVariable String taskId) {
        return runtimeStore.requireTask(userId, taskId);
    }

    /** 查询命令收据，供断线重连和重复提交方确认受理结果。 */
    @GetMapping("/commands/{commandId}")
    public Map<String, Object> getCommand(@PathVariable String commandId) throws IOException {
        DurableCommand command = runtimeStore.requireCommand(commandId);
        return Map.of("commandId", command.commandId(), "status", command.status().name());
    }
}
