package com.atlas.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

/**
 * 直接回答节点 — 当 Supervisor 判定无需 Tool 调用时，直接由 LLM 生成回复。
 */
public class DirectAnswerNode implements NodeAction {

    private final ChatClient chatClient;

    public DirectAnswerNode(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String userQuery = state.value("input")
                .map(v -> (String) v)
                .orElse("");

        String answer = chatClient.prompt()
                .system("你是 Atlas K8s 助手。用户的问题不需要调用工具，请直接回答。")
                .user(userQuery)
                .call()
                .content();

        return Map.of("final_answer", answer);
    }
}
