package com.agentsquad.squad.gateway.exceptions;

import com.agentsquad.squad.enums.ApiCode;
import com.agentsquad.squad.common.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 网关接口统一错误出口，所属 gateway 模块基础架构
 * 业务拒绝使用 HTTP 200，响应体用 ApiResult 包装；具体业务异常映射随业务迁入后补充
 */
@Slf4j
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception exception) {
        log.error("网关未处理异常", exception);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResult.error(ApiCode.INTERNAL_ERROR));
    }
}