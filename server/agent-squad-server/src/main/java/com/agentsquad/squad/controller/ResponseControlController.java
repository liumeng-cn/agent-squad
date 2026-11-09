package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.vo.CommandReceiptVO;
import com.agentsquad.squad.dto.ControlDTO;
import com.agentsquad.squad.vo.MessageReceiptVO;
import com.agentsquad.squad.service.ResponseRetryService;
import com.agentsquad.squad.service.ResponseControlService;
import com.agentsquad.squad.identity.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResult<CommandReceiptVO> cancel(HttpServletRequest servletRequest, @PathVariable String executionId,
                                            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ControlDTO request) {
        return ApiResult.success(controls.cancel(UserContext.current(servletRequest).userId(), executionId, key, request.expectedVersion()));
    }

    @PostMapping("/api/v1/executions/{executionId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<MessageReceiptVO> retry(HttpServletRequest servletRequest, @PathVariable String executionId,
                                           @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ControlDTO request) {
        return ApiResult.success(retries.retry(UserContext.current(servletRequest).userId(), executionId, key, request.expectedVersion()));
    }
}
