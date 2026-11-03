package com.agentsquad.squad.gateway.exception;

/**
 * 统一错误响应；只暴露可理解的原因，不包含 SQL、令牌或内部路径。
 */
public record ErrorView(String message) {
}
