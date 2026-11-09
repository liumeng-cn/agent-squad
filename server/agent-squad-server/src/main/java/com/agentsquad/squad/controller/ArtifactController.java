package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.identity.UserContext;
import com.agentsquad.squad.model.po.ArtifactPO;
import com.agentsquad.squad.model.vo.ArtifactVO;
import com.agentsquad.squad.service.ArtifactService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 产物接口
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
    public ApiResult<ArtifactVO> upload(HttpServletRequest servletRequest,
                                        @RequestHeader("Idempotency-Key") String key, @RequestParam("file") MultipartFile file) throws IOException {
        String userId = UserContext.current(servletRequest).userId();
        return ApiResult.success(artifactService.upload(userId, key, file.getOriginalFilename(), file.getBytes()));
    }

    @GetMapping("/artifacts/{artifactId}")
    public ApiResult<ArtifactVO> get(HttpServletRequest servletRequest,
                                     @PathVariable String artifactId) {
        return ApiResult.success(artifactService.get(UserContext.current(servletRequest).userId(), artifactId));
    }

    @GetMapping("/tasks/{taskId}/artifacts")
    public ApiResult<List<ArtifactVO>> list(HttpServletRequest servletRequest,
                                            @PathVariable String taskId) {
        return ApiResult.success(artifactService.listTask(UserContext.current(servletRequest).userId(), taskId));
    }

    @GetMapping("/artifacts/{artifactId}/content")
    public ResponseEntity<byte[]> download(HttpServletRequest servletRequest,
                                           @PathVariable String artifactId) {
        ArtifactPO row = artifactService.content(UserContext.current(servletRequest).userId(), artifactId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(row.getMediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(row.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(row.getContent());
    }
}
