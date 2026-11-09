package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.MemoryPO;
import com.agentsquad.squad.model.po.MemoryScopePinPO;
import com.agentsquad.squad.model.bo.MemorySourceBO;
import com.agentsquad.squad.model.po.MemoryWriteReceiptPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 显式记忆、授权代际及执行引用的持久化访问。
 * <p>
 * 不做SDK调用；写入方先锁用户记忆代际，再修改记忆与引用屏障，避免纠正与冻结交错。
 */
@Mapper
public interface MemoryMapper {
    int ensureRevision(@Param("user") Long user);

    long lockRevision(@Param("user") Long user);

    long revision(@Param("user") Long user);

    int advanceRevision(@Param("user") Long user);

    MemoryPO find(@Param("user") Long user, @Param("id") Long id);

    MemoryPO findActiveKey(@Param("user") Long user, @Param("key") String key);

    List<MemoryPO> list(@Param("user") Long user, @Param("before") Long before, @Param("limit") int limit);

    List<MemoryPO> active(@Param("user") Long user, @Param("now") LocalDateTime now, @Param("limit") int limit);

    List<Long> recall(@Param("user") Long user, @Param("query") String query,
                      @Param("now") LocalDateTime now, @Param("limit") int limit);

    int insert(@Param("id") Long id, @Param("user") Long user, @Param("key") String key,
               @Param("content") String content, @Param("expires") LocalDateTime expires,
               @Param("sourceId") Long sourceId, @Param("quote") String quote);

    int update(@Param("user") Long user, @Param("id") Long id, @Param("version") long version,
               @Param("content") String content, @Param("expires") LocalDateTime expires,
               @Param("sourceId") Long sourceId, @Param("quote") String quote);

    int delete(@Param("user") Long user, @Param("id") Long id, @Param("version") long version);

    int blockOldScopes(@Param("user") Long user, @Param("revision") long revision);

    int blockedScopes(@Param("user") Long user);

    MemoryScopePinPO pin(@Param("user") Long user, @Param("scope") Long scope);

    int pinScope(@Param("user") Long user, @Param("scope") Long scope, @Param("revision") long revision);

    int pinItem(@Param("user") Long user, @Param("scope") Long scope, @Param("id") Long id, @Param("version") long version);

    List<Long> pinnedIds(@Param("user") Long user, @Param("scope") Long scope);

    Long pinnedVersion(@Param("user") Long user, @Param("scope") Long scope, @Param("id") Long id);

    MemorySourceBO source(@Param("user") Long user, @Param("scope") Long scope);

    MemoryWriteReceiptPO receipt(@Param("user") Long user, @Param("scope") Long scope, @Param("key") String key);

    int insertReceipt(@Param("user") Long user, @Param("scope") Long scope, @Param("key") String key,
                      @Param("hash") String hash, @Param("result") String result);

    int recordSource(@Param("user") Long user, @Param("memoryId") Long memoryId,
                     @Param("version") long version, @Param("messageId") Long messageId);

    int invalidateSources(@Param("user") Long user, @Param("memoryId") Long memoryId);

    List<Long> invalidatedSources(@Param("user") Long user, @Param("now") LocalDateTime now);

    List<Long> blockedScopeIds(@Param("user") Long user, @Param("revision") long revision);

    List<Long> staleScopeIds(@Param("user") Long user, @Param("now") LocalDateTime now);

    List<Long> sourceMessages(@Param("user") Long user, @Param("source") Long source);

    List<Long> scopeMessages(@Param("user") Long user, @Param("scope") Long scope);
}
