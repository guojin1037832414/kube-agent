# Atlas v3.1 意图评分统一体系设计方案

> 作者：Atlas Team
> 版本：v3.1.0-unified
> 状态：设计稿，待评审后落地

---

## 1. 当前问题诊断

| 层级 | Matcher | 原始 Score 范围 | 当前阈值 | 问题 |
|------|---------|----------------|----------|------|
| L1 | EmbeddingMatcher | cosine sim `[0, 1]` 浮点 | ≥ 0.75 命中 | 余弦相似度分布不均匀，0.75 可能只是“及格”，0.95 才是高置信，但直接和 L2 的 1.0 比较不公平 |
| L2 | RuleMatcher.exact | 固定 `1.0` | 命中即返回 | 精确匹配是业务规则最高优先级，但其 `1.0` 为“规则满分”标记，非概率意义，会压死 L1 的 0.95 |
| L3 | LLMClassifier | 预留 | — | 未来 LLM 可能返回 `[0,1]` 概率或百分比，需要统一入口 |
| L4 | RuleMatcher.fuzzy | `[0, 0.99]` 浮点 | ≥ 0.3 命中 | 封顶 0.99，但和 L1 的 0.95 仍同尺度，难以区分层级权威性 |

核心矛盾：
1. **尺度异构**：L1 是连续概率型，L2 Exact 是离散规则型，L4 是混合型。
2. **维度单一**：只用 `confidence` 一个字段，无法区分“原始分 / 校准分 / 层级权重”。
3. **缺乏仲裁**：当 L1→IntentA (0.95) 与 L2→IntentB (1.0) 冲突时，当前代码直接返回 L2，无仲裁逻辑。

---

## 2. 业界调研结论

### 2.1 Rasa NLU Confidence
- Rasa 的分类器输出**归一化概率** `[0, 1]`（softmax over intent logits）。
- **Fallback 策略**：`confidence < 0.7`（可配置）→ fallback；`0.7 ~ 0.9` 之间若 Top1 与 Top2 差距 `< 0.1`，触发 `Ambiguous Intent`。
- **关键启示**：不仅看绝对阈值，还要看**Top1-Top2 区分度（margin）**和**Platt Scaling 校准**。

### 2.2 LangChain Router
- LangChain 的 `RouterChain` 通常将 Embedding Similarity 或 LLM 分类结果映射到 `[0, 1]`。
- 采用 **Destination + Score** 双字段，Score 仅用于排序，最终路由由 `default_destination` 兜底。
- **关键启示**：分层路由中，各层分数应经过**层内校准**后再参与最终决策。

### 2.3 LLM 置信度校准
- LLM 直接输出的“confidence”往往是**未校准的（miscalibrated）**，即 0.9 不代表 90% 准确率。
- 常用校准方法：
  - **Temperature Scaling**：$q_i = \frac{\exp(z_i / T)}{\sum_j \exp(z_j / T)}$，在 dev set 上优化 $T$。
  - **Platt Scaling**：用逻辑回归拟合 $P(y=1|s) = \frac{1}{1 + e^{As + B}}$。
  - **Self-Consistency / Temperature Sampling**：多次采样取投票率作为校准概率。
- **关键启示**：L3 落地时必须经过一个 `LlmCalibrator`，不要直接相信原始置信度。

---

## 3. 统一评分体系设计

### 3.1 设计原则

1. **所有层级最终输出统一的 `normalizedScore`（校准分）∈ `[0, 1]`**。
2. **保留 `rawScore` 用于调试、监控和 A/B 测试**。
3. **L2 规则（Exact）优先级高于 L1 语义，但不绝对**：通过仲裁器处理冲突，而非硬编码顺序。
4. **引入“层权重因子”** $w_{layer}$，在最终报告中体现不同层级的权威性，但**决策以 `normalizedScore` 为准**。

### 3.2 归一化公式

定义统一归一化函数 $\mathcal{N}(raw, layer)$：

#### L1 — Embedding 语义层
余弦相似度 $sim \in [0, 1]$，其分布特点是：低于 0.70 大多为噪声，0.80 以上才进入可信区间。采用 **Sigmoid 拉伸**进行校准：

$$
s_{L1} = \frac{1}{1 + e^{-10 \cdot (sim - 0.82)}}
$$

校准对照表：

| raw sim | 0.55 | 0.65 | 0.72 | 0.75 | 0.80 | 0.85 | 0.90 | 0.93 | 0.95 | 0.98 | 1.00 |
|---------|------|------|------|------|------|------|------|------|------|------|------|
| $s_{L1}$ | 0.03 | 0.09 | 0.18 | 0.25 | 0.45 | 0.73 | 0.90 | 0.96 | 0.98 | 1.00 | 1.00 |

> 说明：sigmoid 将中段（0.75~0.85）快速拉开，使得 L1 的 0.95 经校准后达到 0.98，与 L2 Exact 的 0.98 同级，从而具备仲裁条件。

