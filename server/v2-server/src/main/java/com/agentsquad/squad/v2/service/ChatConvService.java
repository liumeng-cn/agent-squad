package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.common.utils.UUIDUtils;
import com.agentsquad.squad.common.vo.PageVO;
import com.agentsquad.squad.v2.converter.ChatConvConverter;
import com.agentsquad.squad.v2.config.properties.ChatAgentProperties;
import com.agentsquad.squad.v2.dto.ConvPageQueryDTO;
import com.agentsquad.squad.v2.enums.ConvTitleSource;
import com.agentsquad.squad.v2.mapper.ChatConvMapper;
import com.agentsquad.squad.v2.po.ChatConvPO;
import com.agentsquad.squad.v2.vo.ConvSummaryVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.SessionInbox;
import io.agentscope.core.session.SessionLog;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话目录业务
 * <p>
 * 管理对话的建档、改名、归档和删除；消息与执行事实由 SDK 原生持久化，本服务只维护目录事实。
 * 删除正在执行的对话会先请求原生停止，避免任务在后台静默运行且无法找回。
 */
@Slf4j
@Service
public class ChatConvService {

    @Resource
    private ChatConvMapper mapper;

    @Resource
    private ChatConvConverter converter;

    @Resource
    private HarnessAgent chatAgent;

    @Resource
    private ChatAgentProperties properties;

    /**
     * 按对话标识幂等建档
     * <p>
     * 对话标识由客户端生成，重试沿用同一标识；并发或重复建档由唯一约束裁决，返回已有记录，
     * 标题来自首次建档的用户发言，已有目录不覆盖标题，事务内不调用 SDK 或模型。
     */
    @Transactional
    public ChatConvPO register(String convId, String firstMessage) {
        ChatConvPO existing = findByConvId(convId);
        if (existing != null) {
            return existing;
        }

        long userId = ownerId();
        ChatConvPO conv = new ChatConvPO();
        conv.setUserId(userId);
        conv.setConvId(convId);
        conv.setSessionId(UUIDUtils.uuid());
        conv.setAgentDefinitionId(properties.getAgentId());
        conv.setTitle(initialTitle(firstMessage));
        conv.setTitleSource(ConvTitleSource.AUTO);
        conv.setVersion(0L);
        conv.setCreatorId(userId);
        conv.setCreatorName("");
        conv.setLastUpdaterId(userId);
        conv.setLastUpdaterName("");
        try {
            mapper.insert(conv);
        } catch (DuplicateKeyException duplicate) {
            ChatConvPO registered = findByConvId(convId);
            if (registered != null) {
                return registered;
            }
            throw duplicate;
        }
        log.info("对话建档完成 convId={}", convId);
        return conv;
    }

