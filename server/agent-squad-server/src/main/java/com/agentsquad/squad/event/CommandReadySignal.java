package com.agentsquad.squad.event;

/**
 * 已持久受理命令的唤醒引用
 * <p>
 * 仅携带稳定命令身份，提交后安排领取；通知丢失由持久命令扫描恢复，不承载模型输入。
 */
public record CommandReadySignal(Long commandId) {
}
