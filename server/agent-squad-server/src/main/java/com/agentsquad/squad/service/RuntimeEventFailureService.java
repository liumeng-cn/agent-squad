package com.agentsquad.squad.service;

import com.agentsquad.squad.model.bo.RuntimeEventSourceBO;

import com.agentsquad.squad.mapper.RuntimeEventInboxMapper;

import com.agentsquad.squad.enums.RuntimeEventFailureCode;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 原生消费失败记录服务
 * <p>
 * 业务消费回滚后单独保存安全错误码、重试次数和时间；不回退已经成功消费的事件。
 */
@Service
public class RuntimeEventFailureService {
    private static final long RETRY_DELAY_SECONDS = 10;

    @Resource
    private RuntimeEventInboxMapper inboxMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(RuntimeEventSourceBO source, long sequence, RuntimeEventFailureCode code) {
        inboxMapper.recordFailure(source, sequence, code.status(), code,
                LocalDateTime.now(ZoneOffset.UTC).plusSeconds(RETRY_DELAY_SECONDS));
    }
}
