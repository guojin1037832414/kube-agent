package com.atlas.intent.config;

import java.util.List;

/**
 * 单个意图定义数据。
 *
 * <p>中文说明：该 record 来自 {@code intents.yml}，是 L1/L2/L3/L4 意图识别共同读取的配置事实。
 * 它描述“用户这句话可能属于哪个业务意图、交给哪个 Agent 能力域、风险优先级如何”，输出给
 * IntentRouter、EmbeddingMatcher、RuleMatcher、L3IntentClassifier 和仲裁器使用。</p>
 *
 * <p>安全边界：意图定义不是 Tool 权限表，也不是 kube-manager API 白名单。{@code agent} 和
 * {@code level} 只帮助路由和风险排序，不能绕过 SafeToolExecutor、ToolPermission、HITL、
 * durable audit、MCP admin-only 边界或 release gate。NIM/HPC/Slurm/BCM 即使存在历史意图配置，
 * 一期也不得因此恢复二期运行时权力。</p>
 *
 * @param intentId    意图标识符，用于关联 Tool/编排分支；它不是用户输入中的可信命令。
 * @param description 意图描述，主要给日志、提示词和学习文档展示。
 * @param agent       目标 Agent 能力域；只是路由候选，不是权限主体。
 * @param level       风险等级 p0/p1/p2/p3；用于排序和审查，不等于写操作放行。
 * @param keywords    关键词列表，来自配置文件；命中后仍需安全执行边界。
 * @param patterns    正则列表，来自配置文件；不能匹配 token/orgId 等受保护控制面字段。
 * @param examples    口语化示例，用于 Embedding/LLM 学习提示；不能包含密钥或内部敏感端点。
 */
public record IntentDefinition(
    String intentId, String description, String agent,
    String level, List<String> keywords, List<String> patterns, List<String> examples
) {}
