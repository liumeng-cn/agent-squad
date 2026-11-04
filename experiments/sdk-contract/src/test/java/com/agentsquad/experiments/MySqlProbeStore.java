package com.agentsquad.experiments;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionExportSink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 隔离数据库实验收件器
 * <p>
 * 仅供专用容器中的测试建表与故障注入；直接SQL不属于项目生产访问链路。
 */
final class MySqlProbeStore implements SessionExportSink {
    private final MysqlDataSource source;
    private final boolean crashAfterSecondCommit;

    MySqlProbeStore(boolean crashAfterSecondCommit) {
        String url = System.getenv("SQUAD_PROBE_MYSQL_URL");
        if (url == null || !url.contains("/squad_sdk_probe?")) {
            throw new IllegalStateException("只允许专用实验数据库");
        }
        source = new MysqlDataSource();
        source.setURL(url);
        source.setUser("root");
        source.setPassword(System.getenv("SQUAD_PROBE_MYSQL_PASSWORD"));
        this.crashAfterSecondCommit = crashAfterSecondCommit;
    }

    MysqlDataSource source() {
        return source;
    }

    void initialize() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS probe_inbox (native_seq BIGINT PRIMARY KEY, event_id VARCHAR(80) UNIQUE NOT NULL, digest CHAR(64) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS probe_effect (event_id VARCHAR(80) PRIMARY KEY)");
        }
    }

    @Override
    public String name() {
        return "squad-mysql-probe";
    }

    @Override
    public void accept(SessionEvent event) {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(event.payloadJson().getBytes(StandardCharsets.UTF_8)));
            try (var insert = connection.prepareStatement("INSERT INTO probe_inbox(native_seq,event_id,digest) VALUES(?,?,?) ON DUPLICATE KEY UPDATE native_seq=native_seq")) {
                insert.setLong(1, event.seq());
                insert.setString(2, event.eventId());
                insert.setString(3, digest);
                insert.executeUpdate();
            }
            try (var read = connection.prepareStatement("SELECT event_id,digest FROM probe_inbox WHERE native_seq=? FOR UPDATE")) {
                read.setLong(1, event.seq());
                try (var rows = read.executeQuery()) {
                    if (!rows.next() || !event.eventId().equals(rows.getString(1)) || !digest.equals(rows.getString(2))) {
                        connection.rollback();
                        throw new IllegalStateException("实验收件冲突");
                    }
                }
            }
            connection.commit();
            if (crashAfterSecondCommit && event.seq() == 2) {
                Runtime.getRuntime().halt(23);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("实验收件失败", exception);
        }
    }

    void consumeTwice() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.executeUpdate("INSERT IGNORE INTO probe_effect(event_id) SELECT event_id FROM probe_inbox");
                connection.commit();
            }
        }
    }

    long count(String table) throws Exception {
        if (!table.equals("probe_inbox") && !table.equals("probe_effect")) {
            throw new IllegalArgumentException("未知实验表");
        }
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
