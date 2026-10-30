package com.agentsquad.squad.artifact.controller;

import com.agentsquad.squad.artifact.domain.ArtifactRow;
import com.agentsquad.squad.artifact.dto.ArtifactView;
import com.agentsquad.squad.artifact.service.ArtifactService;
import com.agentsquad.squad.identity.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 产物 HTTP 传输边界。
 * <p>
 * 下载始终使用附件响应与 nosniff，归属与内容完整性由应用服务重新校验。
 */
@RestController
@RequestMapping("/api/v1")
public class ArtifactController {
    @Resource
    private ArtifactService artifactService;

    @PostMapping("/artifacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ArtifactView upload(HttpServletRequest servletRequest,
                               @RequestHeader("Idempotency-Key") String key, @RequestParam("file") MultipartFile file) throws IOException {
        String userId = UserContext.current(servletRequest).userId();
        return artifactService.upload(userId, key, file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/artifacts/{artifactId}")
    public ArtifactView get(HttpServletRequest servletRequest,
                            @PathVariable String artifactId) {
        return artifactService.get(UserContext.current(servletRequest).userId(), artifactId);
    }

    @GetMapping("/tasks/{taskId}/artifacts")
    public List<ArtifactView> list(HttpServletRequest servletRequest,
                                   @PathVariable String taskId) {
        return artifactService.listTask(UserContext.current(servletRequest).userId(), taskId);
    }

    @GetMapping("/artifacts/{artifactId}/content")
    public ResponseEntity<byte[]> download(HttpServletRequest servletRequest,
                                           @PathVariable String artifactId) {
        ArtifactRow row = artifactService.content(UserContext.current(servletRequest).userId(), artifactId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(row.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(row.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(row.content());
    }
}
