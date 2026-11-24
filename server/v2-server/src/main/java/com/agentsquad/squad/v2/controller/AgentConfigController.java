package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.v2.dto.AgentPublishDTO;
import com.agentsquad.squad.v2.dto.AgentSaveDTO;
import com.agentsquad.squad.v2.dto.ConfigRevisionDTO;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.service.AgentConfigService;
import com.agentsquad.squad.v2.vo.AgentConfigVO;
import com.agentsquad.squad.v2.vo.AgentConfigVersionVO;
import com.agentsquad.squad.v2.vo.AgentDetailVO;
import com.agentsquad.squad.v2.vo.AgentPublishPreviewVO;
import com.agentsquad.squad.v2.vo.AgentReleaseDetailVO;
import com.agentsquad.squad.v2.vo.AgentReleaseSubagentDetailVO;
import com.agentsquad.squad.v2.vo.AgentReleaseToolDetailVO;
import com.agentsquad.squad.v2.vo.SubagentOptionVO;
import com.agentsquad.squad.v2.vo.ToolOptionVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 配置与 Master 发布定格接口
 * <p>
 * 维护 Master 与 Sub 的配置版本历史、工具与委派关联，并提供仅面向 Master 的发布预检、发布定格及发布快照只读穿透查询。
 */
@RestController
@RequestMapping("/api/v2/agents/management/agents")
public class AgentConfigController {
    @Resource
    private AgentConfigService service;

    /**
     * 查询 Agent 列表
     */
    @GetMapping
    public ApiResult<List<AgentConfigVO>> list() {
        return ApiResult.success(service.list());
    }

    /**
     * 新增 Agent
     */
    @PostMapping
    public ApiResult<AgentDetailVO> create(@Valid @RequestBody AgentSaveDTO request) {
        return ApiResult.success(service.create(request));
    }

    /**
     * 查询可绑定工具（仅启用且就绪）
     */
    @GetMapping("/tool-options")
    public ApiResult<List<ToolOptionVO>> toolOptions() {
        return ApiResult.success(service.toolOptions());
    }

    /**
     * 查询可委派子 Agent（仅启用且就绪）
     */
    @GetMapping("/subagent-options")
    public ApiResult<List<SubagentOptionVO>> subagentOptions() {
        return ApiResult.success(service.subagentOptions());
    }

    /**
     * 查询 Agent 详情
     */
    @GetMapping("/{agentId}")
    public ApiResult<AgentDetailVO> detail(@PathVariable long agentId) {
        return ApiResult.success(service.detail(agentId));
    }

    /**
     * 保存 Agent 改动（自动生成新配置版本）
     */
    @PutMapping("/{agentId}")
    public ApiResult<AgentDetailVO> save(@PathVariable long agentId, @Valid @RequestBody AgentSaveDTO request) {
        return ApiResult.success(service.save(agentId, request));
    }

    /**
     * 启用 Agent
     */
    @PostMapping("/{agentId}/enable")
    public ApiResult<AgentConfigVO> enable(@PathVariable long agentId, @Valid @RequestBody ConfigRevisionDTO request) {
        return ApiResult.success(service.changeStatus(agentId, ConfigStatus.ENABLED, request.getRevision()));
    }

    /**
     * 停用 Agent
     */
    @PostMapping("/{agentId}/disable")
    public ApiResult<AgentConfigVO> disable(@PathVariable long agentId, @Valid @RequestBody ConfigRevisionDTO request) {
        return ApiResult.success(service.changeStatus(agentId, ConfigStatus.DISABLED, request.getRevision()));
    }

    /**
     * 逻辑删除 Agent
     */
    @PostMapping("/{agentId}/delete")
    public ApiResult<Void> delete(@PathVariable long agentId, @Valid @RequestBody ConfigRevisionDTO request) {
        service.delete(agentId, request.getRevision());
        return ApiResult.success(null);
    }

    /**
     * 只读查看历史配置版本
     */
    @GetMapping("/{agentId}/versions/{versionId}")
    public ApiResult<AgentConfigVersionVO> version(@PathVariable long agentId, @PathVariable long versionId) {
        return ApiResult.success(service.readVersion(agentId, versionId));
    }

    /**
     * Master 发布预检
     */
    @GetMapping("/{agentId}/publish-preview")
    public ApiResult<AgentPublishPreviewVO> publishPreview(@PathVariable long agentId) {
        return ApiResult.success(service.publishPreview(agentId));
    }

    /**
     * Master 确认发布并定格快照
     */
    @PostMapping("/{agentId}/publish")
    public ApiResult<AgentConfigVO> publish(@PathVariable long agentId, @Valid @RequestBody AgentPublishDTO request) {
        return ApiResult.success(service.publish(agentId, request));
    }

    /**
     * 只读查看 Master 发布快照详情
     */
    @GetMapping("/{agentId}/releases/{releaseId}")
    public ApiResult<AgentReleaseDetailVO> release(@PathVariable long agentId, @PathVariable long releaseId) {
        return ApiResult.success(service.readRelease(agentId, releaseId));
    }

    /**
     * 沿发布快照上下文只读查看定格的 Sub 详情
     */
    @GetMapping("/{agentId}/releases/{releaseId}/subagents/{subagentId}")
    public ApiResult<AgentReleaseSubagentDetailVO> releaseSubagent(@PathVariable long agentId,
                                                                   @PathVariable long releaseId,
                                                                   @PathVariable long subagentId) {
        return ApiResult.success(service.readReleaseSubagent(agentId, releaseId, subagentId));
    }

    /**
     * 沿发布快照上下文只读查看定格的工具与 MCP 详情
     */
    @GetMapping("/{agentId}/releases/{releaseId}/tools/{toolId}")
    public ApiResult<AgentReleaseToolDetailVO> releaseTool(@PathVariable long agentId,
                                                           @PathVariable long releaseId,
                                                           @PathVariable long toolId) {
        return ApiResult.success(service.readReleaseTool(agentId, releaseId, toolId));
    }
}
