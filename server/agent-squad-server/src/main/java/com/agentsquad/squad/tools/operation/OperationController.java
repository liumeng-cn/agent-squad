package com.agentsquad.squad.tools.operation;

import com.agentsquad.squad.identity.AuthService;
import com.agentsquad.squad.tools.operation.dto.OperationPage;
import com.agentsquad.squad.tools.operation.dto.OperationReconcileRequest;
import com.agentsquad.squad.tools.operation.dto.OperationView;
import com.agentsquad.squad.tools.operation.dto.OperationDetail;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作账本 HTTP 边界。查询公开给属主；核对是唯一的账本状态人工入口。
 */
@RestController
@RequestMapping("/api/v1")
public class OperationController {

    @Resource
    private AuthService authService;

    @Resource
    private OperationService operationService;

    @Resource
    private OperationReconcileService operationReconcileService;

    /**
     * 列出当前用户目标的操作账本；用于操作详情列表与核对。
     */
    @GetMapping("/tasks/{taskId}/operations")
    public OperationPage listByTask(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @PathVariable String taskId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return operationService.listPage(authService.authenticate(token).userId(),
                taskId, cursor, limit);
    }

    /**
     * 读取当前用户单个操作；含参数摘要、确认摘要与执行结果。
     */
    @GetMapping("/operations/{operationId}")
    public OperationDetail getOwned(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @PathVariable String operationId) {
        return operationService.detail(authService.authenticate(token), operationId);
    }

    /**
     * 核对 UNKNOWN 操作；结论与证据落账本，迟到执行器结果无法覆盖。
     */
    @PostMapping("/operations/{operationId}/reconcile")
    @ResponseStatus(HttpStatus.OK)
    public OperationView reconcile(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfToken,
            @PathVariable String operationId,
            @Valid @RequestBody OperationReconcileRequest request) {
        return operationReconcileService.reconcile(
                authService.authenticateWrite(token, csrfToken), operationId, request);
    }
}
