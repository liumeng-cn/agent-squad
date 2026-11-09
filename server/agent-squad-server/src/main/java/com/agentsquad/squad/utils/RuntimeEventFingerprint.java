package com.agentsquad.squad.utils;

import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.util.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 原生事件不可变事实摘要
 * <p>
 * 收件与消费共用完整信封和载荷的摘要算法，仅在内存编码，不保存或记录原始正文。
 */
public final class RuntimeEventFingerprint {
    private RuntimeEventFingerprint() {
    }

    public static String of(SessionEvent event) {
        try {
            byte[] encoded = JsonUtils.getJsonCodec().toJson(event).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM不支持SHA-256", exception);
        }
    }
}
