package com.agentsquad.squad.gateway.auth;

import com.agentsquad.squad.gateway.exception.AuthenticationRequiredException;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * MySQL 持久身份权威：用户、密钥摘要、会话及授权撤销代际。
 * 不存储原始 Cookie/CSRF；网关实例共用数据库即可跨实例认证。
 */
@Service
public class DatabaseAuthService {

    @Resource
    private JdbcTemplate jdbc;

    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    private static final String ACCOUNT_SELECT = "SELECT user_id,username,display_name,password_hash,role,status,"
            + "auth_version,must_change_password,version FROM sq_user WHERE username=? AND del_flag='0'";
    private static final String ACCOUNT_BY_ID = "SELECT user_id,username,display_name,password_hash,role,status,"
            + "auth_version,must_change_password,version FROM sq_user WHERE user_id=? AND del_flag='0'";

    private AccountRecord row(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        return new AccountRecord(rs.getString("user_id"),rs.getString("username"),rs.getString("display_name"),
                rs.getString("password_hash"),rs.getString("role"),rs.getString("status"),
                rs.getLong("auth_version"),rs.getBoolean("must_change_password"),rs.getLong("version"));
    }

    private AccountRecord find(String username) {
        List<AccountRecord> rows = jdbc.query(ACCOUNT_SELECT, this::row, username);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private AccountRecord byId(String userId) {
        List<AccountRecord> rows = jdbc.query(ACCOUNT_BY_ID, this::row, userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private String randomToken(int length) {
        byte[] secret = new byte[length];
        random.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private boolean same(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private String csrf(String token) {
        return digest("agent-squad:csrf:v1:" + token);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 只产生摘要入库；失败凭据和未知用户统一抛 401。
     * 限流记录以账号摘要为键并在同一 SQL 存储，避免多网关绕过。
     */
    @Transactional(noRollbackFor = AuthenticationRequiredException.class)
    public String login(String username, String password) {
        String name = normalize(username);
        String bucket = digest("login:" + name);
        LocalDateTime time = now();
        jdbc.update("INSERT IGNORE INTO sq_auth_rate_bucket(bucket_key,window_start,failures) VALUES(?,?,0)", bucket,time);
        List<LocalDateTime> locks = jdbc.query("SELECT locked_until FROM sq_auth_rate_bucket WHERE bucket_key=? FOR UPDATE",
                (rs,i) -> rs.getTimestamp(1)==null?null:rs.getTimestamp(1).toLocalDateTime(),bucket);
        if (!locks.isEmpty() && locks.getFirst()!=null && locks.getFirst().isAfter(time)) {
            throw new AuthenticationRequiredException();
        }
        AccountRecord account = find(name);
        boolean ok = account != null && password != null && passwordEncoder.matches(password,account.passwordHash());
        if (!ok || !"ACTIVE".equals(account.status())) {
            jdbc.update("UPDATE sq_auth_rate_bucket SET failures=CASE WHEN window_start<? THEN 1 ELSE failures+1 END,"
                            + "locked_until=CASE WHEN window_start>=? AND failures>=4 THEN ? ELSE NULL END,"
                            + "window_start=CASE WHEN window_start<? THEN ? ELSE window_start END WHERE bucket_key=?",
                    time.minusMinutes(15),time.minusMinutes(15),time.plusMinutes(15),
                    time.minusMinutes(15),time,bucket);
            throw new AuthenticationRequiredException();
        }
        jdbc.update("UPDATE sq_auth_rate_bucket SET failures=0,locked_until=NULL,window_start=? WHERE bucket_key=?",time,bucket);
        String token=randomToken(32), csrf=csrf(token);
        jdbc.update("INSERT INTO sq_auth_session(session_id,user_id,token_hash,csrf_hash,issued_auth_version,"
                + "created_at,last_seen_at,idle_expires_at,absolute_expires_at) VALUES(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),account.userId(),digest(token),digest(csrf),account.authVersion(),
                time,time,time.plusHours(24),time.plusDays(7));
        jdbc.update("INSERT INTO sq_auth_audit(event_id,user_id,actor_user_id,event_type,result,occurred_at) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(),account.userId(),account.userId(),"LOGIN","SUCCESS",time);
        return token;
    }

    /**
     * 每次读账户当前状态/授权代际，禁用、改密和撤销即时失效。
     */
    public AuthView authenticate(String token) {
        if (token==null || token.isBlank() || token.length()>256) throw new AuthenticationRequiredException();
        var rows=jdbc.query("SELECT s.user_id,s.issued_auth_version,s.last_seen_at,s.idle_expires_at,"
                        + "s.absolute_expires_at FROM sq_auth_session s WHERE s.token_hash=? AND s.revoked_at IS NULL",
                (rs,i)->new AuthSessionRecord(rs.getString(1),rs.getLong(2),
                        rs.getTimestamp(3).toLocalDateTime(),rs.getTimestamp(4).toLocalDateTime(),
                        rs.getTimestamp(5).toLocalDateTime()),digest(token));
        if(rows.isEmpty())throw new AuthenticationRequiredException();
        AuthSessionRecord session=rows.getFirst();
        LocalDateTime time=now();
        AccountRecord account=byId(session.userId());
        if(account==null || !"ACTIVE".equals(account.status()) || account.authVersion()!=session.issuedAuthVersion()
                || !session.idleExpiresAt().isAfter(time) || !session.absoluteExpiresAt().isAfter(time))
            throw new AuthenticationRequiredException();
        if(session.lastSeenAt().isBefore(time.minusMinutes(5))) {
            jdbc.update("UPDATE sq_auth_session SET last_seen_at=?,idle_expires_at=LEAST(absolute_expires_at,?) "
                            + "WHERE token_hash=? AND revoked_at IS NULL AND last_seen_at<?",
                    time,time.plusHours(24),digest(token),time.minusMinutes(5));
        }
        return new AuthView(account.userId(),account.username(),List.of(account.role()),csrf(token),account.mustChangePassword());
    }

    public AuthView authenticateWrite(String token,String csrfToken) {
        AuthView view=authenticate(token);
        if(csrfToken==null || !same(digest(view.csrfToken()),digest(csrfToken)))
            throw new AuthenticationRequiredException();
        return view;
    }

    public void logout(String token) {
        if(token!=null && !token.isBlank()) {
            jdbc.update("UPDATE sq_auth_session SET revoked_at=? WHERE token_hash=? AND revoked_at IS NULL",now(),digest(token));
        }
    }
}
