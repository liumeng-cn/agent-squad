package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.ChatConvPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话目录访问
 * <p>
 * 简单单表操作使用 MyBatis-Plus 自带方法；标题改名按版本条件更新，使用原子 SQL。
 */
@Mapper
public interface ChatConvMapper extends BaseMapper<ChatConvPO> {

    /**
     * 按版本更新对话标题
     */
    int updateTitle(ChatConvPO conv);
}
