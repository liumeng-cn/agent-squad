package com.agentsquad.squad.testsupport;

import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地模型桩的逐项评价载荷
 * <p>
 * 使用隔离测试库当前计划的真实条件和成功结果编号构造模型意见，不替代服务端证据检查断言。
 */
public final class ModelAcceptanceFixture {
    private ModelAcceptanceFixture() {
    }

    public static Map<String, Object> completion(ObjectMapper json, JdbcTemplate db, String outcome, String answer) {
        try {
            var rows = db.queryForList("SELECT task_id,plan_version,definition_json FROM sq_plan WHERE status='ACTIVE' ORDER BY id DESC LIMIT 1");
            var result = new LinkedHashMap<String, Object>();
            result.put("outcome", outcome);
            result.put("finalAnswer", answer);
            if (!rows.isEmpty()) {
                var row = rows.get(0);
                var plan = json.readValue(String.valueOf(row.get("definition_json")), PlanPublish.class);
                var ids = db.queryForList("SELECT job_id FROM sq_worker_job WHERE task_id=? AND plan_version=? AND status='SUCCEEDED' ORDER BY id",
                        String.class, row.get("task_id"), row.get("plan_version"));
                var reviews = new ArrayList<Map<String, Object>>();
                for (var criterion : plan.acceptanceCriteria()) {
                    reviews.add(Map.of("criterionId", criterion.criterionId(), "verdict", "PASS", "evidenceRefs", ids));
                }
                result.put("acceptanceReviews", reviews);
            }
            return result;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("测试计划无法解析", exception);
        }
    }

    public static Map<String, Object> structuredPlan(Map<String, Object> input) {
        var result = new LinkedHashMap<>(input);
        if (input.get("acceptanceCriteria") instanceof List<?> criteria) {
            var converted = new ArrayList<Object>();
            for (var criterion : criteria) {
                converted.add(criterion instanceof String text ? Map.of("criterionId", "criterion-" + converted.size(),
                        "text", text, "required", true, "verificationType", "MODEL_REVIEW") : criterion);
            }
            result.put("acceptanceCriteria", converted);
        }
        return result;
    }
}
