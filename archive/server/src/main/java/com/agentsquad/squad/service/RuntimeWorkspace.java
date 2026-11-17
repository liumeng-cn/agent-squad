package com.agentsquad.squad.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * SDK进程级临时工作目录
 * <p>
 * 仅容纳SDK可重建的索引和技能缓存；会话、检查点与输入队列必须使用数据库，不能依赖此目录恢复。
 * 每个服务实例独立创建，依赖该目录的Agent关闭后删除，不读取或清理用户历史工作区。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class RuntimeWorkspace {
    private Path directory;

    @PostConstruct
    public void initialize() throws IOException {
        directory = Files.createTempDirectory("squad-sdk-cache-");
    }

    public String path() {
        return directory.toString();
    }

    @PreDestroy
    public void cleanup() {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            log.warn("SDK临时缓存清理未完成，执行历史仍保存在数据库 errorType={}", failure.getClass().getSimpleName());
        }
    }
}
