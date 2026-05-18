# Atlas v3.1 开发审计日志

## 2026-05-14 P1.4 权限感知

### 实现内容
- ToolPermission 注解新增 `roles[]` 数组
- ToolRegistry 权限感知：isVisible()/resolve()预检
- AtlasAgentBase 区分 "权限不足" vs "未实现"
- 5个危险Tool标注 ADMIN_ONLY：deploy_delete, deploy_restart, storage_delete, user_create, user_delete
- L1 Embedding 降级：代理下载失败时自动关闭L1，L2/L4保持工作

### E2E 测试结果
- ✅ 匿名用户请求 admin操作 → 被拦截（user_delete: 权限不足）
- ✅ 匿名用户请求 public操作 → 正常执行（node_query: 返回5个节点）

### 待办
- [ ] Admin Token 登录链路（P3 HITL阶段打通）
- [ ] Embedding模型从HuggingFace下载（ONNX路径404，需确认正确URL）

### 环境
- kube-agent 端口 8500
- ToolRegistry: 23 tools, 6 agents
- 权限分布: PUBLIC=18, AUTHENTICATED=*** ADMIN_ONLY=5

---

## 2026-05-18 M1.5 HITL SSE + Phase X 里程碑重对齐

### 实现内容

**M1.5 后端 — HITL SSE 流式确认完整闭环：**
1. 新建 `TimedDecisionCache.java`（222行）— Caffeine TTL 5min + 最大容量1000 + 幂等性 + 审计日志
2. 重写 `HITLController.java`（288行）— confirmAndResume + clarifyAndResume + resumeGraph + checkpoint 恢复
3. `AtlasOrchestrator.java` — 6处 `pendingDecisions` 替换为注入的 `decisionCache`；4处 emit 追加 `threadId`
4. API 路径 `/api/v1` → `/api/agent`，端口 8500 → 8300
5. 后端 E2E curl 验证：删除pod → HITL_CONFIRM (confidence=0.98) → confirm → resume Graph → CALL_TOOL → done
6. 幂等性验证：重复 confirm 返回 "会话已过期/已确认"

**M1.5 前端 — HITL 弹窗实现：**
1. `types/index.ts` — SseEventType 追加 `hitl_request`/`clarify`，接口追加 HITL 字段
2. `utils/sse.ts` — M1.5 字段透传 + threadId
3. `useChat.ts`（+199行）— `pendingHitl`/`pendingClarify` 响应式状态 + `confirmHITL()`/`clarifyHITL()` resume SSE 解析
4. `ChatView.vue`（+82行）— `watch(pendingHitl)` 命令式确认弹窗（需输入"确认执行"）+ `watch(pendingClarify)` 澄清弹窗
5. TypeScript 类型检查通过（`npx tsc --noEmit` exit code 0）

**Phase X — 文档大扫除 + 路线重规划：**
1. 专家会诊（2位实跑）：架构审计专家 + 文档治理专家，产出 `ARCHITECTURE_AUDIT_20260518.md` + `DOCUMENTATION_GOVERNANCE_REPORT.md`
2. 删除7个过时文件（TASK.md、MIGRATION_...、P1_AUDIT、废弃archive等），H盘备份
3. 归档6份报告（加 ARCHIVED_20260518 前缀）：ONNX调研、QueryAgent设计、API映射、L3分类、P2规划、开源调研
4. 新建 ROADMAP.md（M2-M5 新里程碑 + 验收标准）、CHANGELOG.md（M0/M1.5记录）、README.md（项目门面）
5. 新建 ADR-009（StateGraph迁移完成）、ADR-010（AtlasBrain替代ReactAgent Supervisor）
6. 更新 `PROJECT_ATLAS_V3.md` 状态从"🚧 P0"→"✅ M1.5已完成"
7. GitHub代理项目级配置（.git/config http.https://github.com.proxy = 127.0.0.1:10792），不污染WSL全局/Windows

### E2E 测试结果

