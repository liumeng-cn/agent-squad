package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.identity.UserContext;
import com.agentsquad.squad.model.dto.ConnectionCreateDTO;
import com.agentsquad.squad.model.dto.ConnectionUpdateDTO;
import com.agentsquad.squad.model.vo.ConnectionPageVO;
import com.agentsquad.squad.model.vo.ConnectionVO;
import com.agentsquad.squad.service.ConnectionService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 连接管理接口。归属取认证会话；密钥只在请求体出现，任何响应不回显
 */
@RestController
@RequestMapping("/api/v1")
public class ConnectionController {

    @Resource
    private ConnectionService connectionService;

    /**
     * 创建当前用户的连接，密钥加密入库，同幂等键复用原连接。
     */
    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<ConnectionVO> create(
            HttpServletRequest servletRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConnectionCreateDTO request) {
        return ApiResult.success(connectionService.create(UserContext.current(servletRequest),
                idempotencyKey, request));
    }

    /**
     * 分页列出当前用户的连接。
     */
    @GetMapping("/connections")
    public ApiResult<ConnectionPageVO> list(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.success(connectionService.list(UserContext.current(servletRequest).userId(), cursor, limit));
    }

    /**
     * 读取当前用户单个连接视图，仅含 secretPresent。
     */
    @GetMapping("/connections/{connectionId}")
    public ApiResult<ConnectionVO> get(
            HttpServletRequest servletRequest,
            @PathVariable String connectionId) {
        return ApiResult.success(connectionService.get(UserContext.current(servletRequest).userId(), connectionId));
    }

    /**
     * 启停或更新策略、轮换密钥；revision 随成功更新递增。
     */
    @PatchMapping("/connections/{connectionId}")
    public ApiResult<ConnectionVO> update(
            HttpServletRequest servletRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String connectionId,
            @Valid @RequestBody ConnectionUpdateDTO request) {
        return ApiResult.success(connectionService.update(UserContext.current(servletRequest),
                connectionId, idempotencyKey, request));
    }
}
