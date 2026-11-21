package com.agentsquad.squad.user.server;

import com.agentsquad.squad.common.enums.ManagementUserStatus;
import com.agentsquad.squad.common.enums.PermissionCode;
import com.agentsquad.squad.common.enums.PermissionStage;
import com.agentsquad.squad.common.enums.RbacStatus;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.user.api.vo.ManagementAccessVO;
import com.agentsquad.squad.user.server.bo.ManagementUserBO;
import com.agentsquad.squad.user.server.dto.RbacStatusDTO;
import com.agentsquad.squad.user.server.dto.RoleUpdateDTO;
import com.agentsquad.squad.user.server.mapper.RbacMapper;
import com.agentsquad.squad.user.server.po.RolePO;
import com.agentsquad.squad.user.server.service.PermissionService;
import com.agentsquad.squad.user.server.service.RoleService;
import com.agentsquad.squad.user.server.service.UserRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 授权变更边界测试
 * <p>
 * 检查版本冲突、内置角色保护、越权恢复和最后管理员拒绝；数据库回滚另由集成验证覆盖。
 */
class RbacServiceTest {
    @Test
    void rejectsStaleAndBuiltInRoleEdits() {
        RbacMapper mapper = mock(RbacMapper.class);
        RoleService service = new RoleService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "access", mock(PermissionService.class));
        RolePO role = new RolePO();
        role.setVersion(2L);
        role.setBuiltIn(false);
        when(mapper.lockRole("test")).thenReturn(role);
        RoleUpdateDTO request = new RoleUpdateDTO();
        request.setVersion(1L);
        assertThrows(BusinessException.class, () -> service.update("test", request));
        role.setBuiltIn(true);
        request.setVersion(2L);
        assertThrows(BusinessException.class, () -> service.update("test", request));
        verify(mapper, never()).deleteRolePermissions(anyString(), any());
    }

    @Test
    void cannotEnableRoleOutsideOwnPermissions() {
        RbacMapper mapper = mock(RbacMapper.class);
        PermissionService access = mock(PermissionService.class);
        RoleService service = new RoleService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "access", access);
        RolePO role = new RolePO();
        role.setVersion(0L);
        role.setAppliedVersion(0L);
        role.setBuiltIn(false);
        when(mapper.lockRole("test")).thenReturn(role);
        when(mapper.knownPermissions()).thenReturn(List.of(PermissionCode.USER_MANAGE.getCode()));
        when(mapper.rolePermissions("test", PermissionStage.APPLIED)).thenReturn(List.of(PermissionCode.USER_MANAGE.getCode()));
        ManagementAccessVO current = new ManagementAccessVO();
        current.setPermissionCodes(List.of(PermissionCode.ROLE_MANAGE.getCode()));
        when(access.current()).thenReturn(current);
        RbacStatusDTO request = new RbacStatusDTO();
        request.setVersion(0L);
        request.setStatus(RbacStatus.ACTIVE);
        assertThrows(BusinessException.class, () -> service.status("test", request));
        verify(mapper, never()).updateRoleStatus(anyString(), any(), any());
    }

    @Test
    void refusesLastAdministratorRemovalBeforeAuditAndVersionUpdate() {
        RbacMapper mapper = mock(RbacMapper.class);
        PermissionService access = mock(PermissionService.class);
        UserRoleService service = new UserRoleService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "access", access);
        when(access.userId("123")).thenReturn(123L);
        ManagementUserBO member = new ManagementUserBO();
        member.setVersion(0L);
        member.setStatus(ManagementUserStatus.ACTIVE);
        when(mapper.lockMember(123L)).thenReturn(member);
        when(mapper.memberRoles(123L)).thenReturn(List.of("role-platform-admin"));
        when(mapper.activeAdministrators()).thenReturn(0L);
        RbacStatusDTO request = new RbacStatusDTO();
        request.setVersion(0L);
        request.setStatus(RbacStatus.DISABLED);
        assertThrows(BusinessException.class, () -> service.status("123", request));
        verify(mapper, never()).bumpMemberVersion(anyLong(), anyLong(), any());
        verify(mapper, never()).audit(any(), anyString(), anyString(), any());
    }
}
