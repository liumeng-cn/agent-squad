package com.agentsquad.squad.tools.operation;

/**
 * 外部连接器调用边界。
 * <p>
 * 接收操作账本分配的幂等键，并返回可持久化的执行结果 JSON。
 */
@FunctionalInterface
public interface ExternalCall {

    String call(String externalKey);
}
