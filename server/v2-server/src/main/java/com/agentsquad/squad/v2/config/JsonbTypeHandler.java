package com.agentsquad.squad.v2.config;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL JSONB 字段转换
 * <p>
 * 读取时按字段泛型还原为 Map、List 或配置对象；写入时以未指定类型传参，由数据库转换为 jsonb。
 */
public class JsonbTypeHandler extends JacksonTypeHandler {
    public JsonbTypeHandler(Class<?> type) {
        super(type);
    }

    public JsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Object parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setObject(index, toJson(parameter), Types.OTHER);
    }
}
