-- 在 Agent 管理菜单已初始化的数据库执行。
BEGIN;

-- 工具目录与 MCP 服务的管理授权
INSERT INTO squad_user.sq_menu (menu_id, parent_menu_id, code, name, type, route_name, route_path, sort_order)
VALUES ('menu-agent-tool', 'menu-agent-management', 'agent-tool', '工具目录', 'PAGE', 'management-tools', '/management/tools', 42),
       ('menu-agent-mcp', 'menu-agent-management', 'agent-mcp', 'MCP 服务', 'PAGE', 'management-mcp-servers', '/management/mcp-servers', 43)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO squad_user.sq_permission (code, name, module, description, menu_id, sort_order)
VALUES ('management:tool:query', '查询 工具', 'agents', '查询工具目录配置', 'menu-agent-tool', 1),
       ('management:tool:edit', '编辑 工具', 'agents', '编辑工具目录配置', 'menu-agent-tool', 2),
       ('management:tool:publish', '发布 工具', 'agents', '发布工具目录配置', 'menu-agent-tool', 3),
       ('management:mcp:query', '查询 MCP', 'agents', '查询MCP 服务配置', 'menu-agent-mcp', 1),
       ('management:mcp:edit', '编辑 MCP', 'agents', '编辑MCP 服务配置', 'menu-agent-mcp', 2),
       ('management:mcp:publish', '发布 MCP', 'agents', '发布MCP 服务配置', 'menu-agent-mcp', 3)
ON CONFLICT (code) DO NOTHING;

INSERT INTO squad_user.sq_role_menu (role_id, menu_id, stage)
SELECT 'role-platform-admin', m.menu_id, s.stage
FROM squad_user.sq_menu m
CROSS JOIN (VALUES ('DRAFT'), ('APPLIED')) AS s(stage)
WHERE m.menu_id IN ('menu-agent-tool', 'menu-agent-mcp')
ON CONFLICT DO NOTHING;

INSERT INTO squad_user.sq_role_permission (role_id, permission_code, stage)
SELECT 'role-platform-admin', p.code, s.stage
FROM squad_user.sq_permission p
CROSS JOIN (VALUES ('DRAFT'), ('APPLIED')) AS s(stage)
WHERE p.code IN ('management:tool:query', 'management:tool:edit', 'management:tool:publish', 'management:mcp:query', 'management:mcp:edit', 'management:mcp:publish')
ON CONFLICT DO NOTHING;

INSERT INTO squad_user.sq_permission_route (permission_code, http_method, path_pattern, sort_order)
VALUES ('management:agent:query', 'GET', '/agents/tool-options', 40),
       ('management:tool:query', 'GET', '/tools', 41),
       ('management:tool:edit', 'POST', '/tools', 42),
       ('management:tool:query', 'GET', '/tools/[1-9][0-9]{0,18}/versions', 43),
       ('management:tool:edit', 'POST', '/tools/[1-9][0-9]{0,18}/versions', 44),
       ('management:tool:edit', 'PUT', '/tools/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}', 45),
       ('management:tool:publish', 'POST', '/tools/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}/publish', 46),
       ('management:mcp:query', 'GET', '/mcp-servers', 47),
       ('management:mcp:edit', 'POST', '/mcp-servers', 48),
       ('management:mcp:query', 'GET', '/mcp-servers/[1-9][0-9]{0,18}/versions', 49),
       ('management:mcp:edit', 'POST', '/mcp-servers/[1-9][0-9]{0,18}/versions', 50),
       ('management:mcp:edit', 'PUT', '/mcp-servers/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}', 51),
       ('management:mcp:publish', 'POST', '/mcp-servers/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}/publish', 52),
       ('management:tool:query', 'GET', '/tools/mcp-options', 53)
ON CONFLICT (http_method, path_pattern) DO NOTHING;

COMMIT;
