package com.agentsquad.experiments;

/**
 * 终结工具实验模式
 * <p>
 * 分别隔离审批、外部执行挂起与终结期间补充输入，不参与生产业务状态。
 */
enum ControlledFinishMode {
    APPROVAL, EXTERNAL, SUSPEND, BLOCKED
}
