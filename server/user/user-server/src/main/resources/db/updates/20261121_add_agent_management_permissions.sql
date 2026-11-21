BEGIN;

-- Agent 配置管理菜单及角色权限
INSERT INTO squad_user.sq_menu (menu_id, parent_menu_id, code, name, type, route_name, route_path, sort_order)
VALUES ('menu-agent-management', NULL, 'agent-management', 'Agent 管理', 'DIRECTORY', NULL, NULL, 40),
       ('menu-agent-config', 'menu-agent-management', 'agent-config', 'Agent 配置', 'PAGE',
        'management-agents', '/management/agents', 41)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO squad_user.sq_permission (code, name, module, description, menu_id, sort_order)
VALUES ('management:agent:query', '查询 Agent', 'agents', '浏览配置和版本', 'menu-agent-config', 1),
       ('management:agent:edit', '编辑 Agent', 'agents', '建档及维护草稿', 'menu-agent-config', 2),
       ('management:agent:publish', '发布 Agent', 'agents', '发布及切换生效版本', 'menu-agent-config', 3)
ON CONFLICT (code) DO NOTHING;

INSERT INTO squad_user.sq_role_menu (role_id, menu_id, stage)
SELECT 'role-platform-admin', m.menu_id, s.stage
FROM squad_user.sq_menu m
CROSS JOIN (VALUES ('DRAFT'), ('APPLIED')) AS s(stage)
WHERE m.menu_id IN ('menu-agent-management', 'menu-agent-config')
ON CONFLICT DO NOTHING;

INSERT INTO squad_user.sq_role_permission (role_id, permission_code, stage)
SELECT 'role-platform-admin', p.code, s.stage
FROM squad_user.sq_permission p
CROSS JOIN (VALUES ('DRAFT'), ('APPLIED')) AS s(stage)
WHERE p.code IN ('management:agent:query', 'management:agent:edit', 'management:agent:publish')
ON CONFLICT DO NOTHING;

INSERT INTO squad_user.sq_permission_route (permission_code, http_method, path_pattern, sort_order)
VALUES
    ('management:agent:query', 'GET', '/agents', 30),
    ('management:agent:edit', 'POST', '/agents', 31),
    ('management:agent:query', 'GET', '/agents/[1-9][0-9]{0,18}/versions', 32),
    ('management:agent:query', 'GET', '/agents/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}', 33),
    ('management:agent:edit', 'POST', '/agents/[1-9][0-9]{0,18}/versions', 34),
    ('management:agent:edit', 'PUT', '/agents/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}', 35),
    ('management:agent:publish', 'POST', '/agents/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}/publish', 36),
    ('management:agent:publish', 'POST', '/agents/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}/activate', 37)
ON CONFLICT (http_method, path_pattern) DO NOTHING;

COMMIT;
