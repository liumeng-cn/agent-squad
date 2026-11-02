package com.agentsquad.squad.gateway.admin;

import java.util.List;

/**
 * 用户摘要分页结果；游标为上一页最后一个用户业务 ID。
 */
public record AdminUserPage(List<AdminUserView> items, String nextCursor) {
}