#### L2 Exact — 规则精确匹配
精确匹配代表业务确定性意图，但不应为绝对 `1.0`（语义上仍可能存在歧义）。

$$
s_{L2}^{exact} = 0.98
$$

#### L3 — LLM 语义分类
LLM 原始输出（概率 / logit）先经过 **Temperature Scaling** 校准：

$$
s_{L3} = \text{softmax}\left(\frac{\mathbf{z}}{T}\right)_{best}
$$

其中 $T$ 在开发集上通过 NLL 损失最小化求得（典型值 $T \in [1.2, 2.5]$，若 LLM 过自信则 $T > 1$）。

若 LLM 返回的是百分比字符串（如 "95%"），则直接除以 100 再乘校准系数 $\alpha=0.95$：

$$
s_{L3} = \frac{p_{pct}}{100} \times 0.95
$$

#### L4 — 模糊兜底
RuleMatcher.fuzzyScore 已经在 `[0, 0.99]`，代表关键词覆盖率 + pattern 加分。作为兜底层级，封顶应低于 L1/L2/L3：

$$
s_{L4} = raw_{fuzzy} \times 0.75
$$

模糊层最高分封顶 **0.75**，确保不会越过 L2 Exact 和 L1 高置信度结果。

---

### 3.3 阈值边界表（路由决策）

在 `IntentRouter` 中，各层根据自身 `normalizedScore` 决定是直接命中、交叉验证还是下放下一层。

| 层级 | normalizedScore 区间 | 原始参考值 | 路由决策 | 说明 |
|------|---------------------|-----------|---------|------|
| **L1** | $[0.90, 1.00]$ | sim ≥ 0.93 | **直接命中 (Direct)** | 语义极高置信，直接返回，无需下传 |
| **L1** | $[0.70, 0.90)$ | sim ∈ [0.84, 0.93) | **交叉验证 (Cross-Check)** | 继续 L2；若 L2 命中同 intent，score 提升 3%；若 L2 命中不同 intent，进入仲裁 |
| **L1** | $[0.40, 0.70)$ | sim ∈ [0.76, 0.84) | **弱命中 (Weak)** | 继续 L2；L2 未命中则保留 L1，但标记 `needsConfirm` |
| **L1** | $[0, 0.40)$ | sim < 0.76 | **未命中 (Miss)** | 视为 L1 无结果，进入 L2 |
| **L2 Exact** | $0.98$ | 命中即 0.98 | **规则命中** | 若与 L1 冲突，进入 **仲裁器**（见 §4） |
| **L3** | $[0.85, 1.00]$ | — | **LLM 直接命中** | 跳过 L4 |
| **L3** | $[0.60, 0.85)$ | — | **LLM 建议** | 进入 L4 兜底验证 |
| **L3** | $< 0.60$ | — | **LLM 未知** | 进入 L4 |
| **L4** | $[0.55, 0.75]$ | raw ≥ 0.73 | **模糊命中** | 返回最佳模糊结果，标记 `fuzzy=true` |
| **L4** | $< 0.55$ | raw < 0.73 | **Unknown** | 返回 `unknown`，触发通用兜底 Agent |

---

### 3.4 层权重因子（用于监控 & 报告）

虽然归一化后分数已经可比，但在最终报告中可附一个**层级权威性权重** $w_{layer}$，用于构成可解释的 `finalScore`：

$$
finalScore_{report} = s_{norm} \times w_{layer}
$$

| 层级 | $w_{layer}$ | 封顶分数 | 含义 |
|------|------------|---------|------|
| L1 | 0.95 | 0.95 | 语义推断，非绝对 |
| L2 Exact | 1.00 | 0.98 | 业务规则，最高权威 |
| L3 | 0.97 | 0.97 | LLM 推理，权威但需校准 |
| L4 | 0.75 | 0.75 | 模糊兜底，最低权威 |

> 注：`finalScore_report` 仅用于日志、监控面板和 A/B 测试，**不用于路由决策**。路由决策以 `s_norm` 为准。

---

## 4. 冲突仲裁设计

### 4.1 冲突定义
当两个不同层返回了 **不同的 `intentId`** 且各自的 `normalizedScore` 均高于阈值时，视为冲突。典型场景：
- **L1→`deploy_pod` ($s=0.92$)** vs **L2→`scale_app` ($s=0.98$)**
- **L1→`delete_ns` ($s=0.95$)** vs **L3→`restart_app` ($s=0.88$)**

### 4.2 仲裁器算法 (IntentArbiter)

