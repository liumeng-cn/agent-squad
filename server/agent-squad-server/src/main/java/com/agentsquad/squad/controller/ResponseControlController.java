package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.identity.UserContext;
import com.agentsquad.squad.model.dto.ControlDTO;
import com.agentsquad.squad.model.vo.CommandReceiptVO;
import com.agentsquad.squad.model.vo.MessageReceiptVO;
import com.agentsquad.squad.service.ResponseControlService;
import com.agentsquad.squad.service.ResponseRetryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 停止指定回答的接口
 * <p>
 * 身份来自Cookie与CSRF，版本和幂等键保护重复点击；独立目标不受本请求影响。
 */
@RestController
public class ResponseControlController {
    @Resource
    private ResponseRetryService retries;

    @Resource
    private ResponseControlService controls;

    @PostMapping("/api/v1/executions/{executionId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<CommandReceiptVO> cancel(HttpServletRequest servletRequest, @PathVariable Long executionId,
                                              @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ControlDTO request) {
        return ApiResult.success(controls.cancel(UserContext.current(servletRequest).userId(), executionId, key, request.expectedVersion()));
    }

    @PostMapping("/api/v1/executions/{executionId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<MessageReceiptVO> retry(HttpServletRequest servletRequest, @PathVariable Long executionId,
                                             @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ControlDTO request) {
        return ApiResult.success(retries.retry(UserContext.current(servletRequest).userId(), executionId, key, request.expectedVersion()));
    }
}
