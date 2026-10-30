package com.agentsquad.squad.skill.domain;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.skill.domain.SkillTextFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 技能文本包规范化与发布规则。
 * <p>
 * 纯业务校验不依赖 Spring 或磁盘；所有文件先通过路径、文本和大小检查，再解析安全 YAML 元数据。
 */
public final class SkillPackageValidator {

    private SkillPackageValidator() {
    }

    /**
     * 返回不可变发布包；按路径排序并使用长度分帧摘要，避免文件顺序或拼接歧义影响幂等身份。
     */
    public static SkillPackage validate(List<SkillTextFile> input, int maxFiles, int maxFileBytes, long maxPackageBytes) {
        if (input == null || input.isEmpty() || input.size() > maxFiles) {
            throw invalid("技能包文件数必须为 1 到 " + maxFiles);
        }
        List<SkillTextFile> files = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        long bytes = 0;
        for (SkillTextFile file : input) {
            if (file == null) {
                throw invalid("技能包不能包含空文件记录");
            }
            String path = validatePath(file.path());
            if (!paths.add(path.toLowerCase(java.util.Locale.ROOT))) {
                throw invalid(path + "：存在重复或大小写冲突路径");
            }
            String content = file.content();
            if (content == null || content.chars().anyMatch(c -> c == 0 || c < 32 && c != '\n' && c != '\r' && c != '\t')
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(content)) {
                throw invalid(path + "：仅接受有效 UTF-8 文本");
            }
            int size = content.getBytes(StandardCharsets.UTF_8).length;
            if (size > maxFileBytes) {
                throw invalid(path + "：单文件超过 " + maxFileBytes + " 字节");
            }
            bytes += size;
            if (bytes > maxPackageBytes) {
                throw invalid(path + "：技能包总大小超过 " + maxPackageBytes + " 字节");
            }
            files.add(new SkillTextFile(path, content));
        }
        files.sort(Comparator.comparing(SkillTextFile::path));
        SkillTextFile entry = files.stream().filter(file -> "SKILL.md".equals(file.path())).findFirst()
                .orElseThrow(() -> invalid("SKILL.md：缺少技能入口文件"));
        Map<?, ?> metadata = metadata(entry.content());
        String name = scalar(metadata, "name", 100);
        if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}")) {
            throw invalid("SKILL.md：name 只能包含字母、数字、点、下划线或短横线");
        }
        String description = scalar(metadata, "description", 4000);
        String version = metadata.get("version") == null ? "1.0.0" : scalar(metadata, "version", 80);
        if (!version.matches("[a-zA-Z0-9][a-zA-Z0-9._+-]{0,79}")) {
            throw invalid("SKILL.md：version 格式无效");
        }
        List<String> requiredTools = requiredTools(metadata.get("requires-tools"));
        return new SkillPackage(name, version, description, requiredTools, List.copyOf(files), hash(files));
    }

    /**
     * JSON 文件集合不支持符号链接；规范化路径也不允许逃逸、平台盘符或隐藏的路径片段。
     */
    public static String validatePath(String path) {
        if (path == null || path.isBlank() || path.length() > 240 || path.startsWith("/") || path.contains("\\")
                || path.contains(":") || path.chars().anyMatch(c -> c < 32)
                || !Normalizer.isNormalized(path, Normalizer.Form.NFC)) {
            throw invalid("技能文件路径无效");
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part) || !part.equals(part.strip())) {
                throw invalid(path + "：禁止空路径、相对逃逸或首尾空白");
            }
        }
        return path;
    }

    private static Map<?, ?> metadata(String text) {
        String normalized = text.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw invalid("SKILL.md：必须提供 YAML frontmatter");
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0 && normalized.endsWith("\n---")) {
            end = normalized.length() - 4;
        }
        if (end < 0) {
            throw invalid("SKILL.md：YAML frontmatter 未结束");
        }
        String header = normalized.substring(4, end);
        if (java.util.regex.Pattern.compile("(?:^|\\s)[&*][a-zA-Z0-9_-]+").matcher(header).find()) {
            throw invalid("SKILL.md：元数据不允许 YAML 锚点或别名");
        }
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(8);
        options.setCodePointLimit(16384);
        try {
            Object value = new Yaml(new SafeConstructor(options)).load(header);
            if (value instanceof Map<?, ?> map) {
                return map;
            }
        } catch (RuntimeException exception) {
            throw invalid("SKILL.md：YAML 元数据无效，不允许重复字段、别名或对象标签");
        }
        throw invalid("SKILL.md：元数据必须为对象");
    }

    private static String scalar(Map<?, ?> metadata, String key, int maxLength) {
        Object value = metadata.get(key);
        if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength
                || text.chars().anyMatch(c -> c < 32 && c != '\n' && c != '\t')) {
            throw invalid("SKILL.md：" + key + " 必须为长度不超过 " + maxLength + " 的文本");
        }
        return text.strip();
    }

    private static List<String> requiredTools(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values) || values.size() > 64) {
            throw invalid("SKILL.md：requires-tools 必须为最多 64 项的工具名称列表");
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String name) || !name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}")
                    || result.contains(name)) {
                throw invalid("SKILL.md：requires-tools 含无效或重复工具名");
            }
            result.add(name);
        }
        return List.copyOf(result);
    }

    private static String hash(List<SkillTextFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (SkillTextFile file : files) {
                byte[] path = file.path().getBytes(StandardCharsets.UTF_8);
                byte[] content = file.content().getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(8).putInt(path.length).putInt(content.length).array());
                digest.update(path);
                digest.update(content);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }

    private static RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
