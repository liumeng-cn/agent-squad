package com.agentsquad.squad.tools.connection;

import com.agentsquad.squad.identity.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import com.agentsquad.squad.tools.dto.ConnectionCreateRequest;
import com.agentsquad.squad.tools.dto.ConnectionPage;
import com.agentsquad.squad.tools.dto.ConnectionResponse;
import com.agentsquad.squad.tools.dto.ConnectionUpdateRequest;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 连接管理 HTTP 边界。归属取认证会话；密钥只在请求体出现，任何响应不回显。
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
    public ConnectionResponse create(
            HttpServletRequest servletRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConnectionCreateRequest request) {
        return connectionService.create(UserContext.current(servletRequest),
                idempotencyKey, request);
    }

    /**
     * 分页列出当前用户的连接。
     */
    @GetMapping("/connections")
    public ConnectionPage list(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return connectionService.list(UserContext.current(servletRequest).userId(), cursor, limit);
    }

    /**
     * 读取当前用户单个连接视图，仅含 secretPresent。
     */
    @GetMapping("/connections/{connectionId}")
    public ConnectionResponse get(
            HttpServletRequest servletRequest,
            @PathVariable String connectionId) {
        return connectionService.get(UserContext.current(servletRequest).userId(), connectionId);
    }

    /**
     * 启停或更新策略、轮换密钥；revision 随成功更新递增。
     */
    @PatchMapping("/connections/{connectionId}")
    public ConnectionResponse update(
            HttpServletRequest servletRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String connectionId,
            @Valid @RequestBody ConnectionUpdateRequest request) {
        return connectionService.update(UserContext.current(servletRequest),
                connectionId, idempotencyKey, request);
    }
}
