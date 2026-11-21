package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.v2.dto.AgentCreateDTO;
import com.agentsquad.squad.v2.dto.AgentDraftDTO;
import com.agentsquad.squad.v2.dto.AgentRevisionDTO;
import com.agentsquad.squad.v2.service.AgentConfigService;
import com.agentsquad.squad.v2.service.ToolConfigService;
import com.agentsquad.squad.v2.vo.AgentConfigVO;
import com.agentsquad.squad.v2.vo.AgentConfigVersionVO;
import com.agentsquad.squad.v2.vo.ToolVersionVO;
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
 * Agent配置接口
 * <p>
 * 提供目录、草稿以及版本发布和切换操作。
 */
@RestController
@RequestMapping("/api/v2/agents/management/agents")
public class AgentConfigController {
    @Resource
    private AgentConfigService service;

    @Resource
    private ToolConfigService toolConfig;

    /** 查询工具选项 */
    @GetMapping("/tool-options")
    public ApiResult<List<ToolVersionVO>> toolOptions() {
        return ApiResult.success(toolConfig.options());
    }

    /**
     * 查询Agent
     */
    @GetMapping
    public ApiResult<List<AgentConfigVO>> list() {
        return ApiResult.success(service.list());
    }

    /**
     * 新增Agent
     */
    @PostMapping
    public ApiResult<AgentConfigVO> create(@Valid @RequestBody AgentCreateDTO request) {
        return ApiResult.success(service.create(request));
    }

    /**
     * 查询版本
     */
    @GetMapping("/{agentId}/versions")
    public ApiResult<List<AgentConfigVersionVO>> listVersions(@PathVariable long agentId) {
        return ApiResult.success(service.listVersions(agentId));
    }

    /**
     * 读取版本
     */
    @GetMapping("/{agentId}/versions/{versionId}")
    public ApiResult<AgentConfigVersionVO> version(@PathVariable long agentId, @PathVariable long versionId) {
        return ApiResult.success(service.readVersion(agentId, versionId));
    }

    /**
     * 创建草稿
     */
    @PostMapping("/{agentId}/versions")
    public ApiResult<AgentConfigVersionVO> createDraft(@PathVariable long agentId, @Valid @RequestBody AgentDraftDTO request) {
        return ApiResult.success(service.saveDraft(agentId, null, request));
    }

    /**
     * 保存草稿
     */
    @PutMapping("/{agentId}/versions/{versionId}")
    public ApiResult<AgentConfigVersionVO> saveDraft(@PathVariable long agentId, @PathVariable long versionId, @Valid @RequestBody AgentDraftDTO request) {
        return ApiResult.success(service.saveDraft(agentId, versionId, request));
    }

    /**
     * 发布版本
     */
    @PostMapping("/{agentId}/versions/{versionId}/publish")
    public ApiResult<AgentConfigVO> publish(@PathVariable long agentId, @PathVariable long versionId, @Valid @RequestBody AgentRevisionDTO request) {
        return ApiResult.success(service.publish(agentId, versionId, request.getRevision()));
    }

    /**
     * 切换版本
     */
    @PostMapping("/{agentId}/versions/{versionId}/activate")
    public ApiResult<AgentConfigVO> activate(@PathVariable long agentId, @PathVariable long versionId, @Valid @RequestBody AgentRevisionDTO request) {
        return ApiResult.success(service.activate(agentId, versionId, request.getRevision()));
    }
}
