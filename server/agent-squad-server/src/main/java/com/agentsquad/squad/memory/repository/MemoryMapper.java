package com.agentsquad.squad.memory.repository;

import com.agentsquad.squad.memory.domain.MemoryRow;
import com.agentsquad.squad.memory.domain.MemoryScopePin;
import com.agentsquad.squad.memory.domain.MemorySource;
import com.agentsquad.squad.memory.domain.MemoryWriteReceiptRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 显式记忆、授权代际及执行引用的持久边界。
 * <p>
 * 不做SDK调用；写入方先锁用户记忆代际，再修改记忆与引用屏障，避免纠正与冻结交错。
 */
@Mapper
public interface MemoryMapper {
    @Insert("INSERT INTO sq_memory_revision (user_id, revision) VALUES (#{user}, 0) ON DUPLICATE KEY UPDATE user_id = user_id")
    int ensureRevision(@Param("user") String user);

    @Select("SELECT revision FROM sq_memory_revision WHERE user_id = #{user} FOR UPDATE")
    long lockRevision(@Param("user") String user);

    @Select("SELECT COALESCE(MAX(revision), 0) FROM sq_memory_revision WHERE user_id = #{user}")
    long revision(@Param("user") String user);

    @Update("UPDATE sq_memory_revision SET revision = revision + 1 WHERE user_id = #{user}")
    int advanceRevision(@Param("user") String user);

