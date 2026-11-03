package com.agentsquad.squad.gateway.admin;

import com.agentsquad.squad.gateway.auth.AuthRepository;
import com.agentsquad.squad.gateway.auth.AuthView;
import com.agentsquad.squad.gateway.auth.PasswordChangeRequest;
import com.agentsquad.squad.gateway.auth.PasswordPolicy;
import com.agentsquad.squad.gateway.auth.SessionSecrets;
import com.agentsquad.squad.gateway.exception.InvalidRequestException;
import com.agentsquad.squad.gateway.exception.PasswordChangeRequiredException;
import com.agentsquad.squad.gateway.exception.PermissionDeniedException;
import com.agentsquad.squad.gateway.exception.ResourceNotFoundException;
import com.agentsquad.squad.gateway.exception.StateConflictException;
import com.agentsquad.squad.gateway.exception.ValidationFailedException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 账号生命周期服务。
 *
 * <p>所有方法为阻塞事务方法，调用方必须在 boundedElastic 执行。管理员操作在事务内先按
 * user_id 顺序锁定活动管理员、再锁定目标用户，避免并发降级/停用绕过最后管理员约束。</p>
 */
@Slf4j
@Service
public class UserAccountService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DISPLAY_NAME = 100;
    private static final Set<String> ROLES = Set.of("USER", "ADMIN");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,63}$");

    @Resource
    private AdminUserMapper adminUserMapper;

    @Resource
    private AuthRepository authRepository;

    @Resource
    private PasswordEncoder passwordEncoder;

    /**
     * 分页列出账号摘要；游标为用户业务 ID，取 limit+1 判断是否还有下一页。
     */
    public AdminUserPage list(AuthView actor, String cursor, Integer limit) {
        requireAdmin(actor);
        int pageSize = limit == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        String normalizedCursor = cursor == null || cursor.isBlank() ? null : cursor.trim();
        if (normalizedCursor != null && normalizedCursor.length() > 64) {
            throw new InvalidRequestException("分页游标不合法");
        }
        List<UserAccountRow> rows = adminUserMapper.listUsers(normalizedCursor, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<UserAccountRow> page = hasNext ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasNext ? page.get(page.size() - 1).userId() : null;
        return new AdminUserPage(page.stream().map(AdminUserView::from).toList(), nextCursor);
    }

    /**
     * 创建账号并生成一次性临时密码；唯一用户名冲突返回 409。
     */
    @Transactional
    public AdminUserCreateResult create(AuthView actor, AdminUserCreateRequest request, String requestId) {
        requireAdmin(actor);
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new InvalidRequestException("用户名不能为空");
        }
        String username = normalizeUsername(request.username());
        String displayName = normalizeDisplayName(request.displayName(), username);
        String role = normalizeRole(request.role(), "USER");
        String temporaryPassword = SessionSecrets.randomToken();
        String userId = UUID.randomUUID().toString();
        try {
            adminUserMapper.insertUser(userId, username, displayName,
                    passwordEncoder.encode(temporaryPassword), role, true,
                    actor.userId(), actor.username());
        } catch (DataIntegrityViolationException exception) {
            throw new StateConflictException("用户名已存在");
        }
        authRepository.insertAudit(userId, actor.userId(), "USER_CREATE", "SUCCESS", requestId);
        log.info("管理员创建账号 actor={} target={} role={} requestId={}",
                actor.userId(), userId, role, requestId);
        return new AdminUserCreateResult(userId, temporaryPassword);
    }

    /**
     * 按 expectedVersion 更新账号；角色或状态变化提升授权代际并撤销该用户全部会话。
     */
    @Transactional
    public AdminUserView update(AuthView actor, String userId, AdminUserUpdateRequest request, String requestId) {
        requireAdmin(actor);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new InvalidRequestException("缺少 expectedVersion");
        }
        if (request.displayName() == null && request.role() == null && request.status() == null) {
            throw new ValidationFailedException("请至少修改一个字段");
        }
        List<String> activeAdmins = adminUserMapper.lockActiveAdmins();
        UserAccountRow target = adminUserMapper.findUserForUpdate(userId);
        if (target == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        if (target.version() != request.expectedVersion()) {
            throw new StateConflictException("数据已发生变化，请刷新后重试");
        }
        String role = request.role() == null ? target.role() : normalizeRole(request.role(), target.role());
        String status = request.status() == null ? target.status() : normalizeStatus(request.status(), target.status());
        String displayName = request.displayName() == null
                ? target.displayName() : normalizeDisplayName(request.displayName(), target.displayName());
        boolean authorizationChanged = !role.equals(target.role()) || !status.equals(target.status());
        boolean demotingActiveAdmin = "ADMIN".equals(target.role()) && "ACTIVE".equals(target.status())
                && (!"ADMIN".equals(role) || !"ACTIVE".equals(status));
        if (demotingActiveAdmin && activeAdmins.size() <= 1) {
            throw new StateConflictException("不能停用或降级最后一个活动管理员");
        }
        long authBump = authorizationChanged ? 1 : 0;
        int updated = adminUserMapper.updateProfile(userId, displayName, role, status,
                request.expectedVersion(), authBump, actor.userId(), actor.username());
        if (updated == 0) {
            throw new StateConflictException("数据已发生变化，请刷新后重试");
        }
        if (authorizationChanged) {
            authRepository.revokeUserSessions(userId, LocalDateTime.now());
        }
        authRepository.insertAudit(userId, actor.userId(), "USER_UPDATE", "SUCCESS", requestId);
        log.info("管理员更新账号 actor={} target={} role={} status={} authChanged={} requestId={}",
                actor.userId(), userId, role, status, authorizationChanged, requestId);
        return AdminUserView.from(adminUserMapper.findUserById(userId));
    }

    /**
     * 重置为一次性临时密码；旧密码与全部会话立即失效。
     */
    @Transactional
    public ResetPasswordResult resetPassword(AuthView actor, String userId, String requestId) {
        requireAdmin(actor);
        UserAccountRow target = adminUserMapper.findUserForUpdate(userId);
        if (target == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        String temporaryPassword = SessionSecrets.randomToken();
        adminUserMapper.updatePassword(userId, passwordEncoder.encode(temporaryPassword), true,
                actor.userId(), actor.username());
        authRepository.revokeUserSessions(userId, LocalDateTime.now());
        authRepository.insertAudit(userId, actor.userId(), "USER_RESET_PASSWORD", "SUCCESS", requestId);
        log.info("管理员重置密码 actor={} target={} requestId={}", actor.userId(), userId, requestId);
        return new ResetPasswordResult(temporaryPassword);
    }

    /**
     * 用户自行改密：校验当前密码后撤销全部会话（含当前），客户端须重新登录。
     */
    @Transactional
    public void changePassword(AuthView actor, PasswordChangeRequest request, String requestId) {
        if (request == null || request.currentPassword() == null || request.currentPassword().isEmpty()) {
            throw new InvalidRequestException("当前密码不能为空");
        }
        PasswordPolicy.require(request.newPassword());
        UserAccountRow target = adminUserMapper.findUserForUpdate(actor.userId());
        if (target == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        if (!passwordEncoder.matches(request.currentPassword(), target.passwordHash())) {
            authRepository.insertAudit(actor.userId(), actor.userId(), "PASSWORD_CHANGE", "FAILURE", requestId);
            throw new InvalidRequestException("当前密码不正确");
        }
        adminUserMapper.updatePassword(actor.userId(), passwordEncoder.encode(request.newPassword()), false,
                actor.userId(), actor.username());
        authRepository.revokeUserSessions(actor.userId(), LocalDateTime.now());
        authRepository.insertAudit(actor.userId(), actor.userId(), "PASSWORD_CHANGE", "SUCCESS", requestId);
        log.info("用户修改密码并撤销全部会话 userId={} requestId={}", actor.userId(), requestId);
    }

    /**
     * 仅在空用户表时创建初始管理员；重复执行返回冲突，应用启动不会调用此方法。
     */
    @Transactional
    public String bootstrapAdmin(String username, String rawPassword) {
        String normalized = normalizeUsername(username);
        PasswordPolicy.require(rawPassword);
        if (adminUserMapper.acquireBootstrapLock() != 1) {
            throw new StateConflictException("初始化正在进行或锁不可用，请稍后重试");
        }
        try {
            String existing = adminUserMapper.findAnyUserIdForUpdate();
            if (existing != null) {
                throw new StateConflictException("已存在账号，拒绝重复初始化");
            }
            String userId = UUID.randomUUID().toString();
            adminUserMapper.insertUser(userId, normalized, normalized, passwordEncoder.encode(rawPassword),
                    "ADMIN", true, userId, normalized);
            authRepository.insertAudit(userId, userId, "BOOTSTRAP_ADMIN", "SUCCESS", "bootstrap");
            log.info("初始管理员已创建 userId={}", userId);
            return userId;
        } finally {
            adminUserMapper.releaseBootstrapLock();
        }
    }

    private static void requireAdmin(AuthView actor) {
        if (actor == null) {
            throw new PermissionDeniedException();
        }
        if (actor.mustChangePassword()) {
            throw new PasswordChangeRequiredException();
        }
        if (!actor.roles().contains("ADMIN")) {
            throw new PermissionDeniedException();
        }
    }

    private static String normalizeUsername(String username) {
        String value = username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(value).matches()) {
            throw new ValidationFailedException("用户名需为 3–64 位小写字母、数字或 . _ -，且以字母或数字开头");
        }
        return value;
    }

    private static String normalizeDisplayName(String displayName, String fallback) {
        if (displayName == null || displayName.isBlank()) {
            return fallback;
        }
        String value = displayName.trim();
        if (value.codePointCount(0, value.length()) > MAX_DISPLAY_NAME) {
            throw new ValidationFailedException("显示名称不能超过 100 个字符");
        }
        return value;
    }

    private static String normalizeRole(String role, String fallback) {
        if (role == null || role.isBlank()) {
            return fallback;
        }
        String value = role.trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(value)) {
            throw new ValidationFailedException("角色只能为 USER 或 ADMIN");
        }
        return value;
    }

    private static String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(value)) {
            throw new ValidationFailedException("状态只能为 ACTIVE 或 DISABLED");
        }
        return value;
    }
}
