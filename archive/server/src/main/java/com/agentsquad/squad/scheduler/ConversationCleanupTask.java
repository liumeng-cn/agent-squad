package com.agentsquad.squad.scheduler;


import com.agentsquad.squad.model.po.ConversationPO;
import com.agentsquad.squad.mapper.ConversationMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 待删除会话清理定时任务
 *
 * <p>扫描已进入删除流程且没有在途目标的会话，推进清理。</p>
 */
@Component
@Slf4j
public class ConversationCleanupTask {

    private static final int CLEANUP_BATCH = 50;

    @Resource
    private ConversationMapper conversationMapper;

    /**
     * 分批提交可清理会话，使用条件更新协调多个实例并发清理。
     */
    public void cleanupDeletingConversations() {
        List<ConversationPO> deletable = conversationMapper.findDeletable(CLEANUP_BATCH);
        for (ConversationPO row : deletable) {
            if (conversationMapper.markDeleted(row.getUserId(), row.getConversationId()) == 1) {
                log.info("会话已逻辑删除 conversationId={} userId={}", row.getConversationId(), row.getUserId());
            }
        }
    }
}
