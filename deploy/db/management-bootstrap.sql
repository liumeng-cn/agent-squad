-- 使用 psql -v manager_phone=手机号 -f deploy/db/management-bootstrap.sql 显式授权已有账号。
-- 不创建普通账号，不修改密码或登录状态，不在应用启动时执行。
BEGIN;
SELECT role_id
FROM squad_user.sq_role
WHERE role_id = 'role-platform-admin'
FOR UPDATE;
INSERT INTO squad_user.sq_user_role (user_id, role_id, status)
SELECT user_id, 'role-platform-admin', 'ACTIVE'
FROM squad_user.sq_user
WHERE phone = :'manager_phone'
  AND status = 'ACTIVE'
  AND del_flag = '0'
ON CONFLICT (user_id, role_id)
DO UPDATE SET status = 'ACTIVE', last_update_time = CURRENT_TIMESTAMP;
UPDATE squad_user.sq_user
SET rbac_version = rbac_version + 1
WHERE phone = :'manager_phone' AND status = 'ACTIVE' AND del_flag = '0';
COMMIT;
