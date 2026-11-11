package com.agentsquad.squad.nativechat;

import com.agentsquad.squad.identity.UserContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 多 SubAgent 并行实验接口。 */
@RestController
@RequestMapping("/api/v1/native-chat")
public class NativeSubagentController {
    @Resource
    private NativeSubagentService nativeSubagentService;

    @PostMapping(path = "/subagents", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(HttpServletRequest request, @Valid @RequestBody SubagentRequest body) {
        UserContext.current(request);
        return nativeSubagentService.stream(body.task());
    }

    public record SubagentRequest(@NotBlank @Size(max = 8000) String task) {}
}
