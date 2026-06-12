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
 * <p>中文说明：PromptBuilder 把 ToolRegistry 中“当前用户可见的工具目录”、ReActMemory 中的历史
 * Observation、已调用动作和格式约束拼成系统提示词。它的目标不是让模型拥有执行权，
 * 而是把服务端允许模型看到的能力边界清楚地告诉模型。</p>
 *
 * <p>安全边界：提示词中的规则只是模型行为引导，不是最终安全控制。
 * 模型即使违反提示词输出高风险 Action、伪造 token/orgId/HITL/audit/release 字段，
 * 后续 ReActEngine、ProtectedToolParameterFilter 和 SafeToolExecutor 仍必须 fail-closed。</p>
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
        // 中文说明：可见工具列表由 ToolRegistry 根据服务端权限生成，不能由前端或 LLM 自己声明。
        // 这里把它放进 Prompt，是为了降低模型乱选工具的概率；真正执行仍以 SafeToolExecutor 为准。
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
            6. 【可用工具】中的风险标签是权威风险提示：凡 operationType=CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER，或 requiresConfirmation=true 的 Tool，禁止直接输出 Action，必须输出模式C，并说明拟执行的操作、目标对象、影响范围和需要人工确认的风险。
            7. 参数已补全、默认值回填、字段可选、用户自然语言表达“确认”，都不能替代服务端 HITL；不要在 Action.params 中主动生成 token/orgId/userId/confirmed/hitlConfirmed/approval/auditReceipt/releaseDecision/writePermitted 等认证、租户、HITL、审计、发布或写入控制字段。
            8. operationType=PLACEHOLDER 或 httpMethod=NONE 表示该 Tool 当前未开放真实后端执行链路；不得声称已经创建/删除/提交/变更成功，也不得把它包装成真实 HTTP 调用。
            9. 关键词类高危表达（delete/删除/scale/扩缩容/权限变更/创建/提交/启停/充值）即使未完全命中风险标签，也要按高危意图谨慎处理；若对应 Tool 的风险标签要求确认，应输出模式C。
            10. 保持推理过程简洁，Thought 不要超过 300 字。
            11. 所有输出必须是中文（专业术语可保留英文）。

            【Pod 诊断工具调用规则】
            1. 默认先调用 pod_status 获取 Pod 是否存在、phase、Ready、restartCount、container state 等基础状态。
            2. 当 Pod 为 Pending、ImagePullBackOff、ErrImagePull、ContainerCreating、CreateContainerConfigError、CreateContainerError、FailedMount、Unschedulable 或 FailedScheduling 时，优先调用 event_query 获取 Pod Warning/异常事件摘要，不要优先调用 log_query。
            3. 当 Pod 为 CrashLoopBackOff、RestartCount>0、Running 但 Ready=false、Terminated Error 或 OOMKilled 时，调用 event_query 后必须结合 log_query，从控制面事件和容器内日志两类证据综合判断。
            4. 当用户明确要求查看日志时，可以调用 log_query；但如果日志为空、Pod 未启动或处于 Pending/镜像拉取/调度失败阶段，必须回退 event_query 查事件摘要。
            5. 当前 event_query 只是基于 kube-manager Pod warning 字段的 Pod Warning/异常事件摘要工具，不是完整 Kubernetes EventList；不得声称“无任何 Kubernetes 事件”，也不得构造 fieldSelector、labelSelector、since、type、involvedObjectKind 等不支持参数。
            6. Final Answer 必须用「现象、证据、判断、建议」组织诊断结论，避免只凭单一工具输出下绝对结论。

            【GPU 实例创建工具调用规则】
            1. 当用户要创建带 GPU 的 Deployment/实例时，不能凭自然语言猜测 GPU 型号、MIG 配置或 gpuSpec。
            2. 如果用户已经明确要求使用 GPU，但没有给出来自组织级 GPU map 的 gpuSpec，应先调用 gpu_query 查询当前组织可用 GPU map。
            3. gpu_query 返回后，优先使用返回 map 的 key 作为 deploy_create_instance 的 gpuSpec，例如 A100 或 A100#all-2g.10gb；不要主动拼造不存在的 key。
            4. 如果 gpu_query 中同一 gpuModel 存在多个 MIG/整卡候选，必须在 Final Answer 中请用户选择明确 gpuSpec，不要直接调用 deploy_create_instance。
            5. deploy_create_instance 是 CREATE 操作，创建前仍需遵守高危/HITL 规则；缺少 name、image、gpuSpec 等关键参数时先澄清，不要提交含糊请求。

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
