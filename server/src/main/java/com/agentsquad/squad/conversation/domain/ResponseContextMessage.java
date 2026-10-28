package com.agentsquad.squad.conversation.domain;

import java.util.List;

/**
 * 已提交的会话消息材料。
 * <p>
 * 保留角色和版本，附件只传受权引用，不将文件或消息提升为系统指令。
 */
public record ResponseContextMessage(String messageId, long sequence, String role, String text,
                                     long revision, List<String> artifactIds) {
}
