package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.identity.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import com.agentsquad.squad.worker.service.WorkerDefinitionService;
import com.agentsquad.squad.domain.dto.WorkerDefinitionCreateRequest;
import com.agentsquad.squad.domain.dto.WorkerDefinitionPage;
import com.agentsquad.squad.domain.dto.WorkerDefinitionResponse;
import com.agentsquad.squad.domain.dto.WorkerStatusUpdateRequest;
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
    private WorkerDefinitionService definitionService;

    /**
     * 发布不可变的 Worker 能力版本。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<WorkerDefinitionResponse> publish(
            HttpServletRequest servletRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WorkerDefinitionCreateRequest request) {
        return ApiResult.success(definitionService.publish(UserContext.current(servletRequest),
                idempotencyKey, request));
    }

    /**
     * 查询已发布能力，不触发 Worker 执行。
     */
    @GetMapping
    public ApiResult<WorkerDefinitionPage> list(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        UserContext.current(servletRequest);
        return ApiResult.success(definitionService.list(cursor, limit));
    }

    /**
     * 按状态版本启停或撤销能力，避免并发管理决定互相覆盖。
     */
    @PatchMapping("/{definitionId}")
    public ApiResult<WorkerDefinitionResponse> changeStatus(
            HttpServletRequest servletRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String definitionId,
            @Valid @RequestBody WorkerStatusUpdateRequest request) {
        return ApiResult.success(definitionService.changeStatus(UserContext.current(servletRequest),
                definitionId, idempotencyKey, request));
    }
}
