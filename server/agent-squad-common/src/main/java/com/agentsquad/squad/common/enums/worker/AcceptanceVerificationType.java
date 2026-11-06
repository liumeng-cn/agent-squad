package com.agentsquad.squad.common.enums.worker;

/**
 * 完成条件的检查方式
 * <p>
 * 程序检查、模型评价与用户确认分别记录，不将模型评价伪装成程序验证。
 */
public enum AcceptanceVerificationType {
    DETERMINISTIC,
    MODEL_REVIEW,
    USER_REVIEW
}
