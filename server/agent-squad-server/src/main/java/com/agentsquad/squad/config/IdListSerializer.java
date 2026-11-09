package com.agentsquad.squad.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 业务 ID 集合序列化器
 * <p>
 * 逐个写出集合内的 ID 字符串，保持数组结构与前端字符串契约一致。
 */
public class IdListSerializer extends JsonSerializer<Object> {

    static final IdListSerializer INSTANCE = new IdListSerializer();

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
        generator.writeStartArray();
        if (value instanceof Iterable<?> items) {
            for (Object item : items) {
                if (item == null) {
                    generator.writeNull();
                } else {
                    generator.writeString(item.toString());
                }
            }
        }
        generator.writeEndArray();
    }
}
