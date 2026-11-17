package com.agentsquad.squad.observer;

/**
 * 全链路追踪的固定步骤名。
 * <p>
 * 名称与设计文档保持一致，禁止在业务代码中散落自定义字符串，便于按步骤聚合和检索。
 */
public final class ObservationNames {

    public static final String HTTP_REQUEST = "http.request";
    public static final String MESSAGE_ACCEPT = "message.accept";
    public static final String COMMAND_DELIVER = "command.deliver";
    public static final String INVOKE_AGENT = "invoke_agent";
    public static final String MODEL_CALL = "model.call";
    public static final String TOOL_EXECUTE = "tool.execute";
    public static final String PLAN_PUBLISH = "plan.publish";
    public static final String WORKER_DISPATCH = "worker.dispatch";
    public static final String EVENT_APPLY = "event.apply";
    public static final String INTERACTION_WAIT = "interaction.wait";
    public static final String ANSWER_PUBLISH = "answer.publish";
    public static final String EVENT_NOTIFY = "event.notify";

    private ObservationNames() {
    }
}
