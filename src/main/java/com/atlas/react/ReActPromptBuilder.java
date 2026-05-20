package com.atlas.react;

import com.atlas.tool.core.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * ReAct 系统提示词构建器。
 *
 * <p>负责为每一轮 ReAct 循环生成中文系统提示词，注入：
 * <ol>
 *   <li>角色定义（Kubernetes 运维专家）</li>
 *   <li>可用工具列表（通过 {@link ToolRegistry} 按权限过滤）</li>
 *   <li>历史 Observation（记忆回溯）</li>
 *   <li>已访问动作列表（防止重复调用）</li>
 *   <li>输出格式约束与高危操作规则</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0-M3.2
 */
@Component
public class ReActPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(ReActPromptBuilder.class);

    private final ToolRegistry toolRegistry;

    public ReActPromptBuilder(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 构建当前轮次的系统提示词。
     *
     * @param userQuery 用户原始查询
     * @param memory    当前 ReAct 记忆体（含历史步骤）
     * @return 完整系统提示词文本
     */
    public String buildSystemPrompt(String userQuery, ReActMemory memory) {
        // 1. 获取当前用户可见工具的系统提示词片段
        String visibleToolsPrompt = toolRegistry.buildSystemPromptForCurrentUser();

        // 2. 获取已调用动作列表（用于规则提示）
        Set<String> visited = memory.visitedActionKeys();
        String visitedText = visited.isEmpty()
            ? "（暂无已调用动作）"
            : visited.stream().collect(Collectors.joining("\n  - ", "  - ", ""));

        // 3. 构建历史 Observation 文本（最多 3000 字符）
        String historyText = memory.toObservationHistory(3000);

        // 4. 组装完整提示词
        String prompt = """
            你是 Atlas Kubernetes 运维专家（ReAct 推理模式）。你的职责是通过多轮 Thought → Action → Observation 循环解决用户问题。

            【用户查询】
            %s

            %s

            【历史执行记录】
            %s

            【已调用动作（禁止重复）】
            %s

            【输出格式要求】
            你必须严格按以下三种模式之一输出，每轮只输出一种模式：

            模式A — 继续推理：
            Thought: <你的逐步推理过程>
            Action: {"tool":"工具名","params":{...}}

            模式B — 最终回答：
            Thought: <简要总结>
            Final Answer: <完整的中文回答，面向用户>

            模式C — 需要人工确认（HITL）：
            Thought: <高危操作识别说明>
            Final Answer: 检测到高危操作，需要人工确认后才能执行。请说明具体操作及风险。

            【规则】
            1. 每轮最多调用一个工具（Action 中只能出现一个 tool）。
            2. 只能调用上述【可用工具】列表中的工具。
            3. Action.params 必须优先使用工具目录「参数契约」中的 canonical 参数名（例如 podName、namespace）；不要主动输出 pod_name、pod、name、ns 等 alias 字段。历史 alias 仅用于系统兼容归一化，不作为推荐格式。
            4. 如果【已调用动作】中已包含相同的 tool + params 组合，则绝不允许再次 Action，必须输出 Final Answer（即使信息不完整也应基于已有 Observation 作答）。
            5. Observation 中若带有「截断/不完整」标记，请不要下绝对结论，请说明数据可能被截断。
            6. 高危操作（delete/删除/scale/扩缩容/权限变更）禁止直接输出 Action，应输出模式C（Final Answer 要求 HITL）。
            7. 保持推理过程简洁，Thought 不要超过 300 字。
            8. 所有输出必须是中文（专业术语可保留英文）。

            【注意】
            - 不要输出 markdown 代码块包裹。
            - Action 必须是合法的单行 JSON，key 使用双引号。
            - 如果当前 Observation 已足够回答问题，请直接输出 Final Answer。
            """.formatted(
                userQuery,
                visibleToolsPrompt,
                historyText,
                visitedText
            );

        log.debug("[ReActPromptBuilder] Built system prompt, visitedActions={}, steps={}",
            visited.size(), memory.steps().size());
        return prompt;
    }
}
