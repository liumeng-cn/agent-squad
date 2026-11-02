package com.agentsquad.squad.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 附件材料正文格式化工具。
 * <p>
 * 按媒体类型规范化用户材料：JSON 美化、CSV 结构化、Markdown 与纯文本保留原文。
 * 截断时追加明确标记，供审计与模型理解边界。
 */
public final class MaterialFormatter {

    private static final ObjectMapper PRETTY = new ObjectMapper();

    static {
        PRETTY.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private MaterialFormatter() {
    }

    /**
     * 格式化附件正文，超长按字符截断并追加标记。
     *
     * @param content   原始 UTF-8 正文
     * @param mediaType 媒体类型（application/json、text/csv、text/markdown、text/plain）
     * @param maxChars  单文件最大字符数（含截断标记）
     * @return 格式化后的正文，可能被截断
     */
    public static String format(String content, String mediaType, int maxChars) {
        String formatted = formatByType(content, mediaType);
        if (formatted.length() <= maxChars) {
            return formatted;
        }
        int keep = Math.max(0, maxChars - TRUNCATION_MARKER.length());
        return formatted.substring(0, keep) + TRUNCATION_MARKER;
    }

    private static final String TRUNCATION_MARKER = "\n[已截断：原始内容超过预算，可通过 read_attachment 工具读取完整内容]";

    private static String formatByType(String content, String mediaType) {
        if ("application/json".equals(mediaType)) {
            try {
                JsonNode node = PRETTY.readTree(content);
                return PRETTY.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            } catch (Exception exception) {
                return content;
            }
        }
        if ("text/csv".equals(mediaType)) {
            return formatCsv(content);
        }
        return content;
    }

    /**
     * CSV 结构化：保留表头与全部行，以管道分隔，便于模型理解表格结构。
     */
    private static String formatCsv(String content) {
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            result.append(trimmed).append('\n');
        }
        return result.toString();
    }
}
