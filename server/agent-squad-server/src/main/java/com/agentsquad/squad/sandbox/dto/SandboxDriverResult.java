package com.agentsquad.squad.sandbox.dto;


/**
 * 隔离驱动的已知执行结果。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxDriverResult(int exitCode, String stdout, String stderr, boolean truncated,
                                  java.util.List<SandboxOutput> outputs) {
}
