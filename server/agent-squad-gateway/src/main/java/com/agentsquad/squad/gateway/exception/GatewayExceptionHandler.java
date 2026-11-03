package com.agentsquad.squad.gateway.exception;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证与账号接口的统一错误出口。
 *
 * <p>状态码由异常类型决定，响应体只包含可理解原因；认证相关响应禁止缓存。</p>
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ErrorView> handleUnauthenticated(AuthenticationRequiredException exception) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(PasswordChangeRequiredException.class)
    public ResponseEntity<ErrorView> handlePasswordChangeRequired(PasswordChangeRequiredException exception) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ErrorView> handlePermissionDenied(PermissionDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorView> handleNotFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(StateConflictException.class)
    public ResponseEntity<ErrorView> handleConflict(StateConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorView> handleInvalidRequest(InvalidRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ErrorView> handleValidation(ValidationFailedException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ErrorView> handleRateLimited(RateLimitedException exception) {
        return response(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }

    private static ResponseEntity<ErrorView> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new ErrorView(message));
    }
}
