package com.agentsquad.squad.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 运行内容的稳定摘要工具。
 * <p>
 * 对 UTF-8 文本计算小写 SHA-256 十六进制值，用于持久命令和候选的内容幂等校验。
 */
public final class ContentDigests {

    private ContentDigests() {
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
