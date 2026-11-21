package com.agentsquad.squad.common.enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
/**
 * 功能权限目录
 *
 * 菜单控制页面可见性，功能编码控制实际接口操作，与前端和版本化目录保持一致。
 */
@Getter
@RequiredArgsConstructor
public enum PermissionCode {
    USER_QUERY("management:user:query", "查询用户"),
    USER_ASSIGN_ROLE("management:user:assign-role", "分配角色"),
    USER_ENABLE("management:user:enable", "启用管理资格"),
    USER_DISABLE("management:user:disable", "禁用管理资格"),
    ROLE_QUERY("management:role:query", "查询角色"),
    ROLE_CREATE("management:role:create", "新增角色"),
    ROLE_UPDATE("management:role:update", "修改角色"),
    ROLE_APPLY("management:role:apply", "应用权限"),
    ROLE_ENABLE("management:role:enable", "启用角色"),
    ROLE_DISABLE("management:role:disable", "禁用角色"),
    PERMISSION_QUERY("management:permission:query", "查询权限"),
    AGENT_QUERY("management:agent:query", "查询 Agent"),
    AGENT_EDIT("management:agent:edit", "编辑 Agent"),
    AGENT_PUBLISH("management:agent:publish", "发布 Agent"),
    TOOL_QUERY("management:tool:query", "查询 工具"),
    TOOL_EDIT("management:tool:edit", "编辑 工具"),
    TOOL_PUBLISH("management:tool:publish", "发布 工具"),
    MCP_QUERY("management:mcp:query", "查询 MCP"),
    MCP_EDIT("management:mcp:edit", "编辑 MCP"),
    MCP_PUBLISH("management:mcp:publish", "发布 MCP");
    private final String code;
    private final String desc;
}
