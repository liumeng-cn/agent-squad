package com.agentsquad.squad.integration.agentscope.journal;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 显式本地日志导入命令
 * <p>
 * 仅离线启动启用，正常服务不会自动扫描历史目录或重复导入；完成后启动入口关闭该命令进程。
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "agentsquad.runtime.import-local-journal", havingValue = "true")
public class RuntimeJournalImportRunner implements ApplicationRunner {
    @Resource
    private RuntimeJournalImportService imports;

    @Value("${agentsquad.delivery.enabled:false}")
    private boolean deliveryEnabled;

    @Value("${agentsquad.runtime.import-workspace:}")
    private String source;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (deliveryEnabled || source.isBlank()) {
            throw new IllegalStateException("导入必须关闭命令投递并显式指定import-workspace，且旧服务必须停止");
        }
        var result = imports.importWorkspace(Path.of(source));
        log.info("原生日志导入并核对完成 users={} sessions={} objects={} bytes={}",
                result.users(), result.sessions(), result.objects(), result.bytes());
    }
}
