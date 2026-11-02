package com.agentsquad.squad.gateway.auth;

import com.agentsquad.squad.gateway.exception.AuthenticationRequiredException;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

    /** 账号密码变更与管理员重置必须串行化，避免旧密码覆盖新密码。 */
    private AccountRecord lockAccount(String userId) {
        List<AccountRecord> rows = jdbc.query(ACCOUNT_BY_ID + " FOR UPDATE", this::row, userId);
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

    private void audit(String userId, String actorUserId, String eventType, String result) {
        jdbc.update("INSERT INTO sq_auth_audit(event_id,user_id,actor_user_id,event_type,result,occurred_at) "
                        + "VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(), userId, actorUserId, eventType, result, now());
    }

    private ResponseStatusException invalidRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String displayName(String value) {
        if (value == null || value.strip().length() > 100) {
            throw invalidRequest("显示名称无效或超过100个字符");
        }
        return value.strip();
    }

    private boolean validRole(String role) {
        return "ADMIN".equals(role) || "USER".equals(role);
    }

    private boolean validStatus(String status) {
        return "ACTIVE".equals(status) || "DISABLED".equals(status);
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
        boolean ok = account != null && password != null && password.length() <= 1024
                && passwordEncoder.matches(password,account.passwordHash());
        if (!ok || !"ACTIVE".equals(account.status())) {
            jdbc.update("UPDATE sq_auth_rate_bucket SET failures=CASE WHEN window_start<? THEN 1 ELSE failures+1 END,"
                            + "locked_until=CASE WHEN window_start>=? AND failures>=4 THEN ? ELSE NULL END,"
                            + "window_start=CASE WHEN window_start<? THEN ? ELSE window_start END WHERE bucket_key=?",
                    time.minusMinutes(15),time.minusMinutes(15),time.plusMinutes(15),
                    time.minusMinutes(15),time,bucket);
            audit(account == null ? null : account.userId(), null, "LOGIN", "DENIED");
            throw new AuthenticationRequiredException();
        }
        jdbc.update("UPDATE sq_auth_rate_bucket SET failures=0,locked_until=NULL,window_start=? WHERE bucket_key=?",time,bucket);
        String token=randomToken(32), csrf=csrf(token);
        jdbc.update("INSERT INTO sq_auth_session(session_id,user_id,token_hash,csrf_hash,issued_auth_version,"
                + "created_at,last_seen_at,idle_expires_at,absolute_expires_at) VALUES(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),account.userId(),digest(token),digest(csrf),account.authVersion(),
                time,time,time.plusHours(24),time.plusDays(7));
        audit(account.userId(), account.userId(), "LOGIN", "SUCCESS");
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

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank() || token.length() > 256) return;
        String hash = digest(token);
        List<String> owners = jdbc.query(
                "SELECT user_id FROM sq_auth_session WHERE token_hash=? AND revoked_at IS NULL",
                (rs, i) -> rs.getString(1), hash);
        int revoked = jdbc.update(
                "UPDATE sq_auth_session SET revoked_at=? WHERE token_hash=? AND revoked_at IS NULL",
                now(), hash);
        if (revoked == 1 && !owners.isEmpty()) {
            audit(owners.getFirst(), owners.getFirst(), "LOGOUT", "SUCCESS");
        }
    }

    @Transactional
    public void changePassword(String token, PasswordChangeRequest request) {
        AuthView actor = authenticate(token);
        if (request == null || request.newPassword() == null
                || request.newPassword().length() < 12 || request.newPassword().length() > 1024
                || request.currentPassword() == null) {
            throw invalidRequest("新密码长度须为12至1024个字符");
        }
        AccountRecord user = lockAccount(actor.userId());
        if (user == null || !"ACTIVE".equals(user.status())) {
            throw new AuthenticationRequiredException();
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new AuthenticationRequiredException();
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw invalidRequest("新密码不得与当前密码相同");
        }
        int updated = jdbc.update(
                "UPDATE sq_user SET password_hash=?,must_change_password=0,auth_version=auth_version+1,"
                        + "version=version+1 WHERE user_id=? AND auth_version=? AND del_flag='0'",
                passwordEncoder.encode(request.newPassword()), actor.userId(), user.authVersion());
        if (updated != 1) throw new AuthConflictException();
        jdbc.update("UPDATE sq_auth_session SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",
                now(), actor.userId());
        audit(actor.userId(), actor.userId(), "PASSWORD_CHANGED", "SUCCESS");
    }

    /** 读操作复查账户实时状态，拒绝已撤销的管理员 Session。 */
    private void requireAdmin(AuthView actor) {
        if (actor == null || actor.mustChangePassword() || !actor.roles().contains("ADMIN")) {
            throw new AuthForbiddenException();
        }
        AccountRecord current = byId(actor.userId());
        if (current == null || !"ACTIVE".equals(current.status())
                || !"ADMIN".equals(current.role()) || current.mustChangePassword()) {
            throw new AuthForbiddenException();
        }
    }

    /**
     * 写操作在事务内按稳定顺序锁住所有活跃管理员；不同网关并发降权时
     * 总能以最新锁定读结果判断最后一名管理员，不受 RR 快照读影响。
     */
    private List<String> lockActiveAdmins(AuthView actor) {
        if (actor == null || actor.mustChangePassword() || !actor.roles().contains("ADMIN")) {
            throw new AuthForbiddenException();
        }
        List<String> admins = jdbc.query(
                "SELECT user_id FROM sq_user WHERE role='ADMIN' AND status='ACTIVE' "
                        + "AND del_flag='0' ORDER BY id FOR UPDATE",
                (rs, i) -> rs.getString(1));
        if (!admins.contains(actor.userId())) throw new AuthForbiddenException();
        return admins;
    }

    @Transactional
    public UserCreateReceipt createUser(AuthView actor,UserCreateRequest request) {
        lockActiveAdmins(actor);
        if (request == null || !validRole(request.role())) throw invalidRequest("账号角色无效");
        String username = normalize(request.username());
        if (!username.matches("[a-z0-9._-]{3,64}")) throw invalidRequest("用户名格式无效");
        String name = displayName(request.displayName());
        String password=randomToken(24),userId=UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO sq_user(user_id,username,display_name,password_hash,role,status,creator_id) "
                    + "VALUES(?,?,?,?,?,'ACTIVE',?)",userId,username,name,
                    passwordEncoder.encode(password),request.role(),actor.userId());
        } catch (DuplicateKeyException ex) {
            throw new AuthConflictException();
        }
        audit(userId, actor.userId(), "USER_CREATED", "SUCCESS");
        return new UserCreateReceipt(userId,password);
    }

    public UserPage users(AuthView actor, int limit, long after) {
        requireAdmin(actor);
        if (after < 0) throw invalidRequest("分页游标无效");
        int size = Math.min(Math.max(limit, 1), 100);
        // 多取一条确认是否有下一页；游标使用 sq_user 自增ID，不受展示排序影响。
        var rows = jdbc.query(
                "SELECT id,user_id,username,display_name,role,status,must_change_password,version "
                        + "FROM sq_user WHERE id>? AND del_flag='0' ORDER BY id LIMIT ?",
                (rs, i) -> Map.entry(rs.getLong("id"),
                        new UserSummary(rs.getString("user_id"), rs.getString("username"),
                                rs.getString("display_name"), rs.getString("role"), rs.getString("status"),
                                rs.getBoolean("must_change_password"), rs.getLong("version"))),
                after, size + 1);
        var items = rows.stream().limit(size).map(Map.Entry::getValue).toList();
        String nextCursor = rows.size() > size ? Long.toString(rows.get(size - 1).getKey()) : null;
        return new UserPage(items, nextCursor);
    }

    @Transactional
    public void updateUser(AuthView actor, String userId, UserUpdateRequest request) {
        List<String> activeAdmins = lockActiveAdmins(actor);
        if (request == null || !validRole(request.role()) || !validStatus(request.status())
                || request.expectedVersion() < 0) {
            throw invalidRequest("用户更新参数无效");
        }
        String name = displayName(request.displayName());
        AccountRecord current = byId(userId);
        if (current == null) throw new AuthConflictException();
        if ("ADMIN".equals(current.role()) && "ACTIVE".equals(current.status())
                && (!"ADMIN".equals(request.role()) || !"ACTIVE".equals(request.status()))
                && activeAdmins.size() <= 1) {
            throw new AuthConflictException();
        }
        int updated = jdbc.update(
                "UPDATE sq_user SET display_name=?,role=?,status=?,auth_version=auth_version+1,"
                        + "version=version+1 WHERE user_id=? AND version=? AND del_flag='0'",
                name, request.role(), request.status(), userId, request.expectedVersion());
        if (updated != 1) throw new AuthConflictException();
        jdbc.update("UPDATE sq_auth_session SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",
                now(), userId);
        audit(userId, actor.userId(), "USER_UPDATED", "SUCCESS");
    }

    @Transactional
    public UserCreateReceipt resetPassword(AuthView actor, String userId) {
        lockActiveAdmins(actor);
        String password = randomToken(24);
        int count = jdbc.update(
                "UPDATE sq_user SET password_hash=?,must_change_password=1,"
                        + "auth_version=auth_version+1,version=version+1 "
                        + "WHERE user_id=? AND del_flag='0'",
                passwordEncoder.encode(password), userId);
        if (count != 1) throw new AuthConflictException();
        jdbc.update("UPDATE sq_auth_session SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",
                now(), userId);
        audit(userId, actor.userId(), "PASSWORD_RESET", "SUCCESS");
        return new UserCreateReceipt(userId, password);
    }
}
