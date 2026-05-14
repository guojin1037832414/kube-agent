package com.atlas.intent.rule;

import com.atlas.intent.config.IntentDefinition;
import com.atlas.intent.config.IntentsLoader;
import com.atlas.intent.core.IntentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * L2 层 — 规则精确匹配 + L4 层 — 模糊兜底。
 *
 * <p><b>L2 精确匹配</b>（score == 100，直接返回）：</p>
 * <ul>
 *   <li>关键词全包含：用户 query 包含某意图全部 keywords</li>
 *   <li>正则命中：query 匹配某意图任一 pattern</li>
 * </ul>
 *
 * <p><b>L4 模糊兜底</b>（score &lt; 100，降级用）：</p>
 * <ul>
 *   <li>部分关键词匹配按比例打分</li>
 *   <li>pattern 命中给额外加分</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
@Component
public class RuleMatcher {

    private static final Logger log = LoggerFactory.getLogger(RuleMatcher.class);

    private final IntentsLoader intentsLoader;

    public RuleMatcher(IntentsLoader intentsLoader) {
        this.intentsLoader = intentsLoader;
    }

    /**
     * L2 精确匹配。
     *
     * @param query 用户原始输入
     * @return 匹配结果（score = 1.0），未命中返回 null
     */
    public IntentResult exactMatch(String query) {
        String lower = query.toLowerCase();
        IntentDefinition best = null;

        for (var def : intentsLoader.getAllIntents()) {
            // 关键词全包含
            if (allKeywordsMatch(lower, def.keywords())) {
                best = def;
                break; // 命中即返回，按intents.yml顺序优先
            }
            // 正则命中
            if (anyPatternMatch(query, def.patterns())) {
                best = def;
                break;
            }
        }

        if (best == null) return null;
        return new IntentResult(best.intentId(), best.description(), 1.0, "L2",
            best.agent(), best.level(), query);
    }

    /**
     * L4 模糊兜底匹配。
     *
     * @param query 用户原始输入
     * @return 最佳模糊匹配结果，未命中返回 null
     */
    public IntentResult fuzzyMatch(String query) {
        String lower = query.toLowerCase();
        IntentDefinition best = null;
        double bestScore = 0;

        for (var def : intentsLoader.getAllIntents()) {
            double score = fuzzyScore(lower, def);
            if (score > bestScore) {
                bestScore = score;
                best = def;
            }
        }

        if (best == null || bestScore < 0.3) return null;
        return new IntentResult(best.intentId(), best.description(), bestScore, "L4",
            best.agent(), best.level(), query);
    }

    // ==================== 私有辅助方法 ====================

    private boolean allKeywordsMatch(String lowerQuery, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return false;
        for (String kw : keywords) {
            if (!lowerQuery.contains(kw.toLowerCase())) return false;
        }
        return true;
    }

    private boolean anyPatternMatch(String query, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return false;
        for (String p : patterns) {
            try {
                if (Pattern.compile(p).matcher(query).find()) return true;
            } catch (Exception e) {
                log.warn("[RuleMatcher] 正则编译失败: {}", p);
            }
        }
        return false;
    }

    private double fuzzyScore(String lowerQuery, IntentDefinition def) {
        double score = 0;
        int totalKw = def.keywords() != null ? def.keywords().size() : 0;
        if (totalKw > 0) {
            int matched = 0;
            for (String kw : def.keywords()) {
                if (lowerQuery.contains(kw.toLowerCase())) matched++;
            }
            // 只要有命中就给基础分，避免keywords多的意图门槛过高
            if (matched > 0) {
                score += 0.25 + (double) matched / Math.min(totalKw, 4) * 0.45;
            }
        }
        // examples 增量匹配 (额外加分)
        if (def.examples() != null) {
            for (String ex : def.examples()) {
                if (lowerQuery.contains(ex.toLowerCase()) || similarToQuery(lowerQuery, ex.toLowerCase())) {
                    score += 0.15;
                    break; // 只加一次
                }
            }
        }
        if (anyPatternMatch(lowerQuery, def.patterns())) {
            score += 0.3; // pattern命中额外30%
        }
        return Math.min(score, 0.99); // L4最高<1.0
    }

    private boolean similarToQuery(String query, String example) {
        // 简单相似度：query中有example的任意两个字节长度子串，或有共同关键词
        String[] exWords = example.split("\\s+");
        for (String w : exWords) {
            if (w.length() >= 2 && query.contains(w)) return true;
        }
        return false;
    }
}
