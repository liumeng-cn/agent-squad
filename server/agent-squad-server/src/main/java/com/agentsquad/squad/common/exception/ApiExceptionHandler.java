package com.agentsquad.squad.common.exception;

import com.agentsquad.squad.common.model.ApiCode;
import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.observability.ObservationSupport;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * 将身份和业务错误统一转换为 HTTP 状态，不向客户端返回数据库或模型异常细节。
 * <p>
 * 未单独处理的异常只记录受限字段与错误分类，响应体保持通用提示。
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @Resource
    private ObservationSupport observations;

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiResult<Void>> unauthenticated(AuthenticationRequiredException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResult.error(ApiCode.UNAUTHORIZED, exception.getMessage()));
    }

    @ExceptionHandler(RuntimeBusinessException.class)
    public ResponseEntity<ApiResult<Void>> businessError(RuntimeBusinessException exception) {
        HttpStatus status = switch (exception.code()) {
            case EXPIRED -> HttpStatus.GONE;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT -> HttpStatus.CONFLICT;
            case VALIDATION -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ApiResult.error(ApiCode.fromCode(status.value()), exception.getMessage()));
    }

    /**
     * 处理参数校验与请求体不可读，保持与框架默认一致的 400 响应。
     */
    @ExceptionHandler({ MethodArgumentNotValidException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class })
    public ResponseEntity<ApiResult<Void>> validationError(Exception exception, HttpServletRequest request) {
        log.warn("请求参数错误 {}", observations.logFields(Map.of(
                "method", request.getMethod(), "path", request.getRequestURI(),
                "statusCode", HttpStatus.BAD_REQUEST.value(), "errorType", observations.classify(exception))));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.error(ApiCode.BAD_REQUEST));
    }

    /**
     * 兜底处理未单独声明的异常；框架已给出状态码时沿用，否则按服务端错误返回。
     * 4xx 客户端错误只记录 WARN，避免未认证等正常情况刷 ERROR。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> unexpected(Exception exception, HttpServletRequest request) {
        int statusCode = exception instanceof ErrorResponse response
                ? response.getStatusCode().value() : HttpStatus.INTERNAL_SERVER_ERROR.value();
        if (statusCode >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("请求处理失败 {}", observations.logFields(Map.of(
                    "method", request.getMethod(), "path", request.getRequestURI(),
                    "statusCode", statusCode, "errorType", observations.classify(exception))));
        } else {
            log.warn("请求处理失败 {}", observations.logFields(Map.of(
                    "method", request.getMethod(), "path", request.getRequestURI(),
                    "statusCode", statusCode, "errorType", observations.classify(exception))));
        }
        return ResponseEntity.status(statusCode)
                .body(ApiResult.error(ApiCode.fromCode(statusCode)));
    }
}
