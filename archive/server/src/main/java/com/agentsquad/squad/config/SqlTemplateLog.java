package com.agentsquad.squad.config;

import org.apache.ibatis.logging.slf4j.Slf4jImpl;

/**
 * MyBatis SQL 模板日志
 *
 * <p>输出 JDBC 执行的 SQL 模板，跳过绑定参数、结果行和其他调试消息。</p>
 */
public class SqlTemplateLog extends Slf4jImpl {

    public SqlTemplateLog(String loggerName) {
        super(loggerName);
    }

    @Override
    public void debug(String message) {
        int marker = message.indexOf('>');
        if (marker < 0) {
            return;
        }
        String detail = message.substring(marker + 1).stripLeading();
        if (detail.startsWith("Preparing:") || detail.startsWith("Executing:")) {
            super.debug(message);
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }
}
