package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.v2.dto.SquadMessageRequestDTO;
import com.agentsquad.squad.v2.identity.SquadUserIdentityResolver;
import com.agentsquad.squad.v2.service.SquadMessageService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** V2 消息 SSE 入口。 */
@RestController
@RequestMapping("/api/v2")
public class SquadMessageController {
    @Resource private SquadMessageService messageService;
    @Resource private SquadUserIdentityResolver identityResolver;

    /** POST /api/v2/msg；新消息与断线补收共用该接口。 */
    @PostMapping(value = "/msg", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter message(@Valid @RequestBody SquadMessageRequestDTO request,
                              HttpServletRequest servletRequest) {
        return messageService.stream(request, identityResolver.resolve(servletRequest));
    }
}