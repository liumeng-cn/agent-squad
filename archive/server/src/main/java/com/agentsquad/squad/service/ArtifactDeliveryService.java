package com.agentsquad.squad.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.agentsquad.squad.mapper.ArtifactMapper;
import com.agentsquad.squad.model.vo.ArtifactVO;
import com.agentsquad.squad.policy.ArtifactContentPolicy;
import com.agentsquad.squad.model.po.ArtifactStagePO;
import com.agentsquad.squad.model.bo.ArtifactStageViewBO;
import com.agentsquad.squad.mapper.ArtifactDeliveryMapper;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.model.bo.SandboxExecutionContextBO;
import com.agentsquad.squad.policy.SandboxPolicy;
import com.agentsquad.squad.mapper.SandboxMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 隔离产物的暂存与原子发布。
 * <p>
 * 仅接收驱动从运行工作区取回的内容；再次验证Scope与Worker guard后才关联公开产物。
 */
@Service
public class ArtifactDeliveryService {
    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ConversationEventWriterService events;

    @Resource
    private ArtifactDeliveryMapper stages;

    @Resource
    private ArtifactMapper artifacts;

    @Resource
    private ArtifactService artifactService;

    @Resource
    private SandboxAccessService access;

    @Resource
    private SandboxMapper runs;

    /**
     * 沙箱结果落库事务内暂存，不立即向用户公开；同运行同路径只能具有同一内容。
     */
    @Transactional
    public ArtifactStageViewBO stage(SandboxExecutionContextBO context, Long executionId, String path, byte[] content) {
        access.requireCurrent(context);
        SandboxPolicy.requirePath(path);
        var run = runs.find(context.userId(), executionId);
        if (run == null || !run.getBindingId().equals(context.bindingId()) || !run.getScopeId().equals(context.scopeId())
                || !"IN_FLIGHT".equals(run.getStatus())) {
            throw conflict("沙箱运行与产物归属不一致");
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        ArtifactContentPolicy.mediaType(name, content);
        String hash = ArtifactContentPolicy.digest(content);
        ArtifactStagePO prior = stages.findPath(context.userId(), executionId, path);
        if (prior != null) {
            if (!hash.equals(prior.getSha256())) {
                throw conflict("相同工作区路径的内容已变化");
            }
            return view(prior);
        }
        Long id = IdWorker.getId();
        stages.insert(id, context.userId(), context.scopeId(), context.bindingId(), executionId,
                path, name, hash, content, LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        return new ArtifactStageViewBO(id, executionId, name, hash, content.length, "STAGED", null);
    }

    /**
     * 同事务发布内容、Scope关联和暂存状态；重放返回原产物，不读取模型指定宿主文件。
     */
    @Transactional
    public ArtifactVO commit(Long userId, String sessionId, String guardToken, Long stageId, String expectedHash) {
        ArtifactStagePO stage = stages.find(userId, stageId);
        if (stage == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "暂存产物不存在或已过期");
        }
        SandboxExecutionContextBO context = access.resolve(userId, sessionId, guardToken, "artifact-commit", String.valueOf(stageId));
        if (!stage.getScopeId().equals(context.scopeId()) || !stage.getBindingId().equals(context.bindingId())
                || expectedHash == null || !expectedHash.equals(stage.getSha256())) {
            throw conflict("产物归属或摘要不匹配");
        }
        stage = stages.lock(userId, stageId);
        if (stage == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "暂存产物已过期或被清理");
        }
        if ("COMMITTED".equals(stage.getStatus())) {
            return artifactService.get(userId, stage.getArtifactId());
        }
        var run = runs.find(userId, stage.getExecutionId());
        if (run == null || !"SUCCEEDED".equals(run.getStatus()) || !"STAGED".equals(stage.getStatus())
                || !expectedHash.equals(ArtifactContentPolicy.digest(stage.getContent()))) {
            throw conflict("产物运行未成功或内容完整性校验失败");
        }
        String type = ArtifactContentPolicy.mediaType(stage.getFileName(), stage.getContent());
        Long artifactId = IdWorker.getId();
        artifacts.insert(artifactId, userId, stage.getFileName(), type, stage.getContent().length, expectedHash,
                "stage:" + stageId, stage.getContent(), LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
        artifacts.attachScope(userId, context.scopeId(), artifactId);
        var scope = scopes.requireReadable(userId, context.scopeId());
        if ("TASK".equals(scope.getKind())) {
            artifacts.attach(userId, scope.getOwnerId(), artifactId);
        }
        if (stages.commit(userId, stageId, artifactId) != 1) {
            throw conflict("产物提交状态已变化");
        }
        ArtifactVO published = artifactService.get(userId, artifactId);
        events.publish(scope, "artifact-ready:" + stageId, "artifact.ready", published);
        return published;
    }

    private ArtifactStageViewBO view(ArtifactStagePO row) {
        return new ArtifactStageViewBO(row.getStageId(), row.getExecutionId(), row.getFileName(), row.getSha256(),
                row.getContent().length, row.getStatus(), row.getArtifactId());
    }

    private RuntimeBusinessException conflict(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, message);
    }
}
