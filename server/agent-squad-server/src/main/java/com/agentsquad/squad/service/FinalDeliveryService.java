package com.agentsquad.squad.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.model.po.FinalDeliveryPO;
import com.agentsquad.squad.mapper.FinalDeliveryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同源用户请求的最终交付裁决。
 * <p>
 * 与受理、任务转交及消息发布共用事务；独立代际只由用户显式重试建立，原生恢复复用原交付位。
 */
@Service
@Slf4j
@Transactional(propagation = Propagation.MANDATORY)
public class FinalDeliveryService {
    @Resource
    private FinalDeliveryMapper deliveries;

    public void initialize(Long userId, Long sourceMessageId, Long executionId) {
        deliveries.insert(IdWorker.getId(), userId, sourceMessageId, 1, executionId);
    }

    /**
     * 来源回复持锁后转交当前代际，发布已发生或归属变化时整个任务创建事务回滚。
     */
    public void transfer(Long userId, Long sourceMessageId, Long executionId, Long taskId) {
        FinalDeliveryPO row = requireCurrent(userId, sourceMessageId);
        if (!row.ownedBy("RESPONSE", executionId) || !"OWNED".equals(row.getState())
                || deliveries.transfer(userId, sourceMessageId, row.getGeneration(), executionId, taskId) != 1) {
            throw conflict("当前请求的最终交付权已转交或发布，不能创建目标");
        }
    }

    /**
     * 只有当前所有者可持久发布；重放复用原消息ID，迟到旧代际和委派后的回复不公开模型正文。
     */
    public Long claimPublication(Long userId, Long sourceMessageId, String ownerType, Long ownerId) {
        FinalDeliveryPO row = requireCurrent(userId, sourceMessageId);
        if (!row.ownedBy(ownerType, ownerId) || "CLOSED".equals(row.getState())) {
            log.info("最终交付候选未获发布权 sourceMessageId={} ownerType={} ownerId={} generation={}",
                    sourceMessageId, ownerType, ownerId, row.getGeneration());
            return null;
        }
        if ("PUBLISHED".equals(row.getState())) {
            return row.getPublishedMessageId();
        }
        Long messageId = IdWorker.getId();
        if (deliveries.publish(userId, sourceMessageId, row.getGeneration(), messageId) != 1) {
            throw conflict("最终交付状态已变化");
        }
        return messageId;
    }

    /**
     * 取消只关闭本所有者的未发布交付位，不撤销其他任务或已完成结果。
     */
    public void close(Long userId, Long sourceMessageId, String ownerType, Long ownerId) {
        FinalDeliveryPO row = requireCurrent(userId, sourceMessageId);
        if (row.ownedBy(ownerType, ownerId)) {
            deliveries.close(userId, sourceMessageId, row.getGeneration());
        }
    }

    /**
     * 已通过失败重试校验后建立后继代际；委派任务的原回复不能另建第二条交付链。
     */
    public void retry(Long userId, Long sourceMessageId, Long previousExecutionId, Long executionId) {
        FinalDeliveryPO row = requireCurrent(userId, sourceMessageId);
        if (!row.ownedBy("RESPONSE", previousExecutionId)) {
            throw conflict("该请求已委派或已有后继回复，不能重试原回复");
        }
        deliveries.close(userId, sourceMessageId, row.getGeneration());
        deliveries.insert(IdWorker.getId(), userId, sourceMessageId, row.getGeneration() + 1, executionId);
    }

    private FinalDeliveryPO requireCurrent(Long userId, Long sourceMessageId) {
        FinalDeliveryPO row = deliveries.lockCurrent(userId, sourceMessageId);
        if (row == null) {
            throw conflict("请求缺少最终交付事实，需先核对存储结构与来源");
        }
        return row;
    }

    private RuntimeBusinessException conflict(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, message);
    }
}
