-- 统一 Agent 配置目录名称与接口权限路由，保留原有子菜单、权限与角色授权关系。
UPDATE squad_user.sq_menu
SET name = 'Agent 配置',
    last_update_time = CURRENT_TIMESTAMP
WHERE menu_id = 'menu-agent-management'
  AND name <> 'Agent 配置';

UPDATE squad_user.sq_menu
SET name = 'MCP',
    last_update_time = CURRENT_TIMESTAMP
WHERE menu_id = 'menu-agent-mcp'
  AND name <> 'MCP';

-- 同步新增的删除、发布预检、Master 发布和发布快照只读接口权限路由
INSERT INTO squad_user.sq_permission_route (permission_code, http_method, path_pattern, sort_order)
VALUES ('management:mcp:publish',  'POST', '/mcp-servers/[1-9][0-9]{0,18}/(enable|disable|delete)',                                36),
       ('management:tool:publish', 'POST', '/tools/[1-9][0-9]{0,18}/(enable|disable|delete)',                                      48),
       ('management:agent:publish','POST', '/agents/[1-9][0-9]{0,18}/(enable|disable|delete|publish)',                             58),
       ('management:agent:publish','GET',  '/agents/[1-9][0-9]{0,18}/publish-preview',                                             60),
       ('management:agent:query',  'GET',  '/agents/[1-9][0-9]{0,18}/releases/[1-9][0-9]{0,18}',                                   61),
       ('management:agent:query',  'GET',  '/agents/[1-9][0-9]{0,18}/releases/[1-9][0-9]{0,18}/(subagents|tools)/[1-9][0-9]{0,18}', 62)
ON CONFLICT (http_method, path_pattern) DO NOTHING;
