package com.agentsquad.squad.exceptions;

/**
 * 原生运行容量暂时不足。
 * <p>
 * 仅用于持久命令领取事务的回滚信号，投递器接收后等待下轮扫描；
 * 不把已经持久受理的用户命令标记为失败或创建新的命令。
 */
public class RuntimeAdmissionBusyException extends RuntimeException {

    public RuntimeAdmissionBusyException() {
        super("运行容量已满，等待后续调度");
    }
}
