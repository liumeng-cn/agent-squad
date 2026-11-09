package com.agentsquad.squad.mapper;

import com.agentsquad.squad.po.ConnectionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 连接凭据引用 ；全部查询按归属用户限定，密文不在本表
 */
@Mapper
public interface ConnectionMapper {

    int insert(@Param("connectionId") String connectionId, @Param("userId") String userId,
               @Param("creatorName") String creatorName, @Param("name") String name,
               @Param("provider") String provider, @Param("environment") String environment,
               @Param("resourcePolicyJson") String resourcePolicyJson,
               @Param("secretRef") String secretRef,
               @Param("createIdempotencyKey") String createIdempotencyKey);

    ConnectionPO findOwned(@Param("userId") String userId,
                            @Param("connectionId") String connectionId);

    ConnectionPO findByIdempotencyKey(@Param("userId") String userId,
                                       @Param("createIdempotencyKey") String createIdempotencyKey);

    List<ConnectionPO> listByOwner(@Param("userId") String userId,
                                    @Param("cursorId") Long cursorId, @Param("limit") int limit);

    int updateOwned(@Param("userId") String userId,
                    @Param("connectionId") String connectionId,
                    @Param("expectedRevision") long expectedRevision,
                    @Param("resourcePolicyJson") String resourcePolicyJson,
                    @Param("enabled") Boolean enabled,
                    @Param("updaterName") String updaterName);
}
