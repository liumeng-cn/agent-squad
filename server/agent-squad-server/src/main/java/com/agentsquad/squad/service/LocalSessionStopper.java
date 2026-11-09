package com.agentsquad.squad.service;

import com.agentsquad.squad.bo.LocalSessionHandleBO;

import com.agentsquad.squad.mapper.BindingMapper;
import com.agentsquad.squad.enums.LocalSessionState;
import io.agentscope.harness.agent.session.AgentSession;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 在实际执行节点关闭已取消的原生会话
 * <p>
 * SDK 中断属于合作停止；模型网络阻塞时由公开 close 接口等待终止并强制取消本机运行。
 * 只处理实际启动的本地会话，不创建 Worker、不启动调度、不改写业务终态或未知操作。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class LocalSessionStopper {
    private final ConcurrentHashMap<String, LocalSessionHandleBO> sessions = new ConcurrentHashMap<>();
    private final Set<String> closing = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor closer = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
        var thread = new Thread(runnable, "local-session-stop");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private BindingMapper bindings;

    /**
     * 仅由命令执行入口登记，读取日志和控制连接不登记、不恢复执行。
     */
    public AgentSession register(String userId, String bindingId, AgentSession session) {
        var handle = new LocalSessionHandleBO(userId, bindingId, session);
        sessions.put(handle.key(), handle);
        return session;
    }

    @Scheduled(fixedDelayString = "${agentsquad.cancel.local-check-delay-ms:500}")
    public void stopRequested() {
        for (var handle : sessions.values()) {
            if (closing.contains(handle.key())) {
                continue;
            }
            LocalSessionState state = bindings.localSessionState(handle.userId(), handle.bindingId());
            if (state == null || state == LocalSessionState.FINISHED) {
                sessions.remove(handle.key(), handle);
            } else if (state == LocalSessionState.STOP_REQUESTED && closing.add(handle.key())) {
                try {
                    closer.execute(() -> close(handle));
                } catch (RejectedExecutionException busy) {
                    closing.remove(handle.key());
                }
            }
        }
    }

    /**
     * close 由 SDK 先合作中断，等待最多约十秒后取消阻塞运行；数据库终态由独立核对裁决。
     */
    private void close(LocalSessionHandleBO handle) {
        try {
            handle.session().close();
            sessions.remove(handle.key(), handle);
            log.info("本节点原生会话已关闭 bindingId={}", handle.bindingId());
        } catch (RuntimeException failure) {
            log.warn("本节点原生会话关闭暂未完成 bindingId={} errorType={}", handle.bindingId(), failure.getClass().getSimpleName(), failure);
        } finally {
            closing.remove(handle.key());
        }
    }

    @PreDestroy
    public void shutdown() {
        closer.shutdownNow();
        sessions.clear();
    }
}
