package com.agentsquad.squad.runtime.command;

/**
 * D0 离线探针使用的持久命令对象。
 *
 * <p>命令 ID 同时承担投递意图和幂等收据的职责；真实实现会映射到 sq_command，
 * 本对象刻意不复制 AgentScope 的内部 Turn、Run 或日志结构。</p>
 */
public record DurableCommand(String commandId, String payload, CommandStatus status) {
}
