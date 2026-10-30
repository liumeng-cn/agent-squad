package com.agentsquad.squad.runtime;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;

/**
 * 迁移重放测试上下文。
 * <p>
 * 提供专用测试连接，不构造 Spring 服务或模拟执行收据。
 */
record ConversationMigrationContext(Connection connection) implements Context {

    @Override
    public Configuration getConfiguration() {
        return null;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }
}
