package com.agentsquad.squad.memory.repository;

import com.agentsquad.squad.memory.domain.MemoryRow;
import com.agentsquad.squad.memory.domain.MemoryScopePin;
import com.agentsquad.squad.memory.domain.MemorySource;
import com.agentsquad.squad.memory.domain.MemoryWriteReceiptRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 显式记忆、授权代际及执行引用的持久边界。
 * <p>
 * 不做SDK调用；写入方先锁用户记忆代际，再修改记忆与引用屏障，避免纠正与冻结交错。
 */
@Mapper
public interface MemoryMapper {
    int ensureRevision(@Param("user") String user);

    long lockRevision(@Param("user") String user);

    long revision(@Param("user") String user);

    int advanceRevision(@Param("user") String user);

    MemoryRow find(@Param("user") String user, @Param("id") String id);

    MemoryRow findActiveKey(@Param("user") String user, @Param("key") String key);

    List<MemoryRow> list(@Param("user") String user, @Param("before") Long before, @Param("limit") int limit);

    List<MemoryRow> active(@Param("user") String user, @Param("now") LocalDateTime now, @Param("limit") int limit);

    List<String> recall(@Param("user") String user, @Param("query") String query,
                        @Param("now") LocalDateTime now, @Param("limit") int limit);

    int insert(@Param("id") String id, @Param("user") String user, @Param("key") String key,
               @Param("content") String content, @Param("expires") LocalDateTime expires,
               @Param("sourceId") String sourceId, @Param("quote") String quote);

    int update(@Param("user") String user, @Param("id") String id, @Param("version") long version,
               @Param("content") String content, @Param("expires") LocalDateTime expires,
               @Param("sourceId") String sourceId, @Param("quote") String quote);

    int delete(@Param("user") String user, @Param("id") String id, @Param("version") long version);

    int blockOldScopes(@Param("user") String user, @Param("revision") long revision);

    int blockedScopes(@Param("user") String user);

    MemoryScopePin pin(@Param("user") String user, @Param("scope") String scope);

    int pinScope(@Param("user") String user, @Param("scope") String scope, @Param("revision") long revision);

    int pinItem(@Param("user") String user, @Param("scope") String scope, @Param("id") String id, @Param("version") long version);

    List<String> pinnedIds(@Param("user") String user, @Param("scope") String scope);

    Long pinnedVersion(@Param("user") String user, @Param("scope") String scope, @Param("id") String id);

    MemorySource source(@Param("user") String user, @Param("scope") String scope);

    MemoryWriteReceiptRow receipt(@Param("user") String user, @Param("scope") String scope, @Param("key") String key);

    int insertReceipt(@Param("user") String user, @Param("scope") String scope, @Param("key") String key,
                      @Param("hash") String hash, @Param("result") String result);

    int recordSource(@Param("user") String user, @Param("memoryId") String memoryId,
                     @Param("version") long version, @Param("messageId") String messageId);

    int invalidateSources(@Param("user") String user, @Param("memoryId") String memoryId);

    List<String> invalidatedSources(@Param("user") String user, @Param("now") LocalDateTime now);

    List<String> blockedScopeIds(@Param("user") String user, @Param("revision") long revision);

    List<String> staleScopeIds(@Param("user") String user, @Param("now") LocalDateTime now);

    List<String> sourceMessages(@Param("user") String user, @Param("source") String source);

    List<String> scopeMessages(@Param("user") String user, @Param("scope") String scope);
}
