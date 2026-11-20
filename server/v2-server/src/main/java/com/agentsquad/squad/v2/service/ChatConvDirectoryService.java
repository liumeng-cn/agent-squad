package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.dto.PageDTO;
import com.agentsquad.squad.common.vo.PageVO;
import com.agentsquad.squad.v2.converter.ChatConvConverter;
import com.agentsquad.squad.v2.dto.ConvRenameDTO;
import com.agentsquad.squad.v2.vo.ConvSummaryVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * 对话目录接口编排
 * <p>
 * 分离目录写入和原生状态读取，业务事务提交后才组装响应。
 */
@Service
public class ChatConvDirectoryService {

    @Resource
    private ChatConvService convs;

    @Resource
    private ChatConvConverter converter;

    /**
     * 分页加载对话目录
     */
    public PageVO<ConvSummaryVO> list(String userId, PageDTO request) {
        var source = convs.page(userId, request);
        var records = new ArrayList<ConvSummaryVO>();
        for (var conv : source.getRecords()) {
            records.add(converter.toSummary(conv));
        }

        var result = new PageVO<ConvSummaryVO>();
        result.setRecords(records);
        result.setTotal(source.getTotal());
        result.setCurrent(source.getCurrent());
        result.setSize(source.getSize());
        return result;
    }

    /**
     * 读取对话目录及其原生会话关联
     */
    public ConvSummaryVO read(String convId, String userId) {
        var conv = convs.require(convId, userId);
        return converter.toSummary(conv);
    }

    public ConvSummaryVO rename(String convId, String userId, ConvRenameDTO request) {
        var conv = convs.rename(convId, userId, request);
        return converter.toSummary(conv);
    }

}