| 测试项 | 方式 | 结果 |
|--------|------|------|
| `chat/stream` + "删除所有pod" | curl | ✅ HITL_CONFIRM confidence=0.98 |
| SSE 含 threadId + confirmToken | curl | ✅ |
| `hitl/confirm` + token + threadId | curl | ✅ Resume成功，Graph恢复执行 |
| 幂等性（重复confirm） | curl | ✅ 返回"会话已过期/已确认" |
| 后端编译 | mvn | ✅ 167 files BUILD SUCCESS |
| 后端启动 | java -jar | ✅ 9.4s，109 Tool注册 |
| 前端类型检查 | npx tsc | ✅ exit code 0 |
| 前端dev server | npm run dev | ✅ localhost:3000 |
| 浏览器HITL弹窗 | 手动 | ⚠️ **待哥哥验证** |

### 优点

1. **HITL设计安全**：命令式确认（需输入"确认执行"而非点击按钮），防止误触；幂等Token + Caffeine TTL + 审计日志，生产级安全
2. **前后端SSE对齐**：hitl_request/clarify事件格式一致，threadId透传确保Graph checkpoint可恢复
3. **文档体系重建**：ROADMAP.md作为唯一真相源，里程碑、验收标准、防脱节机制全部写入
4. **Git双推自动化**：项目级代理配置，GitHub走代理、GitLab直连，以后无需手动加 HTTPS_PROXY
5. **TypeScript零错误**：306行前端改动，类型检查全通过

### 风险与问题

1. 🔴 **前端HITL未浏览器实测**：useChat.ts里的confirmHITL()虽然写了resume SSE解析，但真实浏览器环境可能遇到CORS/SSE格式/事件顺序等问题
2. 🟡 **useChat.ts代码重复**：`chatStream()`和`confirmHITL()`里都有几乎相同的`ReadableStream`解析逻辑（buffer→split数据块→JSON.parse），应抽象为共享函数
3. 🟡 **HITL target为空**：AtlasBrain识别高危操作时`target`为空字符串，弹窗只显示"此操作"而非具体意图名，用户体验差
4. 🟡 **ThreadLocal Token透传**：专家会诊两位都指出这是隐藏炸弹，Graph异步线程池+子图嵌套场景下可能泄漏
5. 🟢 **GitHub push偶发超时**：走代理后正常，但网络不稳定时可能仍有问题

### 经验教训

1. **文档必须和代码同步**：PROJECT_STATUS_20260517.md写的进度（M0未完成）和实际代码（M1.5已完成）严重脱节，导致每次汇报都对不上。新建ROADMAP.md + CHANGELOG.md + commit message前缀规范（feat(Mx)）= 防脱节三道防线。
2. **git add .只看git status再执行**：昨天差点因为`git add .`把地面垃圾（txt.txt、检讨书等）提交进仓库。以后必须`git status`确认clean再add。
3. **专家会诊前必须做会前信息收集**：直接派delegate_task给子agent，它们只会浏览文件不搜索，等于假会诊。先自己做3500 token小实验（读核心文件、跑测试、盘文档），再给专家明确的上下文和搜索关键词。
4. **背景进程必须用terminal(background=true)**：不能用nohup/&/setsid，否则系统跟踪不到。启动后poll检查状态，确认ready再下一步。
5. **前端SSE解析的buffer处理有坑**：`split('\n\n')`后要用`pop()`保留未完成的块，否则可能丢数据。这个逻辑在chatStream和confirmHITL里都写了一遍，下次重构要抽象。

### 待办

- [ ] 🔴 **浏览器HITL弹窗实测**：打开localhost:3000 → 输入"删除所有pod" → 确认弹窗 → 输入"确认执行" → 验证resume流
- [ ] 🟡 **抽象SSE解析函数**：把chatStream和confirmHITL里的ReadableStream解析逻辑提取到`sse.ts`
- [ ] 🟡 **HITL target优化**：AtlasBrain识别高危操作时填充具体意图名到target字段
- [ ] 🟡 **ThreadLocal→State显式传递**：M3必须修的技术债务
- [ ] 🟢 **Review Log #23**：M2开始后记录测试补全过程

### 环境

- kube-agent 端口 8300（从8500改过来，对齐前端proxy）
- ToolRegistry: 109 tools, 6 agents
- 权限分布: PUBLIC=89, AUTHENTICATED=12, ADMIN_ONLY=8
- 前端: localhost:3000 (Vite devServer)
- 前端分支: dev (commit d8380b1)
- 后端分支: master (commit d7d8353)
- GitHub代理: http://127.0.0.1:10792 (项目级)
