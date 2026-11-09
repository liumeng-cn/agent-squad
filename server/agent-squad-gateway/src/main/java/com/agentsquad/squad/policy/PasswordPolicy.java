package com.agentsquad.squad.policy;

import com.agentsquad.squad.exceptions.ValidationFailedException;

import java.nio.charset.StandardCharsets;

/**
 * 密码强度规则。
 *
 * <p>BCrypt 只处理前 72 字节，超长密码必须显式拒绝，不能静默截断。</p>
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;
    private static final int BCRYPT_MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /**
     * 校验新密码长度与字节上限；失败时抛出统一 422，不返回具体比较细节。
     */
    public static void require(String password) {
        if (password == null || password.isBlank()) {
            throw new ValidationFailedException("密码不能为空");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ValidationFailedException("密码长度需为 12–128 个字符且不超过 72 字节");
        }
    }
}
