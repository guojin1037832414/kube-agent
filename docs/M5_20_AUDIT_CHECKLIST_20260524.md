# kube-agent M5.20 审计清单

> 生成时间: 2026-05-24 19:31 +0800  
> 审计人: Hermes  
> 审计范围: M5.18~M5.20 Tool 风险治理 + MCP/Memory/Observability 最小闭环

## 一、工程目录审计

### 1. MCP 安全 Manifest
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/main/java/com/atlas/mcp/McpToolManifestService.java` | M5.20 | ✅ PASS | 只导出普通 READ + endpoint 已声明 + 无需确认 Tool；高风险默认阻断。 |
| `src/main/java/com/atlas/mcp/McpManifestController.java` | M5.20 | ✅ PASS | 提供 `/api/agent/mcp/manifest` 清单 API，不暴露真实后端 endpoint。 |
| `src/test/java/com/atlas/mcp/M520McpManifestSafetyContractTest.java` | M5.20 | ✅ PASS | 验证普通 READ 可导出，SENSITIVE_READ/DELETE 不可导出。 |

### 2. Memory 摘要闭环
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/main/java/com/atlas/memory/ConversationSummaryMemoryStore.java` | M5.20 | ✅ PASS | 最近 10 次摘要内存存储，自动脱敏。 |
| `src/main/java/com/atlas/memory/MemoryController.java` | M5.20 | ✅ PASS | 提供摘要写入与查询 API。 |
| `src/test/java/com/atlas/memory/ConversationSummaryMemoryStoreTest.java` | M5.20 | ✅ PASS | 验证只保留最近 10 条且凭证字段脱敏。 |

### 3. Observability 指标闭环
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/main/java/com/atlas/observability/AgentMetricsService.java` | M5.20 | ✅ PASS | ReAct run、Tool call、HITL block 计数/计时。 |
| `src/main/java/com/atlas/observability/AgentMetricsController.java` | M5.20 | ✅ PASS | 提供 `/api/agent/metrics/snapshot` 轻量指标快照。 |
| `src/test/java/com/atlas/observability/AgentMetricsServiceTest.java` | M5.20 | ✅ PASS | 验证 Micrometer counter/timer 注册与递增。 |
| `src/main/resources/application.yml` | M5.20 | ✅ PASS | Actuator 暴露 health/info/metrics/prometheus。 |

### 4. 风险元数据与 HITL
| 文件/范围 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `AtlasToolMapping.java` | M5.18 | ✅ PASS | 新增 `SENSITIVE_READ`，GET 与风险语义分离。 |
| `HitlGuard.java` | M5.18 | ✅ PASS | 普通 READ 外全部 fail-closed。 |
| `src/main/java/com/atlas/tool/impl/*Tool.java` | M5.18/M5.19 | ✅ PASS | 敏感 GET 与 mutation/action Tool 元数据已补齐。 |
| `M511AtlasToolHttpContractTest.java` | M5.18/M5.19 | ✅ PASS | 敏感 READ 与高风险 endpoint 白名单契约。 |

## 二、功能验证

| 功能 | 测试方式 | 结果 |
|------|----------|------|
| MCP Manifest 安全导出 | `M520McpManifestSafetyContractTest` | ✅ PASS |
| Memory 最近摘要 + 脱敏 | `ConversationSummaryMemoryStoreTest` | ✅ PASS |
| Micrometer Agent 指标 | `AgentMetricsServiceTest` | ✅ PASS |
| 高风险 Tool HTTP 契约 | `M511AtlasToolHttpContractTest` | ✅ PASS |
| HITL fail-closed 回归 | `M513HitlFailClosedContractTest` | ✅ PASS |
| 全量回归 | `mvn -q test` | ✅ PASS |

## 三、覆盖率审计

| 指标 | 当前值 | 说明 |
|------|--------|------|
| Tool 总数 | 110 | `src/main/java/com/atlas/tool/impl` 统计口径 |
| 已声明 HTTP 元数据 | 81 | M5.17 后 58，M5.18/M5.19 后 81 |
| 普通 READ 免确认 | 53 | 可进入 MCP Manifest 候选，但仍受权限过滤 |
| SENSITIVE_READ 且确认 | 7 | 敏感读不进入普通 READ 免确认面 |
| mutation/action 且确认 | 20 | CREATE/DELETE/ACTION 等高风险操作需要确认 |
| 真实 mutation 未声明 | 0 | POST/DELETE/PUT/PATCH 源码扫描口径 |
| 高风险未确认 | 0 | 非 READ 已声明 Tool 均需确认 |

## 四、缺口分析

| 缺口/风险 | 优先级 | 影响 | 建议 |
|-----------|--------|------|------|
| MCP 仍不是完整 stdio/sse 可执行 Server | MED | 外部 Agent 暂不能直接执行 Tool | 等 HITL、权限上下文、审计、限流全部接线后再开放。 |
| Memory 为内存型 | MED | 重启后摘要丢失 | M6 接 Redis/Chroma/向量检索。 |
| TraceId/Token/SSE 指标未全量覆盖 | MED | 深度诊断仍需日志辅助 | M6 增加 traceId、LLM token 成本、SSE 连接数。 |
| 仍有 29 个 Tool 未声明 HTTP 元数据 | MED | 继续 UNKNOWN fail-closed，不影响安全但影响体验 | 后续按批继续治理，不因覆盖率牺牲安全。 |

## 五、审计结论

✅ **PASS**：M5.20 最小闭环完成。安全边界优先于功能开放：MCP 只提供安全 Manifest，不开放高风险执行；Memory 自动脱敏；Observability 指标可用；全量测试通过。

## 六、下一步建议

1. 提交并双推 M5.18~M5.20。
2. M6 再做 Redis/Chroma 长期记忆、完整 MCP stdio/sse Server、TraceId/Token/SSE 指标。
3. 剩余 UNKNOWN Tool 继续分批补 metadata，但默认 fail-closed 不阻塞 M5 完成。
