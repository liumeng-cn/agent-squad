package com.agentsquad.squad.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 生产运行配置守卫。
 * <p>
 * 拒绝自动执行DDL及不可信身份配置，仅检查配置值，不访问数据库或维护结构版本。
 */
@Component
@Profile("prod")
public class ProductionConfigurationGuard implements InitializingBean {
    @Value("${spring.sql.init.mode:always}")
    private String sqlInitMode;

    @Value("${agentsquad.identity.strict:false}")
    private boolean strictIdentity;

    @Value("${agentsquad.identity.enforce-user-status:false}")
    private boolean enforceUserStatus;

    @Value("${agentsquad.identity.allow-unsigned-local:false}")
    private boolean allowUnsignedLocal;

    /**
     * 启动前拒绝危险配置；建表由用户独立执行完整schema文件。
     */
    @Override
    public void afterPropertiesSet() {
        if (!"never".equalsIgnoreCase(sqlInitMode) || !strictIdentity
                || !enforceUserStatus || allowUnsignedLocal) {
            throw new IllegalStateException("生产必须关闭启动 DDL 和本地身份，并强制签名及用户状态检查");
        }
    }
}
