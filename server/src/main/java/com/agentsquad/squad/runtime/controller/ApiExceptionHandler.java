package com.agentsquad.squad.runtime.controller;

import com.agentsquad.squad.identity.AuthenticationRequiredException;
import com.agentsquad.squad.runtime.service.RuntimeBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 将身份和业务错误统一转换为 HTTP 状态，不向客户端返回数据库或模型异常细节。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<Map<String, String>> unauthenticated(AuthenticationRequiredException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(RuntimeBusinessException.class)
    public ResponseEntity<Map<String, String>> businessError(RuntimeBusinessException exception) {
        HttpStatus status = switch (exception.code()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case VALIDATION -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("message", exception.getMessage()));
    }
}
