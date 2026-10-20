package com.agentsquad.squad.runtime.d1;

import com.agentsquad.squad.identity.D1AuthService;
import com.agentsquad.squad.runtime.command.DurableCommand;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.List;

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

    @Resource
    private D1AuthService authService;

    /** 创建会话，返回后续任务归属所需的 conversationId。 */
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public D1Conversation createConversation(
            @RequestHeader(value = "X-D1-User", required = false) String headerUserId,
            @CookieValue(value = "SQUAD_SESSION", required = false) String sessionToken,
            @RequestBody D1Request request) {
        return runtimeStore.createConversation(resolveUserId(headerUserId, sessionToken), request.title());
    }

    /** 先持久受理目标，再返回 202 语义的任务收据。 */
    @PostMapping("/conversations/{conversationId}/tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public D1Task createTask(
            @RequestHeader(value = "X-D1-User", required = false) String headerUserId,
            @CookieValue(value = "SQUAD_SESSION", required = false) String sessionToken,
            @PathVariable String conversationId,
            @RequestBody D1Request request) throws IOException {
        return runtimeStore.createTask(resolveUserId(headerUserId, sessionToken), conversationId, request.message());
    }

    /** 查询任务，读取时再次校验用户归属。 */
    @GetMapping("/tasks/{taskId}")
    public D1Task getTask(
            @RequestHeader(value = "X-D1-User", required = false) String headerUserId,
            @CookieValue(value = "SQUAD_SESSION", required = false) String sessionToken,
            @PathVariable String taskId) {
        return runtimeStore.requireTask(resolveUserId(headerUserId, sessionToken), taskId);
    }

    /** 返回任务事件快照，前端可用最后序号建立后续增量游标。 */
    @GetMapping("/tasks/{taskId}/events")
    public List<D1TaskEvent> getEvents(
            @RequestHeader(value = "X-D1-User", required = false) String headerUserId,
            @CookieValue(value = "SQUAD_SESSION", required = false) String sessionToken,
            @PathVariable String taskId) {
        return runtimeStore.events(resolveUserId(headerUserId, sessionToken), taskId);
    }

    /** 查询命令收据，供断线重连和重复提交方确认受理结果。 */
    @GetMapping("/commands/{commandId}")
    public Map<String, Object> getCommand(@PathVariable String commandId) throws IOException {
        DurableCommand command = runtimeStore.requireCommand(commandId);
        return Map.of("commandId", command.commandId(), "status", command.status().name());
    }

    /** 优先使用认证 Cookie，Header 仅用于离线探针和开发调试。 */
    private String resolveUserId(String headerUserId, String sessionToken) {
        if (sessionToken != null && !sessionToken.isBlank()) {
            return authService.authenticate(sessionToken).userId();
        }
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId;
        }
        throw new IllegalArgumentException("登录已失效");
    }
}
