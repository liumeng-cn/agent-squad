package com.agentsquad.squad.memory.controller;

import com.agentsquad.squad.identity.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import com.agentsquad.squad.memory.dto.MemoryCreate;
import com.agentsquad.squad.memory.dto.MemoryMutationResult;
import com.agentsquad.squad.memory.dto.MemoryPage;
import com.agentsquad.squad.memory.dto.MemoryUpdate;
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
    public MemoryPage list(HttpServletRequest servletRequest,
                           @RequestParam(required = false) String cursor,
                           @RequestParam(defaultValue = "20") int limit) {
        return memories.list(UserContext.current(servletRequest).userId(), cursor, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryMutationResult create(HttpServletRequest servletRequest,
                                       @Valid @RequestBody MemoryCreate request) {
        return memories.create(UserContext.current(servletRequest).userId(), request);
    }

    @PatchMapping("/{memoryId}")
    public MemoryMutationResult update(HttpServletRequest servletRequest,
                                       @PathVariable String memoryId,
                                       @Valid @RequestBody MemoryUpdate request) {
        return memories.update(UserContext.current(servletRequest).userId(), memoryId, request);
    }

    @DeleteMapping("/{memoryId}")
    public MemoryMutationResult delete(HttpServletRequest servletRequest,
                                       @PathVariable String memoryId,
                                       @RequestParam long expectedVersion) {
        return memories.delete(UserContext.current(servletRequest).userId(), memoryId, expectedVersion);
    }
}
