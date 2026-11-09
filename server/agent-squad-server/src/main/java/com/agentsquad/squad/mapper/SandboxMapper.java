package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.bo.SandboxBindingBO;
import com.agentsquad.squad.model.po.SandboxRunPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 沙箱执行与固定会话身份的
 * <p>
 * 原生调用和业务键分别去重；运行失联不自动回到待执行状态。
 */
@Mapper
public interface SandboxMapper {
    SandboxBindingBO binding(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    int activeWorker(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                     @Param("guardToken") String guardToken);

    SandboxRunPO prior(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                       @Param("logicalKey") String logicalKey, @Param("turnId") String turnId,
                       @Param("toolCallId") String toolCallId);

    SandboxRunPO find(@Param("userId") Long userId, @Param("executionId") Long executionId);

    int insert(@Param("executionId") Long executionId, @Param("userId") Long userId,
               @Param("scopeId") Long scopeId, @Param("bindingId") Long bindingId,
               @Param("logicalKey") String logicalKey, @Param("turnId") String turnId,
               @Param("toolCallId") String toolCallId, @Param("requestHash") String requestHash,
               @Param("deadlineAt") java.time.LocalDateTime deadlineAt);

    int finish(@Param("userId") Long userId, @Param("executionId") Long executionId,
               @Param("status") String status, @Param("resultJson") String resultJson);

    java.util.List<SandboxRunPO> expired();

    int expireOne(@Param("userId") Long userId, @Param("executionId") Long executionId);
}
