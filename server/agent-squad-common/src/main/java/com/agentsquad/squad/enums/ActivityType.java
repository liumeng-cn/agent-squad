package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 执行活动类型
 * <p>
 * 用于持久活动与页面契约，不替代回复、目标或Worker的业务完成状态。
 */
@Getter
public enum ActivityType {
    CONTEXT_PREPARE(1, "CONTEXT_PREPARE", "上下文准备"),
    FILE_READ(2, "FILE_READ", "读取文件"),
    SKILL_LOAD(3, "SKILL_LOAD", "加载技能"),
    DATA_QUERY(4, "DATA_QUERY", "数据查询"),
    TASK_CREATE(5, "TASK_CREATE", "创建目标"),
    CAPABILITY_LOOKUP(6, "CAPABILITY_LOOKUP", "能力查询"),
    PLAN_PUBLISH(7, "PLAN_PUBLISH", "发布计划"),
    WORKER_DISPATCH(8, "WORKER_DISPATCH", "派发工作"),
    TASK_CHANGE(9, "TASK_CHANGE", "修改目标"),
    MODEL_CALL(10, "MODEL_CALL", "调用模型"),
    AUTO_RETRY_SCHEDULED(11, "AUTO_RETRY_SCHEDULED", "自动重试已安排"),
    USER_RETRY_SCHEDULED(12, "USER_RETRY_SCHEDULED", "用户重试已安排"),
    SCRIPT_EXECUTE(13, "SCRIPT_EXECUTE", "执行脚本"),
    EXTERNAL_ACTION(14, "EXTERNAL_ACTION", "外部动作"),
    MEMORY_WRITE(15, "MEMORY_WRITE", "写入记忆"),
    RESULT_VERIFY(16, "RESULT_VERIFY", "结果验证"),
    OPERATION_RECONCILE(17, "OPERATION_RECONCILE", "操作核对"),
    RESULT_AGGREGATE(18, "RESULT_AGGREGATE", "结果汇总"),
    RESULT_SUBMIT(19, "RESULT_SUBMIT", "提交结果"),
    ARTIFACT_COMMIT(20, "ARTIFACT_COMMIT", "产物提交"),
    ANSWER_PUBLISH(21, "ANSWER_PUBLISH", "发布回答"),
    USER_INTERACTION(22, "USER_INTERACTION", "用户交互"),
    TOOL_CALL(23, "TOOL_CALL", "工具调用");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ActivityType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
