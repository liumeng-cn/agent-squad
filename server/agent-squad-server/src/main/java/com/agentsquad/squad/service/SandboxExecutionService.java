package com.agentsquad.squad.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.domain.enums.RuntimeExceptionCode;
import com.agentsquad.squad.domain.model.SandboxExecutionContext;
import com.agentsquad.squad.domain.model.SandboxInputFile;
import com.agentsquad.squad.domain.model.SandboxPolicy;
import com.agentsquad.squad.domain.dto.SandboxExecuteRequest;
import com.agentsquad.squad.domain.dto.SandboxExecutionResult;
import com.agentsquad.squad.sandbox.infrastructure.HarnessDockerSandboxDriver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsquad.squad.domain.model.ArtifactContentPolicy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Python沙箱的持久受理与隔离执行编排。
 * <p>
 * 运行身份由工具绑定，数据库事务不包容器执行；未知结果保留预算与原执行键。
 */
@Service
@Slf4j
public class SandboxExecutionService {
    @Resource private UserStatusGuardService userStatus;
    @Resource
    private com.agentsquad.squad.service.ArtifactService artifacts;

    @Resource
    private com.agentsquad.squad.mapper.ArtifactMapper artifactMapper;

    @Resource
    private SandboxAccessService access;

    @Resource
    private SandboxRunTransactionService transactions;

    @Resource
    private HarnessDockerSandboxDriver driver;

    @Resource
    private ObjectMapper json;

    public SandboxExecutionResult execute(String userId, String sessionId, String guardToken,
                                          String turnId, String toolCallId, SandboxExecuteRequest request) {
        userStatus.requireActive(userId);
        driver.requireAvailable();
        SandboxPolicy.validate(request);
        var context = access.resolve(userId, sessionId, guardToken, turnId, toolCallId);
        var inputs = resolveInputs(context, request);
        String hash = hash(request);
        var claim = transactions.begin(context, request, hash);
        var prior = claim.run();
        // 本次新建记录才有派发权，唯一原生调用重放不允许第二次执行。
        if (!claim.newlyAccepted()) {
            return transactions.replay(prior);
        }
        if (!"IN_FLIGHT".equals(prior.getStatus())) {
            return transactions.replay(prior);
        }
        try {
            var result = driver.execute(request, inputs);
            var outcome = transactions.finish(context, prior.getExecutionId(), request, result);
            log.info("沙箱执行结束 executionId={} scopeId={} result={} outputCount={}", prior.getExecutionId(),
                    context.scopeId(), outcome.status(), outcome.stagedArtifacts().size());
            return outcome;
        } catch (Exception exception) {
            transactions.unknown(context, prior.getExecutionId());
            log.warn("沙箱结果待核对 executionId={} scopeId={} reason={}", prior.getExecutionId(), context.scopeId(),
                    exception.getClass().getSimpleName());
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "沙箱结果未能完整提交，预算已保留，禁止自动重放。执行号：" + prior.getExecutionId());
        }
    }

    private java.util.List<SandboxInputFile> resolveInputs(
            SandboxExecutionContext context, SandboxExecuteRequest request) {
        var inputs = new java.util.ArrayList<SandboxInputFile>();
        var paths = new java.util.HashSet<String>();
        long bytes = 0;
        for (var file : request.files()) {
            byte[] content = file.content().getBytes(StandardCharsets.UTF_8);
            paths.add(file.path());
            inputs.add(new SandboxInputFile(file.path(), content));
            bytes += content.length;
        }
        var attached = artifactMapper.listScopeIds(context.userId(), context.scopeId());
        for (String id : request.artifactIds()) {
            if (!attached.contains(id)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "沙箱输入附件不属于当前执行材料");
            }
            var artifact = artifacts.content(context.userId(), id);
            String path = "inputs/" + id + "/" + artifact.getFileName();
            SandboxPolicy.requirePath(path);
            if (!paths.add(path) || artifact.getContent().length > SandboxPolicy.MAX_FILE_BYTES) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "沙箱附件重复或超过单文件限制");
            }
            inputs.add(new SandboxInputFile(path, artifact.getContent()));
            bytes += artifact.getContent().length;
        }
        if (bytes > SandboxPolicy.MAX_TOTAL_BYTES) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "沙箱输入材料总量超限");
        }
        return java.util.List.copyOf(inputs);
    }

    private String hash(SandboxExecuteRequest request) {
        try {
            return ArtifactContentPolicy.digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("沙箱请求无法编码", exception);
        }
    }
}
