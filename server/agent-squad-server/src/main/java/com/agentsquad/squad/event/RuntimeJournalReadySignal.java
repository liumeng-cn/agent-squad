package com.agentsquad.squad.event;

import com.agentsquad.squad.model.bo.RuntimeEventSourceBO;

/**
 * 原生持久收件的绑定唤醒
 * <p>
 * 仅声明已提交事实可供补读，不把通知序号当作业务消费游标。
 */
public record RuntimeJournalReadySignal(RuntimeEventSourceBO source) {
}
