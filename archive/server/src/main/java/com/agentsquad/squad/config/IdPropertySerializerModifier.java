package com.agentsquad.squad.config;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * 业务 ID 属性序列化修正器
 * <p>
 * 名称以 Id 结尾的 Long 属性改用字符串输出，以 Ids 结尾的集合属性逐项输出字符串；
 * 其它数值字段不受影响。
 */
public class IdPropertySerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> writers) {
        for (BeanPropertyWriter writer : writers) {
            String name = writer.getName();
            Class<?> type = writer.getType().getRawClass();
            if (name.endsWith("Id") && (type == Long.class || type == long.class)) {
                writer.assignSerializer(ToStringSerializer.instance);
            } else if (name.endsWith("Ids") && Iterable.class.isAssignableFrom(type)) {
                writer.assignSerializer(IdListSerializer.INSTANCE);
            }
        }
        return writers;
    }
}
