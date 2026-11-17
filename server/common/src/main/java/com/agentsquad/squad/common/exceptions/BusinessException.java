package com.agentsquad.squad.common.exceptions;

import com.agentsquad.squad.common.enums.ApiCode;
import lombok.Getter;

/**
 * 业务拒绝异常
 * <p>
 * 所属common模块，按明确职责维护用户身份与数据边界。
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ApiCode code;

    public BusinessException(ApiCode code, String message) {
        super(message);
        this.code = code;
    }
}
