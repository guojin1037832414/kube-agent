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
- 权限分布: PUBLIC=18, AUTHENTICATED=0, ADMIN_ONLY=5
