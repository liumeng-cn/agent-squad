package com.agentsquad.squad.runtime.dto;

/**
 * 任务当前答案视图；D1 直接回答阶段为占位，内容由后续 Bridge 写入。
 */
public record AnswerView(long revision, String text, boolean completed) {
}
