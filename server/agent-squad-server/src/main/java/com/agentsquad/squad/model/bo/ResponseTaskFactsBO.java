package com.agentsquad.squad.model.bo;


import java.util.List;

/**
 * 来源回复冻结时观察到的当前目标事实。
 * <p>
 * 目标版本与计划节点状态一起保存，供Master说明真实进展；不授予聊天回复修改计划的权限。
 */
public record ResponseTaskFactsBO(String taskId, String status, long version, long planVersion,
                                  boolean cancellationRequested, List<ResponseJobFactBO> jobs, long goalRevision,
                                  List<TaskDirectiveFactBO> directives) {
}
