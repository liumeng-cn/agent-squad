package com.agentsquad.squad.controller;

import com.agentsquad.squad.service.OperationService;

import com.agentsquad.squad.service.OperationReconcileService;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.identity.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import com.agentsquad.squad.vo.OperationPageVO;
import com.agentsquad.squad.dto.OperationReconcileDTO;
import com.agentsquad.squad.vo.OperationVO;
import com.agentsquad.squad.vo.OperationDetailVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作账本接口。查询公开给属主；核对是唯一的账本状态人工入口
 */
@RestController
@RequestMapping("/api/v1")
public class OperationController {

    @Resource
    private OperationService operationService;

    @Resource
    private OperationReconcileService operationReconcileService;

    /**
     * 列出当前用户目标的操作账本；用于操作详情列表与核对。
     */
    @GetMapping("/tasks/{taskId}/operations")
    public ApiResult<OperationPageVO> listByTask(
            HttpServletRequest servletRequest,
            @PathVariable String taskId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.success(operationService.listPage(UserContext.current(servletRequest).userId(),
                taskId, cursor, limit));
    }

    /**
     * 读取当前用户单个操作；含参数摘要、确认摘要与执行结果。
     */
    @GetMapping("/operations/{operationId}")
    public ApiResult<OperationDetailVO> getOwned(
            HttpServletRequest servletRequest,
            @PathVariable String operationId) {
        return ApiResult.success(operationService.detail(UserContext.current(servletRequest), operationId));
    }

    /**
     * 核对 UNKNOWN 操作；结论与证据落账本，迟到执行器结果无法覆盖。
     */
    @PostMapping("/operations/{operationId}/reconcile")
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<OperationVO> reconcile(
            HttpServletRequest servletRequest,
            @PathVariable String operationId,
            @Valid @RequestBody OperationReconcileDTO request) {
        return ApiResult.success(operationReconcileService.reconcile(
                UserContext.current(servletRequest), operationId, request));
    }
}
