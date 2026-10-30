package com.agentsquad.squad.skill.repository;

import com.agentsquad.squad.skill.domain.SkillGrantRow;
import com.agentsquad.squad.skill.domain.SkillReference;
import com.agentsquad.squad.skill.domain.SkillVersionRow;
import com.agentsquad.squad.skill.domain.SkillTextFile;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 个人技能版本与授权持久化。
 * <p>
 * 只执行带用户归属的数据读写；业务启停、包校验和执行准入由服务层负责。
 */
@Repository
public class SkillRepository {

    private static final String VERSION_COLUMNS = "id, skill_version_id, user_id, skill_name, skill_version, "
            + "description, content_hash, required_tools_json, status, authorization_revision";

    @Resource
    private JdbcTemplate jdbc;

    public SkillVersionRow find(String userId, String versionId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM sq_skill_version "
                        + "WHERE user_id = ? AND skill_version_id = ? AND del_flag = '0'",
                SkillRepository::versionRow, userId, versionId).stream().findFirst().orElse(null);
    }

    public SkillVersionRow lockVersion(String userId, String versionId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM sq_skill_version "
                        + "WHERE user_id = ? AND skill_version_id = ? AND del_flag = '0' FOR UPDATE",
                SkillRepository::versionRow, userId, versionId).stream().findFirst().orElse(null);
    }

    public SkillVersionRow findNamedVersion(String userId, String name, String version) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM sq_skill_version "
                        + "WHERE user_id = ? AND skill_name = ? AND skill_version = ? AND del_flag = '0'",
                SkillRepository::versionRow, userId, name, version).stream().findFirst().orElse(null);
    }

    /**
     * 唯一键竞争后使用当前读取得胜出版本，避免 MySQL 可重复读仍返回竞争前的空快照。
     */
    public SkillVersionRow findNamedVersionCurrent(String userId, String name, String version) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM sq_skill_version "
                        + "WHERE user_id = ? AND skill_name = ? AND skill_version = ? AND del_flag = '0' FOR UPDATE",
                SkillRepository::versionRow, userId, name, version).stream().findFirst().orElse(null);
    }

    public List<SkillVersionRow> list(String userId, long afterId, int limit) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM sq_skill_version "
                        + "WHERE user_id = ? AND id > ? AND del_flag = '0' ORDER BY id LIMIT ?",
                SkillRepository::versionRow, userId, afterId, limit);
    }

    /**
     * 每个名称只选择最新启用版本；旧执行的引用由独立固定表保存。
     */
    public List<SkillVersionRow> listSelectable(String userId) {
        // 先按稳定顺序锁版本，再锁各版本授权；不使用带快照子查询的 JOIN 推断当前启用版本。
        List<SkillVersionRow> versions = jdbc.query("SELECT " + VERSION_COLUMNS + " FROM sq_skill_version "
                        + "WHERE user_id = ? AND del_flag = '0' ORDER BY id FOR UPDATE",
                SkillRepository::versionRow, userId);
        Map<String, SkillVersionRow> selected = new LinkedHashMap<>();
        for (SkillVersionRow version : versions) {
            if (!"ENABLED".equals(version.status())) {
                continue;
            }
            SkillGrantRow grant = lockGrant(userId, version.skillVersionId());
            if (grant != null && grant.enabled()) {
                selected.put(version.name(), version);
            }
        }
        return selected.values().stream().sorted(java.util.Comparator.comparing(SkillVersionRow::name)).toList();
    }


    public void insertVersion(String userId, String versionId, String name, String version, String description,
                              String contentHash, String requiredToolsJson) {
        jdbc.update("""
                INSERT INTO sq_skill_version (
                    skill_version_id, user_id, skill_name, skill_version, description,
                    content_hash, required_tools_json, creator_id, last_updater_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, versionId, userId, name, version, description, contentHash, requiredToolsJson, userId, userId);
    }

    public void insertFile(String userId, String versionId, SkillTextFile file) {
        jdbc.update("""
                INSERT INTO sq_skill_file (
                    skill_file_id, user_id, skill_version_id, file_path, file_content, creator_id, last_updater_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), userId, versionId, file.path(), file.content(), userId, userId);
    }

    public List<SkillTextFile> files(String userId, String versionId) {
        return jdbc.query("""
                        SELECT file_path, file_content
                        FROM sq_skill_file
                        WHERE user_id = ? AND skill_version_id = ? AND del_flag = '0'
                        ORDER BY file_path
                        """, (row, index) -> new SkillTextFile(row.getString("file_path"), row.getString("file_content")),
                userId, versionId);
    }

    public SkillTextFile file(String userId, String versionId, String path) {
        return jdbc.query("""
                        SELECT file_path, file_content
                        FROM sq_skill_file
                        WHERE user_id = ? AND skill_version_id = ? AND file_path = ? AND del_flag = '0'
                        """, (row, index) -> new SkillTextFile(row.getString("file_path"), row.getString("file_content")),
                userId, versionId, path).stream().findFirst().orElse(null);
    }

    public void insertGrant(String userId, String versionId) {
        jdbc.update("""
                INSERT INTO sq_skill_grant (grant_id, user_id, skill_version_id, enabled, creator_id, last_updater_id)
                VALUES (?, ?, ?, FALSE, ?, ?)
                """, UUID.randomUUID().toString(), userId, versionId, userId, userId);
    }

    public SkillGrantRow grant(String userId, String versionId) {
        return jdbc.query("""
                SELECT user_id, skill_version_id, enabled, version
                FROM sq_skill_grant
                WHERE user_id = ? AND skill_version_id = ? AND del_flag = '0'
                """, (row, index) -> new SkillGrantRow(row.getString("user_id"), row.getString("skill_version_id"),
                row.getBoolean("enabled"), row.getLong("version")), userId, versionId).stream().findFirst().orElse(null);
    }

    private SkillGrantRow lockGrant(String userId, String versionId) {
        return jdbc.query("""
                SELECT user_id, skill_version_id, enabled, version
                FROM sq_skill_grant
                WHERE user_id = ? AND skill_version_id = ? AND del_flag = '0'
                FOR UPDATE
                """, (row, index) -> new SkillGrantRow(row.getString("user_id"), row.getString("skill_version_id"),
                row.getBoolean("enabled"), row.getLong("version")), userId, versionId).stream().findFirst().orElse(null);
    }

    public int changeGrant(String userId, String versionId, long expectedVersion, boolean enabled) {
        return jdbc.update("""
                UPDATE sq_skill_grant
                SET enabled         = ?,
                    version         = version + 1,
                    last_updater_id = ?
                WHERE user_id = ? AND skill_version_id = ? AND version = ? AND del_flag = '0'
                """, enabled, userId, userId, versionId, expectedVersion);
    }

    public int revoke(String userId, String versionId) {
        return jdbc.update("""
                UPDATE sq_skill_version
                SET status                 = 'REVOKED',
                    authorization_revision = authorization_revision + 1,
                    last_updater_id        = ?
                WHERE user_id = ? AND skill_version_id = ? AND status <> 'REVOKED' AND del_flag = '0'
                """, userId, userId, versionId);
    }

    public boolean catalogExists(String userId, String scopeId) {
        return !jdbc.queryForList("""
                SELECT catalog_id
                FROM sq_scope_skill_catalog
                WHERE user_id = ? AND scope_id = ? AND del_flag = '0'
                FOR UPDATE
                """, String.class, userId, scopeId).isEmpty();
    }

    public void insertCatalog(String userId, String scopeId) {
        jdbc.update("""
                INSERT INTO sq_scope_skill_catalog (catalog_id, user_id, scope_id, creator_id, last_updater_id)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), userId, scopeId, userId, userId);
    }

    public void insertReference(String userId, String scopeId, SkillVersionRow skill) {
        jdbc.update("""
                        INSERT INTO sq_scope_skill_ref (
                            reference_id, user_id, scope_id, skill_version_id, content_hash,
                            authorization_revision, creator_id, last_updater_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID().toString(), userId, scopeId, skill.skillVersionId(), skill.contentHash(),
                skill.authorizationRevision(), userId, userId);
    }

    public List<SkillReference> references(String userId, String scopeId) {
        return jdbc.query("""
                SELECT r.skill_version_id, v.skill_name, v.skill_version, v.description,
                       r.content_hash, r.authorization_revision
                FROM sq_scope_skill_ref r
                JOIN sq_skill_version v ON v.user_id = r.user_id AND v.skill_version_id = r.skill_version_id
                WHERE r.user_id = ? AND r.scope_id = ? AND r.del_flag = '0'
                ORDER BY v.skill_name
                FOR UPDATE
                """, (row, index) -> new SkillReference(row.getString("skill_version_id"), row.getString("skill_name"),
                row.getString("skill_version"), row.getString("description"), row.getString("content_hash"),
                row.getLong("authorization_revision")), userId, scopeId);
    }

    private static SkillVersionRow versionRow(ResultSet row, int index) throws SQLException {
        return new SkillVersionRow(row.getLong("id"), row.getString("skill_version_id"), row.getString("user_id"),
                row.getString("skill_name"), row.getString("skill_version"), row.getString("description"),
                row.getString("content_hash"), row.getString("required_tools_json"), row.getString("status"),
                row.getLong("authorization_revision"));
    }
}
