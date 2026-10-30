package com.agentsquad.squad.artifact.service;

import com.agentsquad.squad.artifact.ArtifactMapper;
import com.agentsquad.squad.artifact.ArtifactService;
import com.agentsquad.squad.artifact.ArtifactView;
import com.agentsquad.squad.artifact.domain.ArtifactContentPolicy;
import com.agentsquad.squad.artifact.domain.ArtifactStageRow;
import com.agentsquad.squad.artifact.dto.ArtifactStageView;
import com.agentsquad.squad.artifact.repository.ArtifactDeliveryMapper;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.sandbox.domain.SandboxExecutionContext;
import com.agentsquad.squad.sandbox.domain.SandboxPolicy;
import com.agentsquad.squad.sandbox.service.SandboxAccessService;
import com.agentsquad.squad.sandbox.repository.SandboxMapper;
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
    private com.agentsquad.squad.conversation.service.ExecutionScopeService scopes;

    @Resource
    private com.agentsquad.squad.conversation.service.ConversationEventWriter events;

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
    public ArtifactStageView stage(SandboxExecutionContext context, String executionId, String path, byte[] content) {
        access.requireCurrent(context);
        SandboxPolicy.requirePath(path);
        var run = runs.find(context.userId(), executionId);
        if (run == null || !run.bindingId().equals(context.bindingId()) || !run.scopeId().equals(context.scopeId())
                || !"IN_FLIGHT".equals(run.status())) {
            throw conflict("沙箱运行与产物归属不一致");
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        ArtifactContentPolicy.mediaType(name, content);
        String hash = ArtifactContentPolicy.digest(content);
        ArtifactStageRow prior = stages.findPath(context.userId(), executionId, path);
        if (prior != null) {
            if (!hash.equals(prior.sha256())) {
                throw conflict("相同工作区路径的内容已变化");
            }
            return view(prior);
        }
        String id = UUID.randomUUID().toString();
        stages.insert(id, context.userId(), context.scopeId(), context.bindingId(), executionId,
                path, name, hash, content, LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        return new ArtifactStageView(id, executionId, name, hash, content.length, "STAGED", null);
    }

    /**
     * 同事务发布内容、Scope关联和暂存状态；重放返回原产物，不读取模型指定宿主文件。
     */
    @Transactional
    public ArtifactView commit(String userId, String sessionId, String guardToken, String stageId, String expectedHash) {
        ArtifactStageRow stage = stages.find(userId, stageId);
        if (stage == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "暂存产物不存在或已过期");
        }
        SandboxExecutionContext context = access.resolve(userId, sessionId, guardToken, "artifact-commit", stageId);
        if (!stage.scopeId().equals(context.scopeId()) || !stage.bindingId().equals(context.bindingId())
                || expectedHash == null || !expectedHash.equals(stage.sha256())) {
            throw conflict("产物归属或摘要不匹配");
        }
        stage = stages.lock(userId, stageId);
        if (stage == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "暂存产物已过期或被清理");
        }
        if ("COMMITTED".equals(stage.status())) {
            return artifactService.get(userId, stage.artifactId());
        }
        var run = runs.find(userId, stage.executionId());
        if (run == null || !"SUCCEEDED".equals(run.status()) || !"STAGED".equals(stage.status())
                || !expectedHash.equals(ArtifactContentPolicy.digest(stage.content()))) {
            throw conflict("产物运行未成功或内容完整性校验失败");
        }
        String type = ArtifactContentPolicy.mediaType(stage.fileName(), stage.content());
        String artifactId = UUID.randomUUID().toString();
        artifacts.insert(artifactId, userId, stage.fileName(), type, stage.content().length, expectedHash,
                "stage:" + stageId, stage.content(), LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
        artifacts.attachScope(userId, context.scopeId(), artifactId);
        var scope = scopes.requireReadable(userId, context.scopeId());
        if ("TASK".equals(scope.kind())) {
            artifacts.attach(userId, scope.ownerId(), artifactId);
        }
        if (stages.commit(userId, stageId, artifactId) != 1) {
            throw conflict("产物提交状态已变化");
        }
        ArtifactView published = artifactService.get(userId, artifactId);
        events.publish(scope, "artifact-ready:" + stageId, "artifact.ready", published);
        return published;
    }

    private ArtifactStageView view(ArtifactStageRow row) {
        return new ArtifactStageView(row.stageId(), row.executionId(), row.fileName(), row.sha256(),
                row.content().length, row.status(), row.artifactId());
    }

    private RuntimeBusinessException conflict(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, message);
    }
}
