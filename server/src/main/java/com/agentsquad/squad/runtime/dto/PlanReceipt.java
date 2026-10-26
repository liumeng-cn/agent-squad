package com.agentsquad.squad.runtime.dto;

import java.util.List;

/**
 * publish_plan 的持久回执。
 * <p>
 * 返回计划标识与全部委派节点 ID，不等待任何子节点执行完成。
 */
public record PlanReceipt(String planId, String taskId, long planVersion, List<String> jobIds) {
}