    @ConstructorArgs({
            @Arg(column = "memory_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "memory_key", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "expires_at", javaType = LocalDateTime.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "source_quote", javaType = String.class),
            @Arg(column = "id", javaType = long.class)
    })
    @Select("SELECT memory_id, user_id, memory_key, content, status, version, expires_at, source_message_id, source_quote, id FROM sq_memory WHERE user_id = #{user} AND memory_id = #{id} FOR UPDATE")
    MemoryRow find(@Param("user") String user, @Param("id") String id);

    @ConstructorArgs({
            @Arg(column = "memory_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "memory_key", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "expires_at", javaType = LocalDateTime.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "source_quote", javaType = String.class),
            @Arg(column = "id", javaType = long.class)
    })
    @Select("SELECT memory_id, user_id, memory_key, content, status, version, expires_at, source_message_id, source_quote, id FROM sq_memory WHERE user_id = #{user} AND memory_key = #{key} AND status = 'ACTIVE' FOR UPDATE")
    MemoryRow findActiveKey(@Param("user") String user, @Param("key") String key);

    @ConstructorArgs({
            @Arg(column = "memory_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "memory_key", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "expires_at", javaType = LocalDateTime.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "source_quote", javaType = String.class),
            @Arg(column = "id", javaType = long.class)
    })
    @Select("SELECT memory_id, user_id, memory_key, content, status, version, expires_at, source_message_id, source_quote, id FROM sq_memory WHERE user_id = #{user} AND (#{before} IS NULL OR id < #{before}) ORDER BY id DESC LIMIT #{limit}")
    List<MemoryRow> list(@Param("user") String user, @Param("before") Long before, @Param("limit") int limit);

    @ConstructorArgs({
            @Arg(column = "memory_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "memory_key", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "expires_at", javaType = LocalDateTime.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "source_quote", javaType = String.class),
            @Arg(column = "id", javaType = long.class)
    })
    @Select("SELECT memory_id, user_id, memory_key, content, status, version, expires_at, source_message_id, source_quote, id FROM sq_memory WHERE user_id = #{user} AND status = 'ACTIVE' AND (expires_at IS NULL OR expires_at > #{now}) ORDER BY id LIMIT #{limit} FOR UPDATE")
    List<MemoryRow> active(@Param("user") String user, @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("""
            SELECT memory_id FROM sq_memory
            WHERE user_id = #{user} AND status = 'ACTIVE'
              AND (expires_at IS NULL OR expires_at > #{now})
              AND (#{query} IS NULL OR memory_key = #{query} OR LOCATE(#{query}, content) > 0)
            ORDER BY id LIMIT #{limit}
            """)
    List<String> recall(@Param("user") String user, @Param("query") String query,
                        @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Insert("""
            INSERT INTO sq_memory (
                memory_id, user_id, memory_key, content, expires_at, source_message_id, source_quote
            ) VALUES (
                #{id}, #{user}, #{key}, #{content}, #{expires}, #{sourceId}, #{quote}
            )
            """)
    int insert(@Param("id") String id, @Param("user") String user, @Param("key") String key,
               @Param("content") String content, @Param("expires") LocalDateTime expires,
               @Param("sourceId") String sourceId, @Param("quote") String quote);

    @Update("""
            UPDATE sq_memory
            SET content           = #{content},
                expires_at        = #{expires},
                source_message_id = #{sourceId},
                source_quote      = #{quote},
                version           = version + 1
            WHERE user_id = #{user} AND memory_id = #{id} AND version = #{version} AND status = 'ACTIVE'
            """)
    int update(@Param("user") String user, @Param("id") String id, @Param("version") long version,
               @Param("content") String content, @Param("expires") LocalDateTime expires,
               @Param("sourceId") String sourceId, @Param("quote") String quote);

    @Update("""
            UPDATE sq_memory
            SET content = '', source_quote = NULL, status = 'DELETED', version = version + 1
            WHERE user_id = #{user} AND memory_id = #{id} AND version = #{version} AND status = 'ACTIVE'
            """)
    int delete(@Param("user") String user, @Param("id") String id, @Param("version") long version);

    @Update("""
            UPDATE sq_memory_scope SET barrier_status = 'BLOCKED'
            WHERE user_id = #{user} AND memory_revision < #{revision} AND barrier_status = 'CURRENT'
            """)
    int blockOldScopes(@Param("user") String user, @Param("revision") long revision);

    @Select("SELECT COUNT(*) FROM sq_memory_scope WHERE user_id = #{user} AND barrier_status = 'BLOCKED'")
    int blockedScopes(@Param("user") String user);

    @ConstructorArgs({
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "memory_revision", javaType = long.class),
            @Arg(column = "barrier_status", javaType = String.class)
    })
    @Select("SELECT scope_id, memory_revision, barrier_status FROM sq_memory_scope WHERE user_id = #{user} AND scope_id = #{scope} FOR UPDATE")
    MemoryScopePin pin(@Param("user") String user, @Param("scope") String scope);

    @Insert("INSERT INTO sq_memory_scope (user_id, scope_id, memory_revision) VALUES (#{user}, #{scope}, #{revision})")
    int pinScope(@Param("user") String user, @Param("scope") String scope, @Param("revision") long revision);

    @Insert("INSERT INTO sq_memory_scope_item (user_id, scope_id, memory_id, memory_version) VALUES (#{user}, #{scope}, #{id}, #{version})")
    int pinItem(@Param("user") String user, @Param("scope") String scope, @Param("id") String id, @Param("version") long version);

    @Select("SELECT memory_id FROM sq_memory_scope_item WHERE user_id = #{user} AND scope_id = #{scope} ORDER BY id FOR UPDATE")
    List<String> pinnedIds(@Param("user") String user, @Param("scope") String scope);

    @Select("SELECT memory_version FROM sq_memory_scope_item WHERE user_id = #{user} AND scope_id = #{scope} AND memory_id = #{id} FOR UPDATE")
    Long pinnedVersion(@Param("user") String user, @Param("scope") String scope, @Param("id") String id);

    @ConstructorArgs({
            @Arg(column = "message_id", javaType = String.class),
            @Arg(column = "content", javaType = String.class)
    })
    @Select("""
            SELECT m.message_id, m.content FROM sq_response_execution r
            JOIN sq_message m ON m.user_id = r.user_id AND m.message_id = r.source_message_id
            WHERE r.user_id = #{user} AND r.scope_id = #{scope} AND m.role = 'USER' AND m.del_flag = '0'
            """)
    MemorySource source(@Param("user") String user, @Param("scope") String scope);

    @ConstructorArgs({
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "result_json", javaType = String.class)
    })
    @Select("SELECT payload_hash, result_json FROM sq_memory_write_receipt WHERE user_id = #{user} AND scope_id = #{scope} AND logical_key = #{key} FOR UPDATE")
    MemoryWriteReceiptRow receipt(@Param("user") String user, @Param("scope") String scope, @Param("key") String key);

    @Insert("""
            INSERT INTO sq_memory_write_receipt (user_id, scope_id, logical_key, payload_hash, result_json)
            VALUES (#{user}, #{scope}, #{key}, #{hash}, #{result})
            """)
    int insertReceipt(@Param("user") String user, @Param("scope") String scope, @Param("key") String key,
                      @Param("hash") String hash, @Param("result") String result);

    @Insert("""
            INSERT INTO sq_memory_source (user_id, memory_id, memory_version, source_message_id)
            VALUES (#{user}, #{memoryId}, #{version}, #{messageId})
            ON DUPLICATE KEY UPDATE status = status
            """)
    int recordSource(@Param("user") String user, @Param("memoryId") String memoryId,
                     @Param("version") long version, @Param("messageId") String messageId);

    @Update("""
            UPDATE sq_memory_source SET status = 'INVALIDATED'
            WHERE user_id = #{user} AND memory_id = #{memoryId} AND status = 'ACTIVE'
            """)
    int invalidateSources(@Param("user") String user, @Param("memoryId") String memoryId);

    @Select("""
            SELECT s.source_message_id FROM sq_memory_source s
            JOIN sq_memory m ON m.user_id = s.user_id AND m.memory_id = s.memory_id
            WHERE s.user_id = #{user}
              AND (s.status = 'INVALIDATED' OR m.status <> 'ACTIVE'
                OR (m.expires_at IS NOT NULL AND m.expires_at <= #{now}))
            FOR UPDATE
            """)
    List<String> invalidatedSources(@Param("user") String user, @Param("now") LocalDateTime now);

    @Select("""
            SELECT scope_id FROM sq_memory_scope
            WHERE user_id = #{user} AND (barrier_status = 'BLOCKED' OR memory_revision <> #{revision})
            FOR UPDATE
            """)
    List<String> blockedScopeIds(@Param("user") String user, @Param("revision") long revision);

    @Select("""
            SELECT i.scope_id FROM sq_memory_scope_item i
            JOIN sq_memory m ON m.user_id = i.user_id AND m.memory_id = i.memory_id
            WHERE i.user_id = #{user} AND (m.status <> 'ACTIVE' OR m.version <> i.memory_version
              OR (m.expires_at IS NOT NULL AND m.expires_at <= #{now}))
            FOR UPDATE
            """)
    List<String> staleScopeIds(@Param("user") String user, @Param("now") LocalDateTime now);

    @Select("""
            SELECT message_id FROM sq_message
            WHERE user_id = #{user} AND (message_id = #{source} OR source_message_id = #{source})
            """)
    List<String> sourceMessages(@Param("user") String user, @Param("source") String source);

    @Select("""
            SELECT m.message_id FROM sq_message m
            LEFT JOIN sq_response_execution r ON r.user_id = m.user_id AND r.execution_id = m.execution_id
            LEFT JOIN sq_goal_task t ON t.user_id = m.user_id AND t.task_id = m.task_id
            WHERE m.user_id = #{user} AND m.role = 'ASSISTANT' AND (r.scope_id = #{scope} OR t.scope_id = #{scope})
            """)
    List<String> scopeMessages(@Param("user") String user, @Param("scope") String scope);
}
