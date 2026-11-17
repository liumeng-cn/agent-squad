package com.agentsquad.squad.common.exceptions;

import com.agentsquad.squad.common.bo.ApiErrorBO;
import com.agentsquad.squad.common.enums.ApiCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

/**
 * 统一异常映射
 * <p>
 * 异常定义和响应规则集中于common，业务模块只抛出异常，不各自构造错误JSON
 */
public final class ExceptionResolver {
    private ExceptionResolver() {
    }

    /**
     * 统一异常映射
     */
    public static ApiErrorBO resolve(Throwable exception) {
        ApiErrorBO result = new ApiErrorBO();
        result.setCode(ApiCode.SERVICE_UNAVAILABLE);
        result.setStatus(503);
        result.setMessage(ApiCode.SERVICE_UNAVAILABLE.getMsg());
        if (exception instanceof BusinessException business) {
            result.setCode(business.getCode());
            result.setMessage(business.getMessage());
            result.setStatus(business.getCode() == ApiCode.UNAUTHORIZED ? 401 : business.getCode() == ApiCode.FORBIDDEN ? 403 : 200);
        } else if (exception instanceof BindException
                || exception instanceof ConstraintViolationException || exception instanceof IllegalArgumentException
                || exception instanceof ServerWebInputException) {
            result.setCode(ApiCode.BAD_REQUEST);
            result.setStatus(200);
            result.setMessage("请求参数格式无效");
        } else if (exception instanceof ResponseStatusException response) {
            int status = response.getStatusCode().value();
            result.setStatus(status);
            result.setCode(ApiCode.fromCode(status));
            result.setMessage(result.getCode().getMsg());
        }
        return result;
    }
}
