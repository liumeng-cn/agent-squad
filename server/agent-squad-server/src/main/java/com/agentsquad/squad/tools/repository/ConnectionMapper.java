package com.agentsquad.squad.tools.repository;

import com.agentsquad.squad.tools.domain.ConnectionRow;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 连接凭据引用 SQL 边界；全部查询按归属用户限定，密文不在本表。
 */
@Mapper
public interface ConnectionMapper {

    @Insert("""
            INSERT INTO sq_connection
                (connection_id, user_id, name, provider, environment, resource_policy_json,
                 secret_ref, create_idempotency_key, revision, enabled,
                 creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{connectionId}, #{userId}, #{name}, #{provider}, #{environment}, #{resourcePolicyJson},
                 #{secretRef}, #{createIdempotencyKey}, 0, 1,
                 #{userId}, #{creatorName}, #{userId}, #{creatorName})
            """)
    int insert(@Param("connectionId") String connectionId, @Param("userId") String userId,
               @Param("creatorName") String creatorName, @Param("name") String name,
               @Param("provider") String provider, @Param("environment") String environment,
               @Param("resourcePolicyJson") String resourcePolicyJson,
               @Param("secretRef") String secretRef,
               @Param("createIdempotencyKey") String createIdempotencyKey);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "connection_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "provider", javaType = String.class),
            @Arg(column = "environment", javaType = String.class),
            @Arg(column = "resource_policy_json", javaType = String.class),
            @Arg(column = "secret_ref", javaType = String.class),
            @Arg(column = "create_idempotency_key", javaType = String.class),
            @Arg(column = "revision", javaType = long.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "creator_id", javaType = String.class),
            @Arg(column = "creator_name", javaType = String.class),
            @Arg(column = "last_updater_id", javaType = String.class),
            @Arg(column = "last_updater_name", javaType = String.class)
    })
    @Select("""
            SELECT id, connection_id, user_id, name, provider, environment, resource_policy_json,
                   secret_ref, create_idempotency_key, revision, enabled,
                   creator_id, creator_name, last_updater_id, last_updater_name
            FROM sq_connection
            WHERE user_id = #{userId} AND connection_id = #{connectionId} AND del_flag = '0'
            """)
    ConnectionRow findOwned(@Param("userId") String userId,
                            @Param("connectionId") String connectionId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "connection_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "provider", javaType = String.class),
            @Arg(column = "environment", javaType = String.class),
            @Arg(column = "resource_policy_json", javaType = String.class),
            @Arg(column = "secret_ref", javaType = String.class),
            @Arg(column = "create_idempotency_key", javaType = String.class),
            @Arg(column = "revision", javaType = long.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "creator_id", javaType = String.class),
            @Arg(column = "creator_name", javaType = String.class),
            @Arg(column = "last_updater_id", javaType = String.class),
            @Arg(column = "last_updater_name", javaType = String.class)
    })
    @Select("""
            SELECT id, connection_id, user_id, name, provider, environment, resource_policy_json,
                   secret_ref, create_idempotency_key, revision, enabled,
                   creator_id, creator_name, last_updater_id, last_updater_name
            FROM sq_connection
            WHERE user_id = #{userId} AND create_idempotency_key = #{createIdempotencyKey}
              AND del_flag = '0'
            """)
    ConnectionRow findByIdempotencyKey(@Param("userId") String userId,
                                       @Param("createIdempotencyKey") String createIdempotencyKey);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "connection_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "provider", javaType = String.class),
            @Arg(column = "environment", javaType = String.class),
            @Arg(column = "resource_policy_json", javaType = String.class),
            @Arg(column = "secret_ref", javaType = String.class),
            @Arg(column = "create_idempotency_key", javaType = String.class),
            @Arg(column = "revision", javaType = long.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "creator_id", javaType = String.class),
            @Arg(column = "creator_name", javaType = String.class),
            @Arg(column = "last_updater_id", javaType = String.class),
            @Arg(column = "last_updater_name", javaType = String.class)
    })
    @Select("""
            <script>
            SELECT id, connection_id, user_id, name, provider, environment, resource_policy_json,
                   secret_ref, create_idempotency_key, revision, enabled,
                   creator_id, creator_name, last_updater_id, last_updater_name
            FROM sq_connection
            WHERE user_id = #{userId} AND del_flag = '0'
              <if test="cursorId != null">AND id &lt; #{cursorId}</if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ConnectionRow> listByOwner(@Param("userId") String userId,
                                    @Param("cursorId") Long cursorId, @Param("limit") int limit);

    @Update("""
            UPDATE sq_connection
            SET resource_policy_json = COALESCE(#{resourcePolicyJson}, resource_policy_json),
                enabled = COALESCE(#{enabled}, enabled),
                revision = revision + 1,
                last_updater_id = #{userId},
                last_updater_name = #{updaterName}
            WHERE user_id = #{userId} AND connection_id = #{connectionId}
              AND revision = #{expectedRevision} AND del_flag = '0'
            """)
    int updateOwned(@Param("userId") String userId,
                    @Param("connectionId") String connectionId,
                    @Param("expectedRevision") long expectedRevision,
                    @Param("resourcePolicyJson") String resourcePolicyJson,
                    @Param("enabled") Boolean enabled,
                    @Param("updaterName") String updaterName);
}
