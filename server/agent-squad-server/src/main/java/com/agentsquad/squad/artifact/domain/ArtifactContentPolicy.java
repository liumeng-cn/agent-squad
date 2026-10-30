package com.agentsquad.squad.artifact.domain;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * 交付文件名称、类型和摘要规则。
 * <p>
 * 文本要求严格UTF-8；二进制只支持带固定头的PNG/PDF，下载始终附件响应。
 */
public final class ArtifactContentPolicy {
    private static final Map<String, String> TYPES = Map.of("txt", "text/plain", "md", "text/markdown",
            "json", "application/json", "csv", "text/csv", "png", "image/png", "pdf", "application/pdf");

    private ArtifactContentPolicy() {
    }

    public static String mediaType(String name, byte[] bytes) {
        if (name == null || name.isBlank() || name.length() > 240 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(value -> value < 32) || bytes == null || bytes.length > 10485760) {
            throw invalid("交付文件名或内容大小无效");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = TYPES.get(extension);
        if (type == null) {
            throw invalid("交付仅支持txt、md、json、csv、png、pdf");
        }
        if ("png".equals(extension)) {
            byte[] header = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
            if (bytes.length < 8 || !java.util.Arrays.equals(header, java.util.Arrays.copyOf(bytes, 8))) {
                throw invalid("PNG文件头无效");
            }
        } else if ("pdf".equals(extension)) {
            if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw invalid("PDF文件头无效");
            }
        } else {
            try {
                StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            } catch (CharacterCodingException exception) {
                throw invalid("文本交付必须采用UTF-8编码");
            }
        }
        return type;
    }

    public static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private static RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
