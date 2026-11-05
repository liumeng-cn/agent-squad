package com.agentsquad.squad.presentation.stream;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * 可控制执行时机的订阅测试队列
 * <p>
 * 用确定性调度验证通知合并和发送积压，不依赖线程休眠。
 */
final class QueuedExecutor implements Executor {
    final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
        tasks.add(command);
    }

    void runNext() {
        tasks.removeFirst().run();
    }
}
