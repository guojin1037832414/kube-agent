package com.atlas.intent.config;

import java.util.List;

/**
 * 单个意图定义数据。
 *
 * @param intentId    意图标识符
 * @param description 意图描述
 * @param agent       目标Agent
 * @param level       风险等级 p0/p1/p2/p3
 * @param keywords    关键词列表
 * @param patterns    正则列表
 * @param examples    口语化示例
 */
public record IntentDefinition(
    String intentId, String description, String agent,
    String level, List<String> keywords, List<String> patterns, List<String> examples
) {}
