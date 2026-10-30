package com.agentsquad.squad.conversation.domain;

/**
 * 回复可读取的节点进度事实。
 * <p>
 * 只保存当前目标计划的节点身份与状态，不携带Worker日志、内部推理或未核验候选正文。
 */
public record ResponseJobFact(String nodeKey, String status) {
}
