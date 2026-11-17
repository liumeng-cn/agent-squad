package com.agentsquad.squad.model.bo;

/**
 * 外部连接器调用接口
 * <p>
 * 接收操作账本分配的幂等键，并返回可持久化的执行结果 JSON。
 */
@FunctionalInterface
public interface ExternalCallBO {

    String call(String externalKey);
}
