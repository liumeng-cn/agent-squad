package com.agentsquad.squad.worker;

import com.agentsquad.squad.identity.AuthService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionCreateRequest;
import com.agentsquad.squad.worker.dto.WorkerDefinitionPage;
import com.agentsquad.squad.worker.dto.WorkerDefinitionResponse;
import com.agentsquad.squad.worker.dto.WorkerStatusUpdateRequest;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Worker 能力版本的 HTTP 入口。
 * <p>
 * 认证与 CSRF 在入口校验，发布和状态规则交由应用服务处理。
 */
@RestController
@RequestMapping("/api/v1/worker-definitions")
public class WorkerDefinitionController {

    @Resource
    private AuthService authService;

    @Resource
    private WorkerDefinitionService definitionService;

    /**
     * 发布不可变的 Worker 能力版本。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkerDefinitionResponse publish(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfToken,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WorkerDefinitionCreateRequest request) {
        return definitionService.publish(authService.authenticateWrite(token, csrfToken),
                idempotencyKey, request);
    }

    /**
     * 查询已发布能力，不触发 Worker 执行。
     */
    @GetMapping
    public WorkerDefinitionPage list(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        authService.authenticate(token);
        return definitionService.list(cursor, limit);
    }

    /**
     * 按状态版本启停或撤销能力，避免并发管理决定互相覆盖。
     */
    @PatchMapping("/{definitionId}")
    public WorkerDefinitionResponse changeStatus(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfToken,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String definitionId,
            @Valid @RequestBody WorkerStatusUpdateRequest request) {
        return definitionService.changeStatus(authService.authenticateWrite(token, csrfToken),
                definitionId, idempotencyKey, request);
    }
}
