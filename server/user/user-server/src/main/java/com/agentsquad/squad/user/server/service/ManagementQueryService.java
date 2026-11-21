package com.agentsquad.squad.user.server.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.enums.PermissionStage;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.vo.PageVO;
import com.agentsquad.squad.user.server.bo.ManagementUserBO;
import com.agentsquad.squad.user.server.bo.RoleBO;
import com.agentsquad.squad.user.server.converter.RbacConverter;
import com.agentsquad.squad.user.server.dto.ManagementPageDTO;
import com.agentsquad.squad.user.server.mapper.MenuMapper;
import com.agentsquad.squad.user.server.mapper.RbacMapper;
import com.agentsquad.squad.user.server.vo.ManagementUserVO;
import com.agentsquad.squad.user.server.vo.PermissionVO;
import com.agentsquad.squad.user.server.vo.RoleVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 管理列表与详情
 * <p>
 * 负责分页和查询投影装配，不更改账号资料和授权关系。
 */
@Service
public class ManagementQueryService {
    @Resource
    private RbacMapper mapper;

    @Resource
    private RbacConverter converter;

    @Resource
    private MenuMapper menus;

    public PageVO<RoleVO> roles(ManagementPageDTO query) {
        var records = mapper.rolePage(query, offset(query));
        return page(converter.roles(records), mapper.roleCount(query), query);
    }

    public RoleVO role(String roleId) {
        RoleBO role = mapper.roleDetail(roleId);
        if (role == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "角色不存在");
        }
        role.setDraftPermissionCodes(mapper.rolePermissions(roleId, PermissionStage.DRAFT));
        role.setAppliedPermissionCodes(mapper.rolePermissions(roleId, PermissionStage.APPLIED));
        role.setDraftMenuIds(menus.roleMenus(roleId, PermissionStage.DRAFT));
        role.setAppliedMenuIds(menus.roleMenus(roleId, PermissionStage.APPLIED));
        return converter.role(role);
    }

    public PageVO<ManagementUserVO> members(ManagementPageDTO query) {
        var records = mapper.memberPage(query, offset(query));
        for (ManagementUserBO member : records) {
            member.setRoleIds(mapper.memberRoles(Long.valueOf(member.getUserId())));
        }
        return page(converter.members(records), mapper.memberCount(query), query);
    }

    public ManagementUserVO member(Long userId) {
        return memberView(mapper.memberDetail(userId));
    }

    public ManagementUserVO byPhone(String phone) {
        if (phone == null || !phone.matches("1[0-9]{10}")) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请输入有效手机号");
        }
        return memberView(mapper.memberByPhone(phone));
    }

    public PageVO<PermissionVO> permissions(ManagementPageDTO query) {
        var records = mapper.permissionPage(query, offset(query));
        return page(converter.permissions(records), mapper.permissionCount(query), query);
    }

    public List<RoleVO> roleOptions() {
        return converter.roles(mapper.roleOptions());
    }

    private ManagementUserVO memberView(ManagementUserBO member) {
        if (member == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "找不到正常账号，请先注册");
        }
        member.setRoleIds(mapper.memberRoles(Long.valueOf(member.getUserId())));
        return converter.member(member);
    }

    private long offset(ManagementPageDTO query) {
        return (long) (query.getCurrent() - 1) * query.getSize();
    }

    private <T> PageVO<T> page(List<T> records, long total, ManagementPageDTO query) {
        PageVO<T> page = new PageVO<>();
        page.setRecords(records);
        page.setTotal(total);
        page.setCurrent(query.getCurrent());
        page.setSize(query.getSize());
        return page;
    }
}
