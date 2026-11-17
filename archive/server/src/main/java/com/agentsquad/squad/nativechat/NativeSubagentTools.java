package com.agentsquad.squad.nativechat;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/** 多 SubAgent 演示使用的三个纯计算工具，不访问外部系统。 */
public final class NativeSubagentTools {
    private NativeSubagentTools() {}
    public static final class Calculator {
        @Tool(name = "add_numbers", description = "将两个数字相加并返回计算结果", readOnly = true)
        public String addNumbers(@ToolParam(name = "left", description = "第一个数字") double left,
                                 @ToolParam(name = "right", description = "第二个数字") double right) {
            return left + " + " + right + " = " + (left + right);
        }
    }
    public static final class WordCounter {
        @Tool(name = "count_words", description = "统计文本中的词语数量，英文按空白分词", readOnly = true)
        public String countWords(@ToolParam(name = "text", description = "需要统计的文本") String text) {
            if (text == null || text.isBlank()) return "文本为空，词数为 0";
            String trimmed = text.trim();
            long words = trimmed.matches(".*[\\p{IsHan}].*")
                    ? trimmed.codePoints().filter(Character::isLetterOrDigit).count()
                    : trimmed.split("\\s+").length;
            return "文本：" + trimmed + "；词数：" + words;
        }
    }
    public static final class TextReverser {
        @Tool(name = "reverse_text", description = "将输入文本按 Unicode 字符反转", readOnly = true)
        public String reverseText(@ToolParam(name = "text", description = "需要反转的文本") String text) {
            return text == null ? "输入为空" : new StringBuilder(text).reverse().toString();
        }
    }
}
