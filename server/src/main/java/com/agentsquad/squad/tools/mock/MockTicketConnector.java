package com.agentsquad.squad.tools.mock;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.tools.operation.repository.MockTicketMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 本地模拟外部工单系统的连接器。external_key 是账本分配的幂等键；
 * 同键同摘要复用原单，模拟真实连接器的 KEY 幂等语义。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class MockTicketConnector {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MockTicketMapper mockTicketMapper;

    /**
     * 执行模拟写工单：同幂等键重复创建返回原单，不产生第二张工单。
     */
    public String createTicket(String userId, String externalKey, String title, String detail) {
        String requestHash = ContentDigests.sha256(title + "\n" + detail);
        if (mockTicketMapper.countByKey(userId, externalKey) > 0) {
            String priorHash = mockTicketMapper.findRequestHash(userId, externalKey);
            if (!priorHash.equals(requestHash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                        "外部系统同键不同内容，拒绝覆盖");
            }
            return RuntimePayloads.toJson(objectMapper, Map.of(
                    "ticketRef", externalKey, "duplicated", true));
        }
        mockTicketMapper.insert(userId, externalKey, requestHash, title, detail);
        log.info("模拟工单已创建 userId={} ticketRef={} titleLength={}", userId, externalKey, title.length());
        return RuntimePayloads.toJson(objectMapper, Map.of(
                "ticketRef", externalKey, "duplicated", false));
    }

    /**
     * 确定性读结果；不产生副作用，只返回稳定摘要供模型使用。
     */
    public String lookup(String resource, String key) {
        Map<String, Object> record = Map.of(
                "resource", resource,
                "key", key,
                "status", "ACTIVE",
                "digest", ContentDigests.sha256(resource + ":" + key).substring(0, 12));
        return RuntimePayloads.toJson(objectMapper, record);
    }
}
