package com.agentsquad.squad.v2.utils;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.bo.PromptSectionsBO;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化提示词规则
 * <p>
 * 规范化、校验五段式提示词并拼装为运行时使用的完整 Markdown 系统提示词。
 */
public final class PromptSectionRules {
    private static final int MAX_ROLE = 4000;

    private static final int MAX_GOAL = 4000;

    private static final int MAX_WORKFLOW = 6000;

    private static final int MAX_CONSTRAINTS = 4000;

    private static final int MAX_OUTPUT_FORMAT = 2000;

    private PromptSectionRules() {
    }

    /**
     * 规范化并校验长度
     */
    public static PromptSectionsBO normalize(PromptSectionsBO source) {
        PromptSectionsBO sections = new PromptSectionsBO();
        if (source == null) {
            return sections;
        }
        sections.setRole(clean(source.getRole(), "角色与定位", MAX_ROLE));
        sections.setGoal(clean(source.getGoal(), "核心目标", MAX_GOAL));
        sections.setWorkflow(clean(source.getWorkflow(), "工作流程与策略", MAX_WORKFLOW));
        sections.setConstraints(clean(source.getConstraints(), "约束与边界", MAX_CONSTRAINTS));
        sections.setOutputFormat(clean(source.getOutputFormat(), "输出规范", MAX_OUTPUT_FORMAT));
        return sections;
    }

    /**
     * 判断提示词是否满足就绪与发布条件
     */
    public static boolean isReady(PromptSectionsBO sections) {
        if (sections == null) {
            return false;
        }
        return !isBlank(sections.getRole()) && !isBlank(sections.getGoal());
    }

    /**
     * 拼装完整 Markdown 系统提示词
     */
    public static String assemble(PromptSectionsBO sections) {
        if (sections == null) {
            return "";
        }
        List<String> blocks = new ArrayList<>();
        appendBlock(blocks, "角色与定位", sections.getRole());
        appendBlock(blocks, "核心目标", sections.getGoal());
        appendBlock(blocks, "工作流程与策略", sections.getWorkflow());
        appendBlock(blocks, "约束与边界", sections.getConstraints());
        appendBlock(blocks, "输出规范", sections.getOutputFormat());
        return String.join("\n\n", blocks);
    }

    private static void appendBlock(List<String> blocks, String title, String content) {
        if (!isBlank(content)) {
            blocks.add("## " + title + "\n" + content.trim());
        }
    }

    private static String clean(String value, String label, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.length() > maxLength) {
            throw new BusinessException(ApiCode.BAD_REQUEST, label + "不能超过 " + maxLength + " 个字符");
        }
        return text;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
