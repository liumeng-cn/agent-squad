package com.agentsquad.squad.v2.agent.tracing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次模型调用的流式追踪状态
 * <p>
 * 每次订阅独立创建，避免不同会话的模型输出相互混入。
 */
class LangfuseModelTraceState {
    final StringBuilder text = new StringBuilder();
    final List<Map<String, Object>> toolCalls = new ArrayList<>();
    final Map<String, Integer> argumentSizes = new LinkedHashMap<>();
    final AtomicBoolean thinkingActive = new AtomicBoolean();
    final AtomicBoolean firstDelta = new AtomicBoolean();
    final AtomicInteger reasoningChars = new AtomicInteger();
}
