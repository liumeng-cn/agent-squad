package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.enums.ApiCode;
import com.agentsquad.squad.v2.enums.NativeChatStateRejection;
import com.agentsquad.squad.v2.enums.NativeSteeringRejection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 聊天业务拒绝转换
 * <p>
 * 将明确的原生状态拒绝转换为业务码，不吞掉存储或模型基础设施故障。
 */
@RestControllerAdvice(assignableTypes = ChatController.class)
public class ChatExceptionHandler {
    /**
     * 原生状态拒绝使用业务响应，客户端不自动重提结果未知的输入。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResult<Void>> stateConflict(IllegalStateException exception) {
        var reason = NativeChatStateRejection.fromMessage(exception.getMessage());
        if (reason == NativeChatStateRejection.MODEL_DISABLED || reason == NativeChatStateRejection.UNKNOWN
                && NativeSteeringRejection.fromMessage(exception.getMessage()) == NativeSteeringRejection.UNKNOWN) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResult.error(ApiCode.SERVICE_UNAVAILABLE, reason.getDesc()));
        }
        return ResponseEntity.ok(ApiResult.error(ApiCode.CONFLICT, reason.getDesc()));
    }

    /**
     * 同一幂等键复用不同输入是明确业务拒绝。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Void> invalidInput(IllegalArgumentException exception) {
        return ApiResult.error(ApiCode.BAD_REQUEST, "输入或交互标识无效，请检查后重试");
    }
}
