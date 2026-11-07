package com.agentsquad.squad.presentation.service;

import com.agentsquad.squad.common.enums.execution.ActivityGroup;
import com.agentsquad.squad.common.enums.execution.ActivityType;

/**
 * 当前工具的用户过程分类
 * <p>
 * 只描述真实工具边界，不改写工具业务逻辑；未知工具使用中性分类，不编造读取或外部操作。
 */
public final class ActivityToolRegistry {
    private ActivityToolRegistry() {
    }

    public static ActivityClassification classify(String tool) {
        return switch (tool) {
            case "read_attachment" ->
                    new ActivityClassification(ActivityType.FILE_READ, ActivityGroup.PREPARATION, "读取文件");
            case "load_skill_through_path" ->
                    new ActivityClassification(ActivityType.SKILL_LOAD, ActivityGroup.PREPARATION, "加载相关技能");
            case "prepare_context" ->
                    new ActivityClassification(ActivityType.CONTEXT_PREPARE, ActivityGroup.PREPARATION, "准备任务资料");
            case "mock_lookup" ->
                    new ActivityClassification(ActivityType.DATA_QUERY, ActivityGroup.PREPARATION, "查询资料");
            case "create_goal_task" ->
                    new ActivityClassification(ActivityType.TASK_CREATE, ActivityGroup.PLANNING, "建立任务");
            case "list_worker_capabilities" ->
                    new ActivityClassification(ActivityType.CAPABILITY_LOOKUP, ActivityGroup.PLANNING, "选择执行能力");
            case "publish_plan" ->
                    new ActivityClassification(ActivityType.PLAN_PUBLISH, ActivityGroup.PLANNING, "安排任务");
            case "apply_task_directive", "submit_task_directive" ->
                    new ActivityClassification(ActivityType.TASK_CHANGE, ActivityGroup.PLANNING, "处理要求修改");
            case "sandbox_python" ->
                    new ActivityClassification(ActivityType.SCRIPT_EXECUTE, ActivityGroup.EXECUTION, "执行脚本");
            case "mock_ticket_create" ->
                    new ActivityClassification(ActivityType.EXTERNAL_ACTION, ActivityGroup.EXECUTION, "执行外部操作");
            case "write_explicit_memory" ->
                    new ActivityClassification(ActivityType.MEMORY_WRITE, ActivityGroup.EXECUTION, "保存你要求记住的内容");
            case "request_acceptance_review" ->
                    new ActivityClassification(ActivityType.RESULT_VERIFY, ActivityGroup.VERIFICATION, "确认结果");
            case "report_job_result" ->
                    new ActivityClassification(ActivityType.RESULT_SUBMIT, ActivityGroup.DELIVERY, "提交任务结果");
            case "propose_completion" ->
                    new ActivityClassification(ActivityType.ANSWER_PUBLISH, ActivityGroup.DELIVERY, "准备交付回答");
            case "commit_artifact" ->
                    new ActivityClassification(ActivityType.ARTIFACT_COMMIT, ActivityGroup.DELIVERY, "保存结果文件");
            case "request_input" ->
                    new ActivityClassification(ActivityType.USER_INTERACTION, ActivityGroup.PREPARATION, "等待补充信息");
            default -> new ActivityClassification(ActivityType.TOOL_CALL, ActivityGroup.EXECUTION, "调用工具");
        };
    }
}
