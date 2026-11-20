package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.ChatConvPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话目录访问
 * <p>
 * 按用户归属读写对话，建档和改名使用原子 SQL。
 */
@Mapper
public interface ChatConvMapper extends BaseMapper<ChatConvPO> {

    int updateTitle(ChatConvPO conv);
}
