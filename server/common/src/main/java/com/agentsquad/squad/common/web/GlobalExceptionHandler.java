package com.agentsquad.squad.common.web;

import com.agentsquad.squad.common.exceptions.ExceptionResolver;
import com.agentsquad.squad.common.model.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * 统一异常出口
 * <p>
 * MVC接口和过滤器复用同一异常规则，Gateway保留协议适配但复用common映射
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @Resource
    private ObjectMapper mapper;

    /**
     * 处理接口异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handle(Exception exception) {
        var error = ExceptionResolver.resolve(exception);
        return ResponseEntity.status(error.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store")
                .body(ApiResult.error(error.getCode(), error.getMessage()));
    }

    /**
     * 处理过滤异常
     */
    public void write(HttpServletResponse response, Exception exception) throws IOException {
        var error = ExceptionResolver.resolve(exception);
        response.setStatus(error.getStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), ApiResult.error(error.getCode(), error.getMessage()));
    }
}
