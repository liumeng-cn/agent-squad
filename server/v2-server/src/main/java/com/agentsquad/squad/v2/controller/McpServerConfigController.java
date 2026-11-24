package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.v2.dto.ConfigRevisionDTO;
import com.agentsquad.squad.v2.dto.McpServerProbeDTO;
import com.agentsquad.squad.v2.dto.McpServerSaveDTO;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.service.CredentialService;
import com.agentsquad.squad.v2.service.McpServerConfigService;
import com.agentsquad.squad.v2.vo.McpProbeVO;
import com.agentsquad.squad.v2.vo.McpServerDetailVO;
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
 * MCP 服务配置接口
 * <p>
 * 维护远程 MCP 服务目录、配置版本历史、启停、逻辑删除与实时探测；MCP 不允许单独发布。
 */
@RestController
@RequestMapping("/api/v2/agents/management/mcp-servers")
public class McpServerConfigController {
    @Resource
    private McpServerConfigService service;

    @Resource
    private CredentialService credentials;

    /**
     * 查询服务列表
     */
    @GetMapping
    public ApiResult<List<McpServerVO>> list() {
        return ApiResult.success(service.list());
    }

    /**
     * 新增服务
     */
    @PostMapping
    public ApiResult<McpServerDetailVO> create(@Valid @RequestBody McpServerSaveDTO request) {
        return ApiResult.success(service.create(request));
    }

    /**
     * 测试连接
     */
    @PostMapping("/probe")
    public ApiResult<McpProbeVO> probe(@Valid @RequestBody McpServerProbeDTO request) {
        return ApiResult.success(service.probe(request));
    }

    /**
     * 查询凭据引用
     */
    @GetMapping("/credential-options")
    public ApiResult<List<String>> credentialOptions() {
        return ApiResult.success(credentials.names());
    }

    /**
     * 查询服务详情
     */
    @GetMapping("/{mcpServerId}")
    public ApiResult<McpServerDetailVO> detail(@PathVariable long mcpServerId) {
        return ApiResult.success(service.detail(mcpServerId));
    }

    /**
     * 保存服务改动（自动生成新配置版本）
     */
    @PutMapping("/{mcpServerId}")
    public ApiResult<McpServerDetailVO> save(@PathVariable long mcpServerId,
                                             @Valid @RequestBody McpServerSaveDTO request) {
        return ApiResult.success(service.save(mcpServerId, request));
    }

    /**
     * 启用服务
     */
    @PostMapping("/{mcpServerId}/enable")
    public ApiResult<McpServerVO> enable(@PathVariable long mcpServerId,
                                         @Valid @RequestBody ConfigRevisionDTO request) {
        return ApiResult.success(service.changeStatus(mcpServerId, ConfigStatus.ENABLED, request.getRevision()));
    }

    /**
     * 停用服务
     */
    @PostMapping("/{mcpServerId}/disable")
    public ApiResult<McpServerVO> disable(@PathVariable long mcpServerId,
                                          @Valid @RequestBody ConfigRevisionDTO request) {
        return ApiResult.success(service.changeStatus(mcpServerId, ConfigStatus.DISABLED, request.getRevision()));
    }

    /**
     * 逻辑删除服务
     */
    @PostMapping("/{mcpServerId}/delete")
    public ApiResult<Void> delete(@PathVariable long mcpServerId,
                                  @Valid @RequestBody ConfigRevisionDTO request) {
        service.delete(mcpServerId, request.getRevision());
        return ApiResult.success(null);
    }

    /**
     * 只读查看历史配置版本
     */
    @GetMapping("/{mcpServerId}/versions/{versionId}")
    public ApiResult<McpServerVersionVO> version(@PathVariable long mcpServerId, @PathVariable long versionId) {
        return ApiResult.success(service.readVersion(mcpServerId, versionId));
    }
}