    /**
     * 首条发言压缩空白并按 Unicode 字符截取，避免截断表情等补充字符。
     */
    private String initialTitle(String firstMessage) {
        String normalized = firstMessage.replaceAll("(?U)\\s+", " ").strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length <= 40) {
            return normalized;
        }
        return normalized.substring(0, normalized.offsetByCodePoints(0, 40));
    }

    /**
     * 分页查询当前用户的对话目录
     */
    public PageVO<ConvSummaryVO> page(ConvPageQueryDTO request) {
        var query = Wrappers.lambdaQuery(ChatConvPO.class)
                .eq(ChatConvPO::getUserId, ownerId())
                .isNull(ChatConvPO::getDeletedAt)
                .isNull(!request.isArchived(), ChatConvPO::getArchivedAt)
                .isNotNull(request.isArchived(), ChatConvPO::getArchivedAt)
                .orderByDesc(ChatConvPO::getCreateTime)
                .orderByDesc(ChatConvPO::getId);
        Page<ChatConvPO> source = mapper.selectPage(new Page<>(request.getCurrent(), request.getSize()), query);

        List<ConvSummaryVO> records = new ArrayList<>();
        for (ChatConvPO conv : source.getRecords()) {
            records.add(converter.toSummary(conv));
        }
        PageVO<ConvSummaryVO> result = new PageVO<>();
        result.setRecords(records);
        result.setTotal(source.getTotal());
        result.setCurrent(source.getCurrent());
        result.setSize(source.getSize());
        return result;
    }

    /**
     * 读取当前用户的对话详情
     */
    public ConvSummaryVO read(String convId) {
        return converter.toSummary(require(convId));
    }

    /**
     * 按版本条件更新对话标题
     * <p>
     * 标题去除首尾空白后保存，更新成功来源固定为手动；版本不一致返回业务冲突，由客户端刷新版本后重试。
     */
    @Transactional
    public ConvSummaryVO rename(String convId, String title, Long version) {
        String normalized = title.strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 120) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "对话标题长度须为 1 至 120 个字符");
        }

        ChatConvPO conv = require(convId);
        ChatConvPO update = new ChatConvPO();
        update.setUserId(conv.getUserId());
        update.setConvId(conv.getConvId());
        update.setTitle(normalized);
        update.setTitleSource(ConvTitleSource.MANUAL);
        update.setVersion(version);
        update.setLastUpdaterId(conv.getUserId());
        if (mapper.updateTitle(update) == 0) {
            throw new BusinessException(ApiCode.CONFLICT, "对话标题已修改，请刷新后重试");
        }
        log.info("对话改名完成 convId={}", convId);
        return converter.toSummary(require(convId));
    }

    /**
     * 归档当前用户的对话，归档后从主列表移除
     */
    @Transactional
    public void archive(String convId) {
        markArchive(convId, Instant.now());
        log.info("对话归档完成 convId={}", convId);
    }

    /**
     * 恢复已归档的对话
     */
    @Transactional
    public void unarchive(String convId) {
        markArchive(convId, null);
        log.info("对话取消归档 convId={}", convId);
    }

    /**
     * 删除对话，正在执行的原生任务先收到停止请求
     * <p>
     * 删除是软删除，所有入口立即按不存在处理；停止请求只表示已接收，实际终止由 SDK 协作完成，
     * 删除返回不代表执行已经结束，目的是任务被移除后不在后台静默继续。
     */
    @Transactional
    public void delete(String convId) {
        ChatConvPO conv = require(convId);
        if (hasActiveRun(conv)) {
            try {
                resolveAgent(conv).session(runtimeContext(conv)).interrupt();
            } catch (RuntimeException exception) {
                log.warn("删除前请求停止对话执行失败 convId={}, msg={}", convId, exception.getMessage());
            }
        }

        ChatConvPO update = new ChatConvPO();
        update.setId(conv.getId());
        update.setLastUpdaterId(conv.getUserId());
        update.setDeletedAt(Instant.now());
        mapper.updateById(update);
        log.info("对话删除完成 convId={}", convId);
    }

    /**
     * 校验对话归属；不存在、已删除或不属于当前用户时返回统一的业务 404，不暴露其他用户的目录
     */
    public ChatConvPO require(String convId) {
        return requireOwned(convId, ownerId());
    }

    /**
     * 使用已认证的用户标识校验后台读取归属，不依赖请求线程。
     */
    public ChatConvPO requireOwned(String convId, long userId) {
        ChatConvPO conv = findByConvId(convId, userId);
        if (conv == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "对话不存在");
        }
        return conv;
    }

    private void markArchive(String convId, Instant archivedAt) {
        ChatConvPO conv = require(convId);
        ChatConvPO update = new ChatConvPO();
        update.setId(conv.getId());
        update.setLastUpdaterId(conv.getUserId());
        update.setArchivedAt(archivedAt);
        mapper.updateById(update);
    }

    /**
     * 通过原生日志判断对话是否仍有活动执行：收件箱存在未关闭的执行，且写租约仍有效
     */
    private boolean hasActiveRun(ChatConvPO conv) {
        SessionLog log = resolveAgent(conv).sessionLog(runtimeContext(conv));
        SessionInbox.Snapshot inbox = new SessionInbox(log).snapshot();
        return inbox.runId() != null
                && inbox.runId().equals(log.head().owner())
                && log.head().leaseUntil() > System.currentTimeMillis();
    }

    /**
     * 解析绑定Agent
     *
     * 当前仅注册配置中的聊天 Agent；绑定不可用时拒绝操作，避免访问其他 Agent 的会话。
     */
    public HarnessAgent resolveAgent(ChatConvPO conv) {
        if (!properties.getAgentId().equals(conv.getAgentDefinitionId())) {
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE, "对话绑定的 Agent 未注册");
        }
        return chatAgent;
    }

    private RuntimeContext runtimeContext(ChatConvPO conv) {
        return RuntimeContext.builder()
                .userId(conv.getUserId().toString())
                .sessionId(conv.getSessionId())
                .build();
    }

    private ChatConvPO findByConvId(String convId) {
        return findByConvId(convId, ownerId());
    }

    private ChatConvPO findByConvId(String convId, long userId) {
        var query = Wrappers.lambdaQuery(ChatConvPO.class)
                .eq(ChatConvPO::getUserId, userId)
                .eq(ChatConvPO::getConvId, convId)
                .isNull(ChatConvPO::getDeletedAt);
        return mapper.selectOne(query);
    }

    private long ownerId() {
        try {
            return Long.parseLong(CurrentUserContext.getUserId());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "用户身份无效");
        }
    }
}
