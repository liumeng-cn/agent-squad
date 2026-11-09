package com.agentsquad.squad.mapper;

import com.agentsquad.squad.domain.enums.WorkerDefinitionStatus;
import com.agentsquad.squad.domain.entity.WorkerDefinitionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Worker 能力定义的
 * <p>
 * 发布只插入不可变正文；状态通过乐观版本单独更新。
 */
@Mapper
public interface WorkerDefinitionMapper {

    String COLUMNS = "id, definition_id, worker_key, worker_version, status, version, "
            + "definition_json, publish_key, payload_hash, last_status_key, last_status_actor, last_status_hash";

    int insert(@Param("definitionId") String definitionId, @Param("workerKey") String workerKey,
               @Param("workerVersion") String workerVersion, @Param("definitionJson") String definitionJson,
               @Param("publishKey") String publishKey, @Param("payloadHash") String payloadHash,
               @Param("actorId") String actorId, @Param("actorName") String actorName);

    WorkerDefinitionDO findByPublishKey(@Param("actorId") String actorId, @Param("publishKey") String publishKey);

    WorkerDefinitionDO findByDefinitionId(@Param("definitionId") String definitionId);

    List<WorkerDefinitionDO> listAfter(@Param("afterId") long afterId, @Param("limit") int limit);

    int updateStatus(@Param("definitionId") String definitionId, @Param("expectedVersion") long expectedVersion,
                     @Param("status") WorkerDefinitionStatus status, @Param("idempotencyKey") String idempotencyKey,
                     @Param("payloadHash") String payloadHash, @Param("actorId") String actorId,
                     @Param("actorName") String actorName);

    int countEnabled(@Param("workerKey") String workerKey,
                     @Param("workerVersion") String workerVersion);

    List<WorkerDefinitionDO> listEnabledAfter(@Param("afterId") long afterId,
                                                @Param("limit") int limit);

    WorkerDefinitionDO findEnabled(@Param("workerKey") String workerKey,
                                    @Param("workerVersion") String workerVersion);

    WorkerDefinitionDO findPublished(@Param("workerKey") String workerKey,
                                      @Param("workerVersion") String workerVersion);
}
