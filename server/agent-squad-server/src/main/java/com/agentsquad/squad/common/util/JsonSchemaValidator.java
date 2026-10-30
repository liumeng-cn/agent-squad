package com.agentsquad.squad.common.util;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

/**
 * 受控 JSON Schema 契约校验器。
 * <p>
 * 使用固定版本的 JSON Schema 实现校验已发布定义与实际数据；禁止外部引用，
 * 防止发布或执行时从模型提供的 Schema 发起网络读取。
 */
@Component
public class JsonSchemaValidator {

    private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12);

    /**
     * 发布前编译 Schema，避免不可执行的能力定义进入目录。
     */
    public void requireValidSchema(JsonNode schema, String field) {
        if (schema == null || !schema.isObject()) {
            throw invalid(field + " 必须是 JSON Schema 对象");
        }
        requireLocalReferences(schema);
        try {
            registry.getSchema(schema);
        } catch (RuntimeException exception) {
            throw invalid(field + " 不是有效的 JSON Schema");
        }
    }

    /**
     * 计划发布和 Worker 结果提交均对固定能力版本的契约做校验；错误不回显正文。
     */
    public void requireMatches(JsonNode value, JsonNode schema, String field) {
        requireValidSchema(schema, field);
        try {
            Schema compiled = registry.getSchema(schema);
            if (!compiled.validate(value).isEmpty()) {
                throw invalid(field + " 与 Schema 不匹配");
            }
        } catch (RuntimeBusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid(field + " 无法按 Schema 校验");
        }
    }

    private void requireLocalReferences(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if ("$ref".equals(field.getKey()) || "$dynamicRef".equals(field.getKey())) {
                    if (!field.getValue().isTextual()
                            || !field.getValue().asText().startsWith("#/$defs/")) {
                        throw invalid("Schema 只能引用本定义内的 $defs");
                    }
                }
                requireLocalReferences(field.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                requireLocalReferences(child);
            }
        }
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
