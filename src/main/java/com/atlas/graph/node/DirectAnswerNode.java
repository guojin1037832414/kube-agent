package com.atlas.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

/**
 * 直接回答节点 — 当 Supervisor 判定无需 Tool 调用时，直接由 LLM 生成回复。
 *
 * <p>中文说明：direct_answer 适合解释性、闲聊式或无需集群状态的回答。它让 Graph 在不触碰
 * kube-manager 和 Tool 的情况下完成一次对话。</p>
 *
 * <p>安全边界：本节点可以调用 LLM 生成文本，但不能声称已经查询或修改真实集群状态；
 * 如果问题需要实时资源、权限、审计或写入证据，必须路由到 Tool/ReAct/Plan/HITL 等受控路径。</p>
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
