package com.agentsquad.squad.common.enums.execution;

/**
 * 执行活动类型
 * <p>
 * 用于持久活动与页面契约，不替代回复、目标或Worker的业务完成状态。
 */
public enum ActivityType {
    CONTEXT_PREPARE,
    FILE_READ,
    SKILL_LOAD,
    DATA_QUERY,
    TASK_CREATE,
    CAPABILITY_LOOKUP,
    PLAN_PUBLISH,
    WORKER_DISPATCH,
    TASK_CHANGE,
    MODEL_CALL,
    SCRIPT_EXECUTE,
    EXTERNAL_ACTION,
    MEMORY_WRITE,
    RESULT_VERIFY,
    OPERATION_RECONCILE,
    RESULT_AGGREGATE,
    RESULT_SUBMIT,
    ARTIFACT_COMMIT,
    ANSWER_PUBLISH,
    USER_INTERACTION,
    TOOL_CALL
}
