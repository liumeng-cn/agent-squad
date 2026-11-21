package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.v2.dto.AgentRevisionDTO;
import com.agentsquad.squad.v2.dto.McpServerCreateDTO;
import com.agentsquad.squad.v2.dto.McpServerDraftDTO;
import com.agentsquad.squad.v2.service.McpServerConfigService;
import com.agentsquad.squad.v2.vo.McpServerVO;
import com.agentsquad.squad.v2.vo.McpServerVersionVO;
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
 * MCP连接管理接口
 * <p>
 * 提供目录查询、草稿维护和版本发布。
 */
@RestController
@RequestMapping("/api/v2/agents/management/mcp-servers")
public class McpServerConfigController {
    @Resource
    private McpServerConfigService service;

    /**
     * 查询目录
     */
    @GetMapping
    public ApiResult<List<McpServerVO>> list() {
        return ApiResult.success(service.list());
    }

    /**
     * 新增目录
     */
    @PostMapping
    public ApiResult<McpServerVO> create(@Valid @RequestBody McpServerCreateDTO request) {
        return ApiResult.success(service.create(request));
    }

    /**
     * 查询版本
     */
    @GetMapping("/{resourceId}/versions")
    public ApiResult<List<McpServerVersionVO>> versions(@PathVariable long resourceId) {
        return ApiResult.success(service.listVersions(resourceId));
    }

    /**
     * 创建草稿
     */
    @PostMapping("/{resourceId}/versions")
    public ApiResult<McpServerVersionVO> createDraft(@PathVariable long resourceId, @Valid @RequestBody McpServerDraftDTO request) {
        return ApiResult.success(service.saveDraft(resourceId, null, request));
    }

    /**
     * 保存草稿
     */
    @PutMapping("/{resourceId}/versions/{versionId}")
    public ApiResult<McpServerVersionVO> saveDraft(@PathVariable long resourceId, @PathVariable long versionId,
                                                   @Valid @RequestBody McpServerDraftDTO request) {
        return ApiResult.success(service.saveDraft(resourceId, versionId, request));
    }

    /**
     * 发布版本
     */
    @PostMapping("/{resourceId}/versions/{versionId}/publish")
    public ApiResult<McpServerVersionVO> publish(@PathVariable long resourceId, @PathVariable long versionId,
                                                 @Valid @RequestBody AgentRevisionDTO request) {
        return ApiResult.success(service.publish(resourceId, versionId, request.getRevision()));
    }
}
