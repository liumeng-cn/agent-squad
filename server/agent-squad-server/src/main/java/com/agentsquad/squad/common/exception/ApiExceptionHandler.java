package com.agentsquad.squad.common.exception;

import com.agentsquad.squad.common.model.ApiCode;
import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.observability.ObservationSupport;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * 将身份和业务错误统一转换为 ApiResult 业务码，不向客户端返回数据库或模型异常细节。
 * <p>
 * 未单独处理的异常只记录受限字段与错误分类，响应体保持通用提示。
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @Resource
    private ObservationSupport observations;

    /** 浏览器断开后的异步响应已不可写；结束请求，不包装JSON或记录业务失败。 */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void disconnected() {
        log.debug("页面异步连接已断开");
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiResult<Void>> unauthenticated(AuthenticationRequiredException exception) {
        return ResponseEntity.ok()
                .body(ApiResult.error(ApiCode.UNAUTHORIZED, exception.getMessage()));
    }

    @ExceptionHandler(RuntimeBusinessException.class)
    public ResponseEntity<ApiResult<Void>> businessError(RuntimeBusinessException exception) {
        ApiCode code = switch (exception.code()) {
            case EXPIRED -> ApiCode.GONE;
            case NOT_FOUND -> ApiCode.NOT_FOUND;
            case FORBIDDEN -> ApiCode.FORBIDDEN;
            case CONFLICT -> ApiCode.CONFLICT;
            case VALIDATION -> ApiCode.BAD_REQUEST;
        };
        return ResponseEntity.ok()
                .body(ApiResult.error(code, exception.getMessage()));
    }

    /**
     * 已识别的业务拒绝使用 HTTP 200，具体原因由响应业务码表达。
     */
    @ExceptionHandler({ AuthConflictException.class, AuthForbiddenException.class, InvalidRequestException.class,
            PasswordChangeRequiredException.class, PermissionDeniedException.class, RateLimitedException.class,
            ResourceNotFoundException.class, StateConflictException.class, ValidationFailedException.class })
    public ResponseEntity<ApiResult<Void>> rejected(RuntimeException exception) {
        ApiCode code;
        if (exception instanceof AuthConflictException || exception instanceof StateConflictException) {
            code = ApiCode.CONFLICT;
        } else if (exception instanceof AuthForbiddenException || exception instanceof PermissionDeniedException
                || exception instanceof PasswordChangeRequiredException) {
            code = ApiCode.FORBIDDEN;
        } else if (exception instanceof ResourceNotFoundException) {
            code = ApiCode.NOT_FOUND;
        } else if (exception instanceof RateLimitedException) {
            code = ApiCode.TOO_MANY_REQUESTS;
        } else if (exception instanceof ValidationFailedException) {
            code = ApiCode.VALIDATION_FAILED;
        } else {
            code = ApiCode.BAD_REQUEST;
        }
        return ResponseEntity.ok(ApiResult.error(code, exception.getMessage()));
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
        if (isEventStream(request)) {
            // 事件流已按 text/event-stream 协商，再写 JSON 会二次转换失败。
            return ResponseEntity.status(statusCode).build();
        }
        return ResponseEntity.status(statusCode)
                .body(ApiResult.error(ApiCode.fromCode(statusCode)));
    }

    private boolean isEventStream(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}
