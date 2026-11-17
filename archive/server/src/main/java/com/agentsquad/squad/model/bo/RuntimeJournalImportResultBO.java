package com.agentsquad.squad.model.bo;

/**
 * 本地原生日志导入结果
 * <p>
 * 仅统计已校验的会话与存储对象，不包含正文、工具参数或模型内部内容。
 */
public record RuntimeJournalImportResultBO(int users, int sessions, long objects, long bytes) {
}
