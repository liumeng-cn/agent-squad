package com.agentsquad.squad.exceptions;

import com.agentsquad.squad.exceptions.*;
import com.agentsquad.squad.enums.ApiCode;
import com.agentsquad.squad.common.model.ApiResult;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证与账号接口的统一错误出口。
 *
 * <p>业务码由异常类型决定，业务拒绝使用 HTTP 200，响应体使用 {@link ApiResult} 包装；认证相关响应禁止缓存。</p>
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiResult<Void>> handleUnauthenticated(AuthenticationRequiredException exception) {
        return response(ApiCode.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(PasswordChangeRequiredException.class)
    public ResponseEntity<ApiResult<Void>> handlePasswordChangeRequired(PasswordChangeRequiredException exception) {
        return response(ApiCode.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler({PermissionDeniedException.class, AuthForbiddenException.class})
    public ResponseEntity<ApiResult<Void>> handlePermissionDenied(RuntimeException exception) {
        return response(ApiCode.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNotFound(ResourceNotFoundException exception) {
        return response(ApiCode.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({StateConflictException.class, AuthConflictException.class})
    public ResponseEntity<ApiResult<Void>> handleConflict(RuntimeException exception) {
        return response(ApiCode.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResult<Void>> handleInvalidRequest(InvalidRequestException exception) {
        return response(ApiCode.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(ValidationFailedException exception) {
        return response(ApiCode.VALIDATION_FAILED, exception.getMessage());
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ApiResult<Void>> handleRateLimited(RateLimitedException exception) {
        return response(ApiCode.TOO_MANY_REQUESTS, exception.getMessage());
    }

    private static ResponseEntity<ApiResult<Void>> response(ApiCode apiCode, String message) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResult.error(apiCode, message));
    }
}
