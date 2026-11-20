package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 待答用户问题
 * <p>
 * 只公开提问工具的问题和建议选项，不公开其他工具参数或外部执行载荷。
 */
@Data
public class ChatInteractionVO {
    /**
     * 原生待答标识。
     */
    private String requestId;

    /**
     * 问题所属轮次。
     */
    private String turnId;

    /**
     * 向用户展示的问题。
     */
    private String question;

    /**
     * 建议答案，允许用户自行输入。
     */
    private List<String> options = new ArrayList<>();
}
