package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.v2.dto.ConfigRevisionDTO;
import com.agentsquad.squad.v2.dto.ToolSaveDTO;
import com.agentsquad.squad.v2.dto.ToolTestRunDTO;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.service.CredentialService;
import com.agentsquad.squad.v2.service.McpServerConfigService;
import com.agentsquad.squad.v2.service.ToolConfigService;
import com.agentsquad.squad.v2.vo.BuiltinToolOptionVO;
import com.agentsquad.squad.v2.vo.McpRemoteToolVO;
import com.agentsquad.squad.v2.vo.McpServerOptionVO;
import com.agentsquad.squad.v2.vo.ToolDetailVO;
import com.agentsquad.squad.v2.vo.ToolTestRunVO;
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
 * 工具配置接口
 * <p>
 * 维护内置、HTTP 与 MCP 三类工具目录、配置版本历史、启停、逻辑删除与试运行；工具不允许单独发布。
 */
@RestController
@RequestMapping("/api/v2/agents/management/tools")
public class ToolConfigController {
    @Resource
    private ToolConfigService service;

    @Resource
    private McpServerConfigService mcpServers;

    @Resource
    private CredentialService credentials;

    /**
     * 查询工具列表
     */
    @GetMapping
    public ApiResult<List<ToolVO>> list() {
        return ApiResult.success(service.list());
    }

    /**
     * 新增工具
     */
    @PostMapping
    public ApiResult<ToolDetailVO> create(@Valid @RequestBody ToolSaveDTO request) {
        return ApiResult.success(service.create(request));
    }

    /**
     * 试运行工具
     */
    @PostMapping("/test-run")
    public ApiResult<ToolTestRunVO> testRun(@Valid @RequestBody ToolTestRunDTO request) {
        return ApiResult.success(service.testRun(request));
    }

    /**
     * 查询内置实现
     */
    @GetMapping("/builtin-options")
    public ApiResult<List<BuiltinToolOptionVO>> builtinOptions() {
        return ApiResult.success(service.builtinOptions());
    }

    /**
     * 查询可选 MCP 服务（仅启用且就绪）
     */
    @GetMapping("/mcp-options")
    public ApiResult<List<McpServerOptionVO>> mcpOptions() {
        return ApiResult.success(mcpServers.options());
    }

    /**
     * 发现指定 MCP 服务当前配置版本的远程工具
     */
    @GetMapping("/mcp-options/{mcpServerId}/remote-tools")
    public ApiResult<List<McpRemoteToolVO>> remoteTools(@PathVariable long mcpServerId) {
        return ApiResult.success(mcpServers.remoteTools(mcpServerId));
    }

    /**
     * 查询凭据引用
     */
    @GetMapping("/credential-options")
    public ApiResult<List<String>> credentialOptions() {
        return ApiResult.success(credentials.names());
    }

    /**
     * 查询工具详情
     */
    @GetMapping("/{toolId}")
    public ApiResult<ToolDetailVO> detail(@PathVariable long toolId) {
        return ApiResult.success(service.detail(toolId));
    }

    /**
     * 保存工具改动（自动生成新配置版本）
     */
    @PutMapping("/{toolId}")
    public ApiResult<ToolDetailVO> save(@PathVariable long toolId, @Valid @RequestBody ToolSaveDTO request) {
        return ApiResult.success(service.save(toolId, request));
    }

    /**
     * 启用工具
     */
    @PostMapping("/{toolId}/enable")
    public ApiResult<ToolVO> enable(@PathVariable long toolId, @Valid @RequestBody ConfigRevisionDTO request) {
        return ApiResult.success(service.changeStatus(toolId, ConfigStatus.ENABLED, request.getRevision()));
    }

    /**
     * 停用工具
     */
    @PostMapping("/{toolId}/disable")
    public ApiResult<ToolVO> disable(@PathVariable long toolId, @Valid @RequestBody ConfigRevisionDTO request) {
        return ApiResult.success(service.changeStatus(toolId, ConfigStatus.DISABLED, request.getRevision()));
    }

    /**
     * 逻辑删除工具
     */
    @PostMapping("/{toolId}/delete")
    public ApiResult<Void> delete(@PathVariable long toolId, @Valid @RequestBody ConfigRevisionDTO request) {
        service.delete(toolId, request.getRevision());
        return ApiResult.success(null);
    }

    /**
     * 只读查看历史配置版本
     */
    @GetMapping("/{toolId}/versions/{versionId}")
    public ApiResult<ToolVersionVO> version(@PathVariable long toolId, @PathVariable long versionId) {
        return ApiResult.success(service.readVersion(toolId, versionId));
    }
}
