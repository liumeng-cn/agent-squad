package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.dto.PageDTO;
import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.UUIDUtils;
import com.agentsquad.squad.v2.config.properties.ChatAgentProperties;
import com.agentsquad.squad.v2.dto.ConvRenameDTO;
import com.agentsquad.squad.v2.enums.SessionTitleSource;
import com.agentsquad.squad.v2.mapper.ChatConvMapper;
import com.agentsquad.squad.v2.po.ChatConvPO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 对话目录管理
 * <p>
 * 管理归属、标题和分页排序，事务只覆盖业务目录。
 */
@Service
public class ChatConvService {

    @Resource
    private ChatConvMapper mapper;

    @Resource
    private ChatAgentProperties properties;

    /**
     * 创建对话并固定原生会话关联
     */
    @Transactional
    public ChatConvPO create(String userId) {
        long ownerId = ownerId(userId);
        var conv = new ChatConvPO();
        conv.setUserId(ownerId);
        conv.setConvId(UUIDUtils.uuid());
        conv.setAgentId(properties.getAgentId());
        conv.setSessionId(UUIDUtils.uuid());
        conv.setTitle("新对话");
        conv.setTitleSource(SessionTitleSource.AUTO);
        conv.setVersion(0L);
        conv.setArchived(false);
        conv.setDeleted(false);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        conv.setCreatorId(ownerId);
        conv.setCreatorName("");
        conv.setCreateTime(now);
        conv.setLastUpdaterId(ownerId);
        conv.setLastUpdaterName("");
        conv.setLastUpdateTime(now);

        mapper.insert(conv);
        return conv;
    }

    /**
     * 通过标题版本避免覆盖并发改名
     */
    @Transactional
    public ChatConvPO rename(String convId, String userId, ConvRenameDTO request) {
        String title = request.getTitle().strip();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > 120) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "对话标题长度须为 1 至 120 个字符");
        }

        var conv = require(convId, userId);
        conv.setTitle(title);
        conv.setTitleSource(SessionTitleSource.MANUAL);
        conv.setVersion(request.getVersion());
        conv.setLastUpdaterId(conv.getUserId());

        if (mapper.updateTitle(conv) == 0) {
            throw new BusinessException(ApiCode.CONFLICT, "对话标题已修改，请刷新后重试");
        }
        return require(convId, userId);
    }

    /**
     * 按创建时间分页查询对话
     */
    public Page<ChatConvPO> page(String userId, PageDTO request) {
        var query = Wrappers.lambdaQuery(ChatConvPO.class)
                .eq(ChatConvPO::getUserId, ownerId(userId))
                .eq(ChatConvPO::getDeleted, false)
                .eq(ChatConvPO::getArchived, false)
                .orderByDesc(ChatConvPO::getCreateTime, ChatConvPO::getId);
        return mapper.selectPage(new Page<>(request.getCurrent(), request.getSize()), query);
    }

    /**
     * 标记对话删除
     */
    @Transactional
    public void delete(String convId, String userId) {
        var conv = require(convId, userId);
        mapper.update(null, Wrappers.lambdaUpdate(ChatConvPO.class)
                .eq(ChatConvPO::getId, conv.getId())
                .set(ChatConvPO::getDeleted, true));
    }

    /**
     * 保存对话归档状态
     */
    @Transactional
    public void archive(String convId, String userId, boolean archived) {
        var conv = require(convId, userId);
        mapper.update(null, Wrappers.lambdaUpdate(ChatConvPO.class)
                .eq(ChatConvPO::getId, conv.getId())
                .set(ChatConvPO::getArchived, archived));
    }

    /**
     * 校验对话归属
     */
    public ChatConvPO require(String convId, String userId) {
        var query = Wrappers.lambdaQuery(ChatConvPO.class)
                .eq(ChatConvPO::getUserId, ownerId(userId))
                .eq(ChatConvPO::getConvId, convId)
                .eq(ChatConvPO::getDeleted, false);
        var conv = mapper.selectOne(query);
        if (conv == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "对话不存在");
        }
        return conv;
    }

    /**
     * 校验原生会话归属
     */
    public ChatConvPO requireSession(String sessionId, String userId) {
        var query = Wrappers.lambdaQuery(ChatConvPO.class)
                .eq(ChatConvPO::getUserId, ownerId(userId))
                .eq(ChatConvPO::getSessionId, sessionId)
                .eq(ChatConvPO::getDeleted, false);
        var conv = mapper.selectOne(query);
        if (conv == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "对话不存在");
        }
        return conv;
    }

    private long ownerId(String userId) {
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "用户身份无效");
        }
    }
}
