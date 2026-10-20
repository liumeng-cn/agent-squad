package com.agentsquad.squad.runtime.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * D0 使用的单机持久命令存储。
 *
 * <p>它只用于离线恢复探针，不是生产存储替代品。每行保存一个固定字段格式，启动时完整重放，
 * 模拟平台从数据库恢复 ACCEPTED 命令；D1 会替换为 MyBatis 事务表。</p>
 */
public class FileCommandStore {

    private final Path file;

    public FileCommandStore(Path file) {
        this.file = file;
    }

    /**
     * 创建命令或返回同 ID 的原记录，验证同一命令不能更换输入。
     *
     * <p>写入采用追加模式；离线探针不模拟多进程并发，生产实现必须由数据库唯一键和事务保证。</p>
     */
    public synchronized DurableCommand accept(String commandId, String payload) throws IOException {
        Map<String, DurableCommand> commands = readAll();
        DurableCommand existing = commands.get(commandId);
        if (existing != null) {
            if (!existing.payload().equals(payload)) {
                throw new IllegalArgumentException("同一 commandId 的输入不能变化: " + commandId);
            }
            return existing;
        }
        DurableCommand accepted = new DurableCommand(commandId, payload, CommandStatus.ACCEPTED);
        append(accepted);
        return accepted;
    }

    /** 启动恢复时读取还未执行的命令，调用方随后按同一 commandId 投递。 */
    public synchronized List<DurableCommand> pending() throws IOException {
        return readAll().values().stream()
                .filter(command -> command.status() == CommandStatus.ACCEPTED)
                .toList();
    }

    /** 将命令标记为已执行，重复标记保持幂等。 */
    public synchronized DurableCommand markExecuted(String commandId) throws IOException {
        Map<String, DurableCommand> commands = readAll();
        DurableCommand current = Optional.ofNullable(commands.get(commandId))
                .orElseThrow(() -> new IllegalArgumentException("命令不存在: " + commandId));
        if (current.status() == CommandStatus.EXECUTED) {
            return current;
        }
        DurableCommand executed = new DurableCommand(current.commandId(), current.payload(), CommandStatus.EXECUTED);
        append(executed);
        return executed;
    }

    private Map<String, DurableCommand> readAll() throws IOException {
        Map<String, DurableCommand> commands = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return commands;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length != 3) {
                throw new IOException("命令存储记录格式错误");
            }
            commands.put(fields[0], new DurableCommand(fields[0], fields[1], CommandStatus.valueOf(fields[2])));
        }
        return commands;
    }

    private void append(DurableCommand command) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String line = command.commandId() + "\t" + command.payload() + "\t" + command.status() + System.lineSeparator();
        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
