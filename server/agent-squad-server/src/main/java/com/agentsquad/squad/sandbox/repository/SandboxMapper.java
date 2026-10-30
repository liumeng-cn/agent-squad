package com.agentsquad.squad.sandbox.repository;

import com.agentsquad.squad.sandbox.domain.SandboxBindingRow;
import com.agentsquad.squad.sandbox.domain.SandboxRunRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 沙箱执行与固定会话身份的持久边界。
 * <p>
 * 原生调用和业务键分别去重；运行失联不自动回到待执行状态。
 */
@Mapper
public interface SandboxMapper {
    SandboxBindingRow binding(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int activeWorker(@Param("userId") String userId, @Param("bindingId") String bindingId,
                     @Param("guardToken") String guardToken);

    SandboxRunRow prior(@Param("userId") String userId, @Param("bindingId") String bindingId,
                        @Param("logicalKey") String logicalKey, @Param("turnId") String turnId,
                        @Param("toolCallId") String toolCallId);

    SandboxRunRow find(@Param("userId") String userId, @Param("executionId") String executionId);

    int insert(@Param("executionId") String executionId, @Param("userId") String userId,
               @Param("scopeId") String scopeId, @Param("bindingId") String bindingId,
               @Param("logicalKey") String logicalKey, @Param("turnId") String turnId,
               @Param("toolCallId") String toolCallId, @Param("requestHash") String requestHash,
               @Param("deadlineAt") java.time.LocalDateTime deadlineAt);

    int finish(@Param("userId") String userId, @Param("executionId") String executionId,
               @Param("status") String status, @Param("resultJson") String resultJson);

    java.util.List<SandboxRunRow> expired();

    int expireOne(@Param("userId") String userId, @Param("executionId") String executionId);
}
