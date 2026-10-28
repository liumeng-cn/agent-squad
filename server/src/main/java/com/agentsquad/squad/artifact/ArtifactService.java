package com.agentsquad.squad.artifact;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 小文件产物的上传、归属校验与下载服务。
 * <p>
 * 允许 UTF-8 文本、Markdown、JSON、CSV，最多 10 MiB；同用户同上传键必须同名称及内容。
 * 二进制和沙箱 stage/commit 由独立交付流程负责，不从模型提供的宿主路径读取文件。
 */
@Service
@Slf4j
public class ArtifactService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    private static final Map<String, String> TYPES = Map.of(
            "txt", "text/plain", "md", "text/markdown", "json", "application/json", "csv", "text/csv");

    @Resource
    private ArtifactMapper mapper;

    @Resource
    private TaskMapper taskMapper;

    /**
     * 内容校验与摘要完成后原子提交 READY；并发同键重放只返回同一产物。
     */
    @Transactional
    public ArtifactView upload(String userId, String key, String name, byte[] content) {
        if (key == null || key.isBlank() || key.length() > 160 || name == null || name.isBlank()
                || name.length() > 240 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(value -> value < 32) || content == null || content.length > MAX_BYTES) {
            throw invalid("文件名、上传键或文件大小无效（最大 10 MiB）");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
        String type = TYPES.get(extension);
        if (type == null) {
            throw invalid("仅支持 txt、md、json、csv 文件");
        }
        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException exception) {
            throw invalid("文件必须采用 UTF-8 编码");
        }
        String hash = digest(content);
        ArtifactRow prior = mapper.findUpload(userId, key);
        if (prior != null) {
            return sameUpload(userId, prior, name, hash);
        }
        String id = UUID.randomUUID().toString();
        try {
            mapper.insert(id, userId, name, type, content.length, hash, key, content,
                    LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
        } catch (DuplicateKeyException exception) {
            return sameUpload(userId, mapper.findUpload(userId, key), name, hash);
        }
        log.info("产物内容与元数据写入完成 userId={} artifactId={} sizeBytes={} mediaType={}",
                userId, id, content.length, type);
        return get(userId, id);
    }

    public ArtifactView get(String userId, String id) {
        return view(requireOwned(userId, id));
    }

    /**
     * 下载前重验资源归属、有效期和存储摘要；不返回未经完整性校验的字节。
     */
    public ArtifactRow content(String userId, String id) {
        ArtifactRow row = mapper.findContent(userId, id);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "产物不存在、已过期或无权访问");
        }
        if (row.content() == null || row.sizeBytes() != row.content().length
                || !row.sha256().equals(digest(row.content()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "产物内容不可用，请核对后重新上传");
        }
        return row;
    }

    /**
     * 目标受理事务内绑定已 READY 的同用户产物；任何无效引用使整个目标受理回滚。
     */
    public void attach(String userId, String taskId, List<String> ids) {
        if (ids.size() > 20 || ids.stream().distinct().count() != ids.size()) {
            throw invalid("附件最多 20 个且不能重复");
        }
        for (String id : ids) {
            requireOwned(userId, id);
            mapper.attach(userId, taskId, id);
        }
    }

    public List<ArtifactView> listTask(String userId, String taskId) {
        if (taskMapper.findOwned(userId, taskId) == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标不存在");
        }
        return mapper.listTaskIds(userId, taskId).stream().map(id -> get(userId, id)).toList();
    }

    private ArtifactView sameUpload(String userId, ArtifactRow row, String name, String hash) {
        if (row == null || !row.fileName().equals(name) || !row.sha256().equals(hash)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同上传键的文件不一致");
        }
        return get(userId, row.artifactId());
    }

    private ArtifactRow requireOwned(String userId, String id) {
        ArtifactRow row = mapper.findOwned(userId, id);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "产物不存在、已过期或无权访问");
        }
        return row;
    }

    private ArtifactView view(ArtifactRow row) {
        return new ArtifactView(row.artifactId(), row.fileName(), row.mediaType(), row.sizeBytes(),
                row.sha256(), "READY", row.expiresAt().atOffset(ZoneOffset.UTC).toString());
    }

    private String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
