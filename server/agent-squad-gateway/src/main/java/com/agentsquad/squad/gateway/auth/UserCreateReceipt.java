package com.agentsquad.squad.gateway.auth;

/** 临时密码仅在创建/重置接口响应一次，不写日志。 */
public record UserCreateReceipt(String userId, String temporaryPassword) {
}
