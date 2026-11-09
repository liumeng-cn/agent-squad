package com.agentsquad.squad.model.bo;

/**
 * 原生事件的服务端归属
 * <p>
 * 由运行会话装配入口提供，收件事务仍核对数据库绑定，不能从模型事件提取用户身份。
 */
public record RuntimeEventSourceBO(String userId, String bindingId, String sessionId, int mappingVersion) {
    public static final int CURRENT_MAPPING_VERSION = 1;
    private static final int MAX_IDENTITY_LENGTH = 36;
    public RuntimeEventSourceBO {
        requireIdentity(userId);
        requireIdentity(bindingId);
        requireIdentity(sessionId);
        if (mappingVersion != CURRENT_MAPPING_VERSION) {
            throw new IllegalArgumentException("不支持的原生事件映射版本");
        }
    }

    private static void requireIdentity(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_IDENTITY_LENGTH) {
            throw new IllegalArgumentException("原生事件归属标识无效");
        }
    }
}
