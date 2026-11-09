package com.agentsquad.squad.model.bo;

/**
 * 跨实例会话更新提示
 * <p>
 * 仅携带服务端实例和会话定位；接收方按持久游标读取公开事件，不信任通知作为答案或授权。
 */
public record ConversationWakeupNoticeBO(int schemaVersion, String instanceId, String userId, String conversationId) {
}
