package com.agentsquad.squad.integration.agentscope;

import io.agentscope.core.model.Model;

/**
 * 单个模型Profile的运行客户端
 * <p>
 * 仅在内存复用线程安全模型客户端；配置或密钥指纹变化时替换，不把密钥写入缓存键、持久记录或日志。
 */
public record CachedReplyModel(String configurationStamp, Model model) {
}
