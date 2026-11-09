package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.ConnectionPO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 连接凭据引用 ；全部查询按归属用户限定，密文不在本表
 */
@Mapper
public interface ConnectionMapper extends BaseMapper<ConnectionPO> {

    int insertConnection(@Param("connectionId") Long connectionId, @Param("userId") Long userId,
                         @Param("creatorName") String creatorName, @Param("name") String name,
                         @Param("provider") String provider, @Param("environment") String environment,
                         @Param("resourcePolicyJson") String resourcePolicyJson,
                         @Param("secretRef") String secretRef,
                         @Param("createIdempotencyKey") String createIdempotencyKey);

    default ConnectionPO findOwned(Long userId, Long connectionId) {
        return selectOne(new QueryWrapper<ConnectionPO>()
                .eq("user_id", userId)
                .eq("connection_id", connectionId)
                .eq("del_flag", "0"));
    }

    default ConnectionPO findByIdempotencyKey(Long userId, String createIdempotencyKey) {
        return selectOne(new QueryWrapper<ConnectionPO>()
                .eq("user_id", userId)
                .eq("create_idempotency_key", createIdempotencyKey)
                .eq("del_flag", "0"));
    }

    List<ConnectionPO> listByOwner(@Param("userId") Long userId,
                                   @Param("cursorId") Long cursorId, @Param("limit") int limit);

    int updateOwned(@Param("userId") Long userId,
                    @Param("connectionId") Long connectionId,
                    @Param("expectedRevision") long expectedRevision,
                    @Param("resourcePolicyJson") String resourcePolicyJson,
                    @Param("enabled") Boolean enabled,
                    @Param("updaterName") String updaterName);
}
