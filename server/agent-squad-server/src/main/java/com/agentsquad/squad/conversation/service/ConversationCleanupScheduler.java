package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.conversation.domain.ConversationRow;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会话删除异步收尾。把已无在途目标的 DELETING 会话幂等置为 DELETED。
 *
 * <p>运行中目标须等原生投影终态释放 activeTaskId 后才可被清理；
 * 存在未完成目标时会话保持 DELETING 可见阻塞，不伪装删除成功。</p>
 */
@Component
@Slf4j
public class ConversationCleanupScheduler {

    private static final int CLEANUP_BATCH = 50;

    @Resource
    private ConversationMapper conversationMapper;

    /**
     * 批量清理无在途目标的删除中会话；CAS 保证并发调度下仅清理一次。
     */
    @Scheduled(fixedDelayString = "${agentsquad.conversation.cleanup.delay-ms:2000}")
    public void cleanupDeletingConversations() {
        List<ConversationRow> deletable = conversationMapper.findDeletable(CLEANUP_BATCH);
        for (ConversationRow row : deletable) {
            if (conversationMapper.markDeleted(row.userId(), row.conversationId()) == 1) {
                log.info("会话已逻辑删除 conversationId={} userId={}", row.conversationId(), row.userId());
            }
        }
    }
}
