package com.agentsquad.squad.common.bo;

import com.agentsquad.squad.common.enums.ApiCode;
import lombok.Data;

/**
 * 统一异常结果
 * <p>
 * MVC和Gateway使用同一份异常映射结果，不包含原始入参和敏感响应
 */
@Data
public class ApiErrorBO {
    /**
     * HTTP响应状态
     */
    private int status;
    /**
     * 业务错误码
     */
    private ApiCode code;
    /**
     * 对用户公开的提示
     */
    private String message;
}
