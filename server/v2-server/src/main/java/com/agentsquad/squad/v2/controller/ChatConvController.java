package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.dto.PageDTO;
import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.common.vo.PageVO;
import com.agentsquad.squad.v2.dto.ConvArchiveDTO;
import com.agentsquad.squad.v2.dto.ConvRenameDTO;
import com.agentsquad.squad.v2.service.ChatConvDirectoryService;
import com.agentsquad.squad.v2.service.ChatConvService;
import com.agentsquad.squad.v2.vo.ConvSummaryVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 会话接口
 * <p>
 * 为当前登录用户管理对话目录。
 */
@RestController
@RequestMapping("/api/v2/chat/convs")
public class ChatConvController {
    @Resource
    private ChatConvDirectoryService sessions;

    @Resource
    private ChatConvService convs;

    /**
     * 获取会话列表
     */
    @PostMapping("/page")
    public ApiResult<PageVO<ConvSummaryVO>> list(@Valid @RequestBody PageDTO request) {
        return ApiResult.success(sessions.list(CurrentUserContext.getUserId(), request));
    }

    /**
     * 查询对话详情
     */
    @GetMapping("/{convId}")
    public ApiResult<ConvSummaryVO> read(@PathVariable String convId) {
        return ApiResult.success(sessions.read(convId, CurrentUserContext.getUserId()));
    }

    /**
     * 修改会话名称
     */
    @PatchMapping("/{convId}")
    public ApiResult<ConvSummaryVO> rename(@PathVariable String convId, @Valid @RequestBody ConvRenameDTO request) {
        return ApiResult.success(sessions.rename(convId, CurrentUserContext.getUserId(), request));
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/{convId}")
    public ApiResult<Void> delete(@PathVariable String convId) {
        convs.delete(convId, CurrentUserContext.getUserId());
        return ApiResult.success(null);
    }

    /**
     * 归档对话
     */
    @PatchMapping("/{convId}/archive")
    public ApiResult<Void> archive(@PathVariable String convId, @Valid @RequestBody ConvArchiveDTO request) {
        convs.archive(convId, CurrentUserContext.getUserId(), request.isArchived());
        return ApiResult.success(null);
    }
}
