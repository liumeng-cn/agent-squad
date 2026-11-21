package com.agentsquad.squad.user.server;

import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.common.enums.ManagementUserStatus;
import com.agentsquad.squad.common.enums.PermissionCode;
import com.agentsquad.squad.common.enums.RbacStatus;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.IdentityAssertion;
import com.agentsquad.squad.user.server.dto.ManagementPageDTO;
import com.agentsquad.squad.user.server.dto.RbacStatusDTO;
import com.agentsquad.squad.user.server.dto.RbacVersionDTO;
import com.agentsquad.squad.user.server.dto.RoleCreateDTO;
import com.agentsquad.squad.user.server.dto.RoleUpdateDTO;
import com.agentsquad.squad.user.server.dto.UserRolesDTO;
import com.agentsquad.squad.user.server.service.ManagementQueryService;
import com.agentsquad.squad.user.server.service.PermissionService;
import com.agentsquad.squad.user.server.service.RoleService;
import com.agentsquad.squad.user.server.service.UserRoleService;
import com.agentsquad.squad.user.server.vo.ManagementUserVO;
import com.agentsquad.squad.user.server.vo.RoleVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地授权集成验证
 * <p>
 * 显式开启后在已迁移的本地 PostgreSQL 上验证真实 Mapper 和事务，夹具及业务变更均在测试结束时回滚。
 */
@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named = "rbac.integration", matches = "true")
class RbacIntegrationTest {
    @Resource
    private JdbcTemplate jdbc;

    @Resource
    private RoleService roles;

    @Resource
    private UserRoleService members;

    @Resource
    private PermissionService access;

    @Resource
    private ManagementQueryService queries;

    @AfterEach
    void clearIdentity() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void appliesSnapshotsAndRevokesManagementWithoutChangingAccount() {
        long actor = createAccount();
        long target = createAccount();
        jdbc.update("INSERT INTO squad_user.sq_user_role(user_id,role_id,status) VALUES (?,'role-platform-admin','ACTIVE')", actor);
        bind(target);
        assertFalse(access.current().isCanAccess());
        bind(actor);

        RoleCreateDTO create = new RoleCreateDTO();
        create.setCode("test-" + UUID.randomUUID());
        create.setName("集成验证角色");
        create.setDescription("事务回滚夹具");
        create.setPermissionCodes(List.of(PermissionCode.ACCESS.getCode(), PermissionCode.ROLE_READ.getCode()));
        RoleVO role = roles.create(create);
        assertEquals(RbacStatus.DISABLED, role.getStatus());
        assertTrue(role.getAppliedPermissionCodes().isEmpty());
        assertEquals(2, role.getDraftPermissionCodes().size());

        RbacVersionDTO apply = new RbacVersionDTO();
        apply.setVersion(role.getVersion());
        role = roles.apply(role.getRoleId(), apply);
        assertEquals(RbacStatus.ACTIVE, role.getStatus());
        assertEquals(role.getVersion(), role.getAppliedVersion());
        assertEquals(2, role.getAppliedPermissionCodes().size());

        RoleUpdateDTO edit = new RoleUpdateDTO();
        edit.setVersion(role.getVersion());
        edit.setName(role.getName());
        edit.setDescription(role.getDescription());
        edit.setPermissionCodes(List.of(PermissionCode.ACCESS.getCode()));
        role = roles.update(role.getRoleId(), edit);
        assertEquals(1, role.getDraftPermissionCodes().size());
        assertEquals(2, role.getAppliedPermissionCodes().size());

        UserRolesDTO grant = new UserRolesDTO();
        grant.setRoleIds(List.of(role.getRoleId()));
        grant.setVersion(0L);
        ManagementUserVO member = members.roles(Long.toString(target), grant);
        bind(target);
        assertTrue(access.current().isCanAccess());
        assertTrue(access.current().getPermissionCodes().contains(PermissionCode.ROLE_READ.getCode()));
        assertFalse(access.current().getPermissionCodes().contains(PermissionCode.USER_MANAGE.getCode()));

        bind(actor);
        RbacStatusDTO disable = new RbacStatusDTO();
        disable.setVersion(member.getVersion());
        disable.setStatus(RbacStatus.DISABLED);
        member = members.status(Long.toString(target), disable);
        assertEquals(ManagementUserStatus.DISABLED, member.getStatus());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM squad_user.sq_user WHERE user_id=?", String.class, target));
        bind(target);
        assertFalse(access.current().isCanAccess());

        bind(actor);
        ManagementPageDTO query = new ManagementPageDTO();
        query.setStatus(ManagementUserStatus.DISABLED);
        assertTrue(queries.members(query).getTotal() >= 1);
        assertTrue(queries.roles(new ManagementPageDTO()).getTotal() >= 2);
        assertEquals(6L, queries.permissions(new ManagementPageDTO()).getTotal());
        assertThrows(BusinessException.class, () -> members.status(Long.toString(target), disable));
    }

    private long createAccount() {
        Long id = jdbc.queryForObject("SELECT nextval('squad_user.user_business_id_seq')", Long.class);
        jdbc.update("INSERT INTO squad_user.sq_user(user_id,username,phone) VALUES (?,?,?)", id, "rbac-test-" + id, "test-" + id);
        return id;
    }

    private void bind(long id) {
        CurrentUserBO user = new CurrentUserBO();
        user.setUserId(Long.toString(id));
        user.setSessionId("rbac-integration");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(IdentityAssertion.ATTRIBUTE, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
