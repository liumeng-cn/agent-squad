package com.agentsquad.squad.user.server.converter;

import com.agentsquad.squad.user.api.vo.UserAccountVO;
import com.agentsquad.squad.user.server.po.UserPO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 用户资料映射
 * <p>
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@Mapper(componentModel = "spring")
public interface UserConverter {
    @Mapping(target = "avatar", expression = "java(com.agentsquad.squad.user.server.utils.AvatarGenerator.generate(String.valueOf(user.getUserId())))")
    @Mapping(target = "userName", source = "username")
    @Mapping(target = "effectiveDisplayName", expression = "java(user.getDisplayName().isBlank() ? user.getUsername() : user.getDisplayName())")
    UserAccountVO toAccount(UserPO user);
}
