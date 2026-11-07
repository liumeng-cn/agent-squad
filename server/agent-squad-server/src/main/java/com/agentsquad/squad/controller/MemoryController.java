package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.identity.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import com.agentsquad.squad.domain.dto.MemoryCreate;
import com.agentsquad.squad.domain.dto.MemoryMutationResult;
import com.agentsquad.squad.domain.dto.MemoryPage;
import com.agentsquad.squad.domain.dto.MemoryUpdate;
import com.agentsquad.squad.memory.service.MemoryService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人显式记忆设置HTTP入口。
 * <p>
 * 登录态决定所有者，写入必须通过CSRF；响应同时说明已提交内容与旧执行受限的影响。
 */
@RestController
@RequestMapping("/api/v1/memories")
public class MemoryController {
    @Resource
    private MemoryService memories;

    @GetMapping
    public ApiResult<MemoryPage> list(HttpServletRequest servletRequest,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.success(memories.list(UserContext.current(servletRequest).userId(), cursor, limit));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<MemoryMutationResult> create(HttpServletRequest servletRequest,
                                                  @Valid @RequestBody MemoryCreate request) {
        return ApiResult.success(memories.create(UserContext.current(servletRequest).userId(), request));
    }

    @PatchMapping("/{memoryId}")
    public ApiResult<MemoryMutationResult> update(HttpServletRequest servletRequest,
                                                  @PathVariable String memoryId,
                                                  @Valid @RequestBody MemoryUpdate request) {
        return ApiResult.success(memories.update(UserContext.current(servletRequest).userId(), memoryId, request));
    }

    @DeleteMapping("/{memoryId}")
    public ApiResult<MemoryMutationResult> delete(HttpServletRequest servletRequest,
                                                  @PathVariable String memoryId,
                                                  @RequestParam long expectedVersion) {
        return ApiResult.success(memories.delete(UserContext.current(servletRequest).userId(), memoryId, expectedVersion));
    }
}
