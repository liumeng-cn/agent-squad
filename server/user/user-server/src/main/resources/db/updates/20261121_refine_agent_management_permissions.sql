-- 已执行 20261121_add_agent_management_permissions.sql 与 20261121_add_agent_catalog_permissions.sql 的数据库，
-- 升级为最终的智能体配置菜单、功能说明与接口权限规则。使用最终 baseline 初始化的数据库无需执行。
BEGIN;

UPDATE squad_user.sq_menu
SET name = '智能体配置',
    last_update_time = CURRENT_TIMESTAMP
WHERE menu_id = 'menu-agent-management';

UPDATE squad_user.sq_menu
SET name = CASE menu_id
               WHEN 'menu-agent-mcp' THEN 'MCP 服务'
               WHEN 'menu-agent-tool' THEN '工具'
               WHEN 'menu-agent-config' THEN 'Agent'
           END,
    sort_order = CASE menu_id
                     WHEN 'menu-agent-mcp' THEN 41
                     WHEN 'menu-agent-tool' THEN 42
                     WHEN 'menu-agent-config' THEN 43
                 END,
    last_update_time = CURRENT_TIMESTAMP
WHERE menu_id IN ('menu-agent-mcp', 'menu-agent-tool', 'menu-agent-config');

UPDATE squad_user.sq_permission AS p
SET name = v.name,
    description = v.description,
    last_update_time = CURRENT_TIMESTAMP
FROM (VALUES ('management:mcp:query', '查询 MCP 服务', '查看 MCP 服务、版本、引用关系与凭据引用名'),
             ('management:mcp:edit', '编辑 MCP 服务', '新增服务、维护草稿与测试连接'),
             ('management:mcp:publish', '发布 MCP 服务', '发布版本及启用、停用服务'),
             ('management:tool:query', '查询工具', '查看工具、版本、引用关系与可选实现'),
             ('management:tool:edit', '编辑工具', '新增工具、维护草稿与试运行'),
             ('management:tool:publish', '发布工具', '发布版本及启用、停用工具'),
             ('management:agent:query', '查询 Agent', '查看 Agent、版本、引用关系与可绑定能力'),
             ('management:agent:edit', '编辑 Agent', '新增 Agent 与维护草稿'),
             ('management:agent:publish', '发布 Agent', '发布、切换生效版本及启用、停用 Agent'))
         AS v(code, name, description)
WHERE p.code = v.code;

-- 旧接口规则整体替换，避免遗留路径继续放行。
DELETE FROM squad_user.sq_permission_route
WHERE permission_code IN ('management:mcp:query', 'management:mcp:edit', 'management:mcp:publish',
                          'management:tool:query', 'management:tool:edit', 'management:tool:publish',
                          'management:agent:query', 'management:agent:edit', 'management:agent:publish');

-- 智能体配置接口权限：路径为管理前缀之后的部分，按排序号依次匹配。
INSERT INTO squad_user.sq_permission_route (permission_code, http_method, path_pattern, sort_order)
VALUES ('management:mcp:query',    'GET',  '/mcp-servers',                                                          30),
       ('management:mcp:edit',     'POST', '/mcp-servers',                                                          31),
       ('management:mcp:edit',     'POST', '/mcp-servers/probe',                                                    32),
       ('management:mcp:query',    'GET',  '/mcp-servers/credential-options',                                       33),
       ('management:mcp:query',    'GET',  '/mcp-servers/[1-9][0-9]{0,18}',                                         34),
       ('management:mcp:edit',     'PUT',  '/mcp-servers/[1-9][0-9]{0,18}',                                         35),
       ('management:mcp:publish',  'POST', '/mcp-servers/[1-9][0-9]{0,18}/(enable|disable)',                        36),
       ('management:mcp:query',    'GET',  '/mcp-servers/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}',               37),
       ('management:mcp:edit',     'POST', '/mcp-servers/[1-9][0-9]{0,18}/versions',                                38),
       ('management:mcp:edit',     'PUT',  '/mcp-servers/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}',               39),
       ('management:mcp:publish',  'POST', '/mcp-servers/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}/publish',       40),
       ('management:tool:query',   'GET',  '/tools',                                                                41),
       ('management:tool:edit',    'POST', '/tools',                                                                42),
       ('management:tool:edit',    'POST', '/tools/test-run',                                                       43),
       ('management:tool:query',   'GET',  '/tools/(builtin-options|mcp-options|credential-options)',               44),
       ('management:tool:query',   'GET',  '/tools/mcp-options/[1-9][0-9]{0,18}/remote-tools',                      45),
       ('management:tool:query',   'GET',  '/tools/[1-9][0-9]{0,18}',                                               46),
       ('management:tool:edit',    'PUT',  '/tools/[1-9][0-9]{0,18}',                                               47),
       ('management:tool:publish', 'POST', '/tools/[1-9][0-9]{0,18}/(enable|disable)',                              48),
       ('management:tool:query',   'GET',  '/tools/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}',                     49),
       ('management:tool:edit',    'POST', '/tools/[1-9][0-9]{0,18}/versions',                                      50),
       ('management:tool:edit',    'PUT',  '/tools/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}',                     51),
       ('management:tool:publish', 'POST', '/tools/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}/publish',             52),
       ('management:agent:query',  'GET',  '/agents',                                                               53),
       ('management:agent:edit',   'POST', '/agents',                                                               54),
       ('management:agent:query',  'GET',  '/agents/(tool-options|subagent-options)',                               55),
       ('management:agent:query',  'GET',  '/agents/[1-9][0-9]{0,18}',                                              56),
       ('management:agent:edit',   'PUT',  '/agents/[1-9][0-9]{0,18}',                                              57),
       ('management:agent:publish','POST', '/agents/[1-9][0-9]{0,18}/(enable|disable)',                             58),
       ('management:agent:query',  'GET',  '/agents/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}',                    59),
       ('management:agent:edit',   'POST', '/agents/[1-9][0-9]{0,18}/versions',                                     60),
       ('management:agent:edit',   'PUT',  '/agents/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}',                    61),
       ('management:agent:publish','POST', '/agents/[1-9][0-9]{0,18}/versions/[1-9][0-9]{0,18}/(publish|activate)', 62)
ON CONFLICT (http_method, path_pattern) DO NOTHING;

-- 授权结构变化后推进版本，使已登录会话重新加载权限。
UPDATE squad_user.sq_rbac_revision
SET version = version + 1,
    last_update_time = CURRENT_TIMESTAMP
WHERE code = 'authorization';

COMMIT;