```
arbitrate(List<IntentResult> results) -> IntentResult:
    1. 同 intentId 合并:
       取最高 normalizedScore，并追加 crossBoost = +0.03
       （多层交叉确认，提升置信度）

    2. 筛选候选集:
       candidates = results where r.normalizedScore >= 0.70
       if candidates.size == 1: return candidates[0]

    3. 取 Top2 候选: A (score=sA), B (score=sB), sA >= sB

    4. 规则链执行（按优先级）：

       Rule A — 同层决胜:
         if A.matchedLevel == B.matchedLevel:
            return A  // 同层取分数高者

       Rule B — Exact 规则护城河:
         if A.matchedLevel == "L2" and sA >= 0.95:
            if sB < 0.93 or B.matchedLevel != "L2":
               return A  // L2 Exact 在对方非极高置信时胜出

       Rule C — 极高语义压倒规则:
         if B.matchedLevel == "L2" and sB >= 0.95:
            if sA >= 0.96 and (A.level in {p0, p1}):
               return A  // L1/L3 达到 0.96 且意图为 p0/p1 高优，可推翻 L2

       Rule D — 意图优先级兜底:
         优先级：p0 > p1 > p2 > p3
         if A.level 优先级 > B.level 优先级:
            margin = sA - sB
            if margin >= -0.05:  // 允许高优意图分数略低（最多低 0.05）
               return A

       Rule E — 分数差距裁决:
         if (sA - sB) >= 0.15:
            return A  // 显著差距，高分胜

       Rule F — 模糊区 fallback:
         // 分数接近 (<0.15)、层级不同、intent 不同、level 同级
         layerPriority = [L2, L3, L1, L4]
         return priorityWinner(A, B, layerPriority)
```

### 4.3 仲裁规则速查表

| 场景 | L1 (s) | L2 (s) | 差距 | 仲裁结果 | 原因 |
|------|--------|--------|------|---------|------|
| L1 极高语义 vs L2 Exact | 0.96 | 0.98 | 0.02 | **L2 胜** | 差距 < 0.15，L2 规则护城河 |
| L1 极高语义 + p0 vs L2 Exact | 0.97 | 0.98 | 0.01 | **L1 胜** | Rule C：L1≥0.96 且 p0/p1 高优意图 |
| L1 中高语义 vs L2 Exact | 0.85 | 0.98 | 0.13 | **L2 胜** | L1 < 0.93，无法破护城河 |
| L1 高语义 vs L3 中高 | 0.92 | 0.85 (L3) | 0.07 | **L1 胜** | 同层优先不存在，L1/L3 比较，L1 分数高 |
| L1 与 L3 同 intent | 0.88 | 0.90 (L3) | — | **合并→0.93** | 同 intent 交叉确认，+3% boost |
| L1 vs L2 同 intent | 0.85 | 0.98 | — | **合并→1.0** | 同 intent，取 max + 0.03 boost = 1.0 |

---

## 5. 新增 & 修改代码清单

### 5.1 新增 `ScoreNormalizer.java`（统一归一化器）

路径：`src/main/java/com/atlas/intent/core/ScoreNormalizer.java`

职责：为 L1~L4 提供单一入口的归一化函数，所有 Matcher 不再自行解释 score 含义。

### 5.2 新增 `IntentArbiter.java`（冲突仲裁器）

路径：`src/main/java/com/atlas/intent/core/IntentArbiter.java`

职责：收集多层结果，执行 §4.2 的仲裁规则链，输出唯一最佳结果。

### 5.3 修改 `IntentResult.java`

- 增加 `double normalizedScore`（校准后统一分）。
- 保留向后兼容的构造函数：`confidence` 字段同时承担 `normalizedScore` 的角色。

### 5.4 修改 `IntentRouter.java`

- 引入 `ScoreNormalizer` 对每层结果做实时校准。
- 将串联逻辑改为 **“执行→归一化→决策/仲裁”** 模式。
- L1 中低置信度不再无条件回退，而是参与仲裁。

### 5.5 可选：修改 `RuleMatcher.java`

- `fuzzyScore` 返回的 raw score 不再需要做 min(0.99)，归一化由 `ScoreNormalizer` 统一处理。
- `exactMatch` 仍返回 `rawScore = 1.0`，归一化后映射为 `0.98`。

---

## 6. 向后兼容 & 灰度策略

1. **双写监控**：上线后 1 周内，新旧两套 score 同时计算，日志输出 `rawScore / oldConfidence / normalizedScore` 三元组。
2. **阈值 A/B**：`atlas.intent.use-normalized-score=false`（默认 true，可回滚）。
3. **L3 占位**：当前 L3 未实现，`IntentRouter` 中预留 `llmMatcher` 注入点，返回 `Optional.empty()` 时不影响流程。

---

## 附录：数学符号速查

| 符号 | 含义 |
|------|------|
| $sim$ | L1 原始余弦相似度 |
| $s_{L1}, s_{L2}, s_{L3}, s_{L4}$ | 各层归一化后分数 |
| $s_{norm}$ | 统一归一化分数 |
| $T$ | LLM Temperature Scaling 温度参数 |
| $\mathbf{z}$ | LLM 输出 logits 向量 |
| $w_{layer}$ | 层级报告权重 |
| $finalScore_{report}$ | 最终报告分（仅展示） |
| $\Delta = s_A - s_B$ | Top2 候选分数差（margin） |
| $crossBoost$ | 多层级交叉确认加分（+0.03） |
