package com.agentsquad.squad.tools.repository;

import com.agentsquad.squad.tools.domain.ConnectionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 连接凭据引用 SQL 边界；全部查询按归属用户限定，密文不在本表。
 */
@Mapper
public interface ConnectionMapper {

    int insert(@Param("connectionId") String connectionId, @Param("userId") String userId,
               @Param("creatorName") String creatorName, @Param("name") String name,
               @Param("provider") String provider, @Param("environment") String environment,
               @Param("resourcePolicyJson") String resourcePolicyJson,
               @Param("secretRef") String secretRef,
               @Param("createIdempotencyKey") String createIdempotencyKey);

    ConnectionRow findOwned(@Param("userId") String userId,
                            @Param("connectionId") String connectionId);

    ConnectionRow findByIdempotencyKey(@Param("userId") String userId,
                                       @Param("createIdempotencyKey") String createIdempotencyKey);

    List<ConnectionRow> listByOwner(@Param("userId") String userId,
                                    @Param("cursorId") Long cursorId, @Param("limit") int limit);

    int updateOwned(@Param("userId") String userId,
                    @Param("connectionId") String connectionId,
                    @Param("expectedRevision") long expectedRevision,
                    @Param("resourcePolicyJson") String resourcePolicyJson,
                    @Param("enabled") Boolean enabled,
                    @Param("updaterName") String updaterName);
}
