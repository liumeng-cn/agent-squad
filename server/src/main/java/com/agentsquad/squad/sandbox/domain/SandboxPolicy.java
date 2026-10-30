package com.agentsquad.squad.sandbox.domain;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.sandbox.dto.SandboxExecuteRequest;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 沙箱输入的固定资源与路径策略。
 * <p>
 * 路径只在隔离工作区内解释，拒绝遍历、隐藏控制字符和无界资源请求。
 */
public final class SandboxPolicy {
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    public static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;
    public static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    public static final int MAX_LOG_BYTES = 64 * 1024;

    private SandboxPolicy() {
    }

    public static void validate(SandboxExecuteRequest request) {
        if (request == null || request.logicalKey() == null || request.logicalKey().isBlank()
                || request.logicalKey().length() > 120 || request.script() == null || request.script().isBlank()
                || request.script().getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES
                || request.timeoutSeconds() < 1 || request.timeoutSeconds() > 60
                || request.files() == null || request.files().size() > 32
                || request.artifactIds().size() > 20
                || request.artifactIds().stream().anyMatch(id -> id == null || id.isBlank())
                || request.artifactIds().stream().distinct().count() != request.artifactIds().size()
                || request.outputPaths() == null || request.outputPaths().size() > 20) {
            throw invalid("沙箱输入、文件数或执行时限无效");
        }
        Set<String> paths = new HashSet<>();
        long bytes = 0;
        for (var file : request.files()) {
            if (file == null || file.content() == null) {
                throw invalid("沙箱输入文件不能为空");
            }
            requirePath(file.path());
            int size = file.content().getBytes(StandardCharsets.UTF_8).length;
            if (!paths.add(file.path()) || size > MAX_FILE_BYTES) {
                throw invalid("沙箱输入文件重复或过大");
            }
            bytes += size;
        }
        Set<String> outputs = new HashSet<>();
        for (String path : request.outputPaths()) {
            requirePath(path);
            if (!outputs.add(path)) {
                throw invalid("沙箱输出文件不能重复");
            }
        }
        if (bytes > MAX_TOTAL_BYTES) {
            throw invalid("沙箱输入文件总量超限");
        }
    }

    public static void requirePath(String path) {
        if (path == null || path.isBlank() || path.length() > 200 || path.startsWith("/")
                || path.contains("\\") || path.startsWith("__agentsquad_") || path.contains(":") || path.chars().anyMatch(value -> value < 32)
                || java.util.Arrays.stream(path.split("/", -1)).anyMatch(value -> value.isBlank()
                || value.equals(".") || value.equals(".."))) {
            throw invalid("沙箱文件必须使用无遍历的相对路径");
        }
    }

    private static RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
