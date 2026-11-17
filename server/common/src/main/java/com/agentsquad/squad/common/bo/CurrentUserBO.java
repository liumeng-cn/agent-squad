package com.agentsquad.squad.common.bo;

import lombok.Data;

/**
 * 当前请求可信用户
 * <p>
 * 所属common模块，按明确职责维护用户身份与数据边界。
 */
@Data
public class CurrentUserBO {
    /**
     * 已验签用户业务ID
     */
    private String userId;
    /**
     * 登录会话标识
     */
    private String sessionId;
}
