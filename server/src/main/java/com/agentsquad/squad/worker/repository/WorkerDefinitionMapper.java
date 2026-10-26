package com.agentsquad.squad.worker.repository;

import com.agentsquad.squad.worker.domain.WorkerDefinitionRow;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Worker 能力定义的数据访问边界。
 * <p>
 * 发布只插入不可变正文；状态通过乐观版本单独更新。
 */
@Mapper
public interface WorkerDefinitionMapper {

    String COLUMNS = "id, definition_id, worker_key, worker_version, status, version, "
            + "definition_json, publish_key, payload_hash, last_status_key, last_status_actor, last_status_hash";

    @Insert("""
            INSERT INTO sq_worker_definition
                (definition_id, worker_key, worker_version, status, definition_json,
                 publish_key, payload_hash, creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{definitionId}, #{workerKey}, #{workerVersion}, 'ENABLED', #{definitionJson},
                 #{publishKey}, #{payloadHash}, #{actorId}, #{actorName}, #{actorId}, #{actorName})
            """)
    int insert(@Param("definitionId") String definitionId, @Param("workerKey") String workerKey,
               @Param("workerVersion") String workerVersion, @Param("definitionJson") String definitionJson,
               @Param("publishKey") String publishKey, @Param("payloadHash") String payloadHash,
               @Param("actorId") String actorId, @Param("actorName") String actorName);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "definition_id", javaType = String.class),
            @Arg(column = "worker_key", javaType = String.class),
            @Arg(column = "worker_version", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "definition_json", javaType = String.class),
            @Arg(column = "publish_key", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "last_status_key", javaType = String.class),
            @Arg(column = "last_status_actor", javaType = String.class),
            @Arg(column = "last_status_hash", javaType = String.class)
    })
    @Select("SELECT " + COLUMNS + " FROM sq_worker_definition WHERE creator_id = #{actorId} AND publish_key = #{publishKey}")
    WorkerDefinitionRow findByPublishKey(@Param("actorId") String actorId, @Param("publishKey") String publishKey);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "definition_id", javaType = String.class),
            @Arg(column = "worker_key", javaType = String.class),
            @Arg(column = "worker_version", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "definition_json", javaType = String.class),
            @Arg(column = "publish_key", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "last_status_key", javaType = String.class),
            @Arg(column = "last_status_actor", javaType = String.class),
            @Arg(column = "last_status_hash", javaType = String.class)
    })
    @Select("SELECT " + COLUMNS + " FROM sq_worker_definition WHERE definition_id = #{definitionId}")
    WorkerDefinitionRow findByDefinitionId(@Param("definitionId") String definitionId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "definition_id", javaType = String.class),
            @Arg(column = "worker_key", javaType = String.class),
            @Arg(column = "worker_version", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "definition_json", javaType = String.class),
            @Arg(column = "publish_key", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "last_status_key", javaType = String.class),
            @Arg(column = "last_status_actor", javaType = String.class),
            @Arg(column = "last_status_hash", javaType = String.class)
    })
    @Select("SELECT " + COLUMNS + " FROM sq_worker_definition "
            + "WHERE id > #{afterId} ORDER BY id LIMIT #{limit}")
    List<WorkerDefinitionRow> listAfter(@Param("afterId") long afterId, @Param("limit") int limit);

    @Update("""
            UPDATE sq_worker_definition
            SET status = #{status}, version = version + 1,
                last_status_key = #{idempotencyKey}, last_status_hash = #{payloadHash},
                last_status_actor = #{actorId},
                last_updater_id = #{actorId}, last_updater_name = #{actorName}
            WHERE definition_id = #{definitionId} AND version = #{expectedVersion}
              AND status <> 'REVOKED'
            """)
    int updateStatus(@Param("definitionId") String definitionId, @Param("expectedVersion") long expectedVersion,
                     @Param("status") String status, @Param("idempotencyKey") String idempotencyKey,
                     @Param("payloadHash") String payloadHash, @Param("actorId") String actorId,
                     @Param("actorName") String actorName);

    @Select("""
            SELECT COUNT(*) FROM sq_worker_definition
            WHERE worker_key = #{workerKey} AND worker_version = #{workerVersion}
              AND status = 'ENABLED'
            """)
    int countEnabled(@Param("workerKey") String workerKey,
                     @Param("workerVersion") String workerVersion);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "definition_id", javaType = String.class),
            @Arg(column = "worker_key", javaType = String.class),
            @Arg(column = "worker_version", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "definition_json", javaType = String.class),
            @Arg(column = "publish_key", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "last_status_key", javaType = String.class),
            @Arg(column = "last_status_actor", javaType = String.class),
            @Arg(column = "last_status_hash", javaType = String.class)
    })
    @Select("SELECT " + COLUMNS + " FROM sq_worker_definition "
            + "WHERE status = 'ENABLED' AND id > #{afterId} ORDER BY id LIMIT #{limit}")
    List<WorkerDefinitionRow> listEnabledAfter(@Param("afterId") long afterId,
                                                @Param("limit") int limit);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "definition_id", javaType = String.class),
            @Arg(column = "worker_key", javaType = String.class),
            @Arg(column = "worker_version", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "definition_json", javaType = String.class),
            @Arg(column = "publish_key", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "last_status_key", javaType = String.class),
            @Arg(column = "last_status_actor", javaType = String.class),
            @Arg(column = "last_status_hash", javaType = String.class)
    })
    @Select("SELECT " + COLUMNS + " FROM sq_worker_definition "
            + "WHERE worker_key = #{workerKey} AND worker_version = #{workerVersion} "
            + "AND status = 'ENABLED'")
    WorkerDefinitionRow findEnabled(@Param("workerKey") String workerKey,
                                    @Param("workerVersion") String workerVersion);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "definition_id", javaType = String.class),
            @Arg(column = "worker_key", javaType = String.class),
            @Arg(column = "worker_version", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "definition_json", javaType = String.class),
            @Arg(column = "publish_key", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "last_status_key", javaType = String.class),
            @Arg(column = "last_status_actor", javaType = String.class),
            @Arg(column = "last_status_hash", javaType = String.class)
    })
    @Select("SELECT " + COLUMNS + " FROM sq_worker_definition "
            + "WHERE worker_key = #{workerKey} AND worker_version = #{workerVersion}")
    WorkerDefinitionRow findPublished(@Param("workerKey") String workerKey,
                                      @Param("workerVersion") String workerVersion);
}
