package com.agentsquad.squad.user.server.converter;

import com.agentsquad.squad.user.api.vo.MenuFunctionVO;
import com.agentsquad.squad.user.api.vo.MenuVO;
import com.agentsquad.squad.user.server.bo.ManagementUserBO;
import com.agentsquad.squad.user.server.bo.MenuBO;
import com.agentsquad.squad.user.server.bo.MenuFunctionBO;
import com.agentsquad.squad.user.server.bo.PermissionBO;
import com.agentsquad.squad.user.server.bo.RoleBO;
import com.agentsquad.squad.user.server.vo.ManagementUserVO;
import com.agentsquad.squad.user.server.vo.PermissionVO;
import com.agentsquad.squad.user.server.vo.RoleVO;
import org.mapstruct.Mapper;
import java.util.List;

/**
 * 管理查询结果转换
 * <p>
 * 只转换查询投影与响应，不包含授权判断和外部调用。
 */
@Mapper(componentModel = "spring")
public interface RbacConverter {

    MenuVO menu(MenuBO source);
    MenuFunctionVO function(MenuFunctionBO source);

    RoleVO role(RoleBO source);

    ManagementUserVO member(ManagementUserBO source);

    List<RoleVO> roles(List<RoleBO> source);

    List<ManagementUserVO> members(List<ManagementUserBO> source);

    List<PermissionVO> permissions(List<PermissionBO> source);
}
