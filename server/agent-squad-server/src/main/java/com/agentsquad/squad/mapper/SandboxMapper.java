package com.agentsquad.squad.mapper;

import com.agentsquad.squad.domain.entity.SandboxBindingDO;
import com.agentsquad.squad.domain.entity.SandboxRunDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 沙箱执行与固定会话身份的
 * <p>
 * 原生调用和业务键分别去重；运行失联不自动回到待执行状态。
 */
@Mapper
public interface SandboxMapper {
    SandboxBindingDO binding(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int activeWorker(@Param("userId") String userId, @Param("bindingId") String bindingId,
                     @Param("guardToken") String guardToken);

    SandboxRunDO prior(@Param("userId") String userId, @Param("bindingId") String bindingId,
                        @Param("logicalKey") String logicalKey, @Param("turnId") String turnId,
                        @Param("toolCallId") String toolCallId);

    SandboxRunDO find(@Param("userId") String userId, @Param("executionId") String executionId);

    int insert(@Param("executionId") String executionId, @Param("userId") String userId,
               @Param("scopeId") String scopeId, @Param("bindingId") String bindingId,
               @Param("logicalKey") String logicalKey, @Param("turnId") String turnId,
               @Param("toolCallId") String toolCallId, @Param("requestHash") String requestHash,
               @Param("deadlineAt") java.time.LocalDateTime deadlineAt);

    int finish(@Param("userId") String userId, @Param("executionId") String executionId,
               @Param("status") String status, @Param("resultJson") String resultJson);

    java.util.List<SandboxRunDO> expired();

    int expireOne(@Param("userId") String userId, @Param("executionId") String executionId);
}
