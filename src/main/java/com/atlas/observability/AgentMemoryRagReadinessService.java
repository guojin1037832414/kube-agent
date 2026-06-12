package com.atlas.observability;

import com.atlas.memory.ConversationSummaryMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds a Memory/RAG readiness contract without running retrieval.
 *
 * <p>中文说明：该服务只读取当前摘要记忆 store 的有限统计事实，并组合未来 RAG
 * 上线所需的安全契约。它不写 memory、不查向量库、不调用 embedding/reranker/LLM。</p>
 *
 * <p>安全边界：readiness 是 admin-only 只读证据，不是 prompt 权威，也不是检索运行时开关。
 * caller-submitted summary 在未经过 source custody、reviewed trace 和 eval gate 前，不能进入自动 RAG。</p>
 */
@Service
public class AgentMemoryRagReadinessService {

    private final ConversationSummaryMemoryStore memoryStore;
    private final Clock clock;

    @Autowired
    public AgentMemoryRagReadinessService(ConversationSummaryMemoryStore memoryStore) {
        this(memoryStore, Clock.systemUTC());
    }

    AgentMemoryRagReadinessService(ConversationSummaryMemoryStore memoryStore, Clock clock) {
        this.memoryStore = memoryStore;
        this.clock = clock;
    }

    public AgentMemoryRagReadinessResponse readiness() {
        return AgentMemoryRagReadinessResponse.of(
            Instant.now(clock),
            memoryStore.userCount(),
            ConversationSummaryMemoryStore.MAX_SUMMARIES_PER_USER
        );
    }
}
