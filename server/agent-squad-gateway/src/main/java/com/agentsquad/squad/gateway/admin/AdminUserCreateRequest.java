package com.agentsquad.squad.gateway.admin;

import com.agentsquad.squad.common.identity.AccountRole;

/**
 * 管理员创建账号请求；角色缺省为 USER，临时密码由服务端生成。
 */
public record AdminUserCreateRequest(String username, String displayName, AccountRole role) {
}
