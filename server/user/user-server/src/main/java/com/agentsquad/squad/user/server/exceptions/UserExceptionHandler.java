package com.agentsquad.squad.user.server.exceptions;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.model.ApiResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 用户接口业务异常转换
 * <p>
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@RestControllerAdvice
public class UserExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> business(BusinessException exception) {
        return ApiResult.error(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> invalid(MethodArgumentNotValidException exception) {
        return ApiResult.error(ApiCode.BAD_REQUEST, "请检查手机号、验证码或昵称");
    }
}
