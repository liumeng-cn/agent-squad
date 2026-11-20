package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.vo.PageVO;
import com.agentsquad.squad.v2.dto.ConvPageQueryDTO;
import com.agentsquad.squad.v2.dto.ConvRenameDTO;
import com.agentsquad.squad.v2.service.ChatConvService;
import com.agentsquad.squad.v2.vo.ConvSummaryVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话目录接口
 * <p>
 * 为当前登录用户维护对话目录；消息与执行入口在聊天接口。
 */
@RestController
@RequestMapping("/api/v2/chat/convs")
public class ChatConvController {
    @Resource
    private ChatConvService convService;

    /**
     * 获取会话列表
     */
    @PostMapping("/page")
    public ApiResult<PageVO<ConvSummaryVO>> page(@Valid @RequestBody ConvPageQueryDTO request) {
        return ApiResult.success(convService.page(request));
    }

    /**
     * 查询对话详情
     */
    @GetMapping("/{convId}")
    public ApiResult<ConvSummaryVO> read(@PathVariable String convId) {
        return ApiResult.success(convService.read(convId));
    }

    /**
     * 修改对话名称
     */
    @PatchMapping("/{convId}")
    public ApiResult<ConvSummaryVO> rename(@PathVariable String convId, @Valid @RequestBody ConvRenameDTO request) {
        return ApiResult.success(convService.rename(convId, request.getTitle(), request.getVersion()));
    }

    /**
     * 归档对话
     */
    @PostMapping("/{convId}/archive")
    public ApiResult<Void> archive(@PathVariable String convId) {
        convService.archive(convId);
        return ApiResult.success(null);
    }

    /**
     * 恢复已归档对话
     */
    @PostMapping("/{convId}/unarchive")
    public ApiResult<Void> unarchive(@PathVariable String convId) {
        convService.unarchive(convId);
        return ApiResult.success(null);
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/{convId}")
    public ApiResult<Void> delete(@PathVariable String convId) {
        convService.delete(convId);
        return ApiResult.success(null);
    }
}
