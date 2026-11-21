package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.v2.dto.AgentRevisionDTO;
import com.agentsquad.squad.v2.dto.ToolCreateDTO;
import com.agentsquad.squad.v2.dto.ToolDraftDTO;
import com.agentsquad.squad.v2.service.McpServerConfigService;
import com.agentsquad.squad.v2.service.ToolConfigService;
import com.agentsquad.squad.v2.vo.McpServerVersionOptionVO;
import com.agentsquad.squad.v2.vo.ToolVO;
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
 * 工具管理接口
 * <p>
 * 提供目录查询、草稿维护和版本发布。
 */
@RestController
@RequestMapping("/api/v2/agents/management/tools")
public class ToolConfigController {
    @Resource
    private ToolConfigService service;

    @Resource
    private McpServerConfigService mcpServers;

    /**
     * 查询连接选项
     */
    @GetMapping("/mcp-options")
    public ApiResult<List<McpServerVersionOptionVO>> mcpOptions() {
        return ApiResult.success(mcpServers.options());
    }

    /**
     * 查询目录
     */
    @GetMapping
    public ApiResult<List<ToolVO>> list() {
        return ApiResult.success(service.list());
    }

    /**
     * 新增目录
     */
    @PostMapping
    public ApiResult<ToolVO> create(@Valid @RequestBody ToolCreateDTO request) {
        return ApiResult.success(service.create(request));
    }

    /**
     * 查询版本
     */
    @GetMapping("/{resourceId}/versions")
    public ApiResult<List<ToolVersionVO>> versions(@PathVariable long resourceId) {
        return ApiResult.success(service.listVersions(resourceId));
    }

    /**
     * 创建草稿
     */
    @PostMapping("/{resourceId}/versions")
    public ApiResult<ToolVersionVO> createDraft(@PathVariable long resourceId, @Valid @RequestBody ToolDraftDTO request) {
        return ApiResult.success(service.saveDraft(resourceId, null, request));
    }

    /**
     * 保存草稿
     */
    @PutMapping("/{resourceId}/versions/{versionId}")
    public ApiResult<ToolVersionVO> saveDraft(@PathVariable long resourceId, @PathVariable long versionId,
                                              @Valid @RequestBody ToolDraftDTO request) {
        return ApiResult.success(service.saveDraft(resourceId, versionId, request));
    }

    /**
     * 发布版本
     */
    @PostMapping("/{resourceId}/versions/{versionId}/publish")
    public ApiResult<ToolVersionVO> publish(@PathVariable long resourceId, @PathVariable long versionId,
                                            @Valid @RequestBody AgentRevisionDTO request) {
        return ApiResult.success(service.publish(resourceId, versionId, request.getRevision()));
    }
}
