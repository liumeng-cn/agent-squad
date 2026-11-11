package com.agentsquad.squad.v2.stream;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 仅 local profile 开放的 Squad SSE 测试接口。 */
@RestController
@Profile("local")
@RequestMapping("/api/v2/test/events")
public class TestEventStreamController {

    private final TestEventStreamService eventStreamService;

    public TestEventStreamController(TestEventStreamService eventStreamService) {
        this.eventStreamService = eventStreamService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody(required = false) TestEventStreamRequest request) {
        return eventStreamService.stream(request == null
                ? new TestEventStreamRequest(300) : request);
    }
}
