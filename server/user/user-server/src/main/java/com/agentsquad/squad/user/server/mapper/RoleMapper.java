package com.agentsquad.squad.user.server.mapper;

import com.agentsquad.squad.user.server.po.RolePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理角色访问
 * <p>
 * 简单新增复用 BaseMapper，带锁和条件更新通过管理 Mapper XML 完成。
 */
@Mapper
public interface RoleMapper extends BaseMapper<RolePO> {
}
