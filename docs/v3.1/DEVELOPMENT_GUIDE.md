# Atlas v3.1 开发指南

## 快速开始

### 1. 环境要求
- Java 17+ (推荐 GraalVM)
- Maven 3.8+
- WSL (Windows Subsystem for Linux)

### 2. 首次启动

```bash
# 进入项目目录
cd ~/kube-agent

# 编译
mvn clean compile

# 启动 (开发模式)
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.ai.openai.api-key=YOUR_KEY"

# 或使用打包方式 (推荐，避免WSL僵尸进程)
mvn clean package -DskipTests
java -jar target/kube-agent-3.1.0-SNAPSHOT.jar \
  --spring.ai.openai.api-key=YOUR_KEY
```

### 3. 本地Embedding模型首次下载

首次启动时会自动从 HuggingFace 下载模型到 `~/.atlas/models/all-MiniLM/`：
```
~/.atlas/models/all-MiniLM/
├── model.onnx           # ONNX模型文件 (~90MB)
└── tokenizer.json       # 分词器配置
```

如果网络受限，可手动下载后放入该目录。

### 4. API密钥配置

```bash
# 方式1: 环境变量 (推荐)
export ATLAS_LLM_API_KEY="sk-T5BnkBXiizu15sO3OSq8csiVEFL0Oypjcgiw1lWx21aZBGhw"

# 方式2: 启动参数
--spring.ai.openai.api-key=sk-...

# 方式3: ~/.hermes/secure_config.yml (如果你用Hermes管理)
```

### 5. 开发规范

- 所有新增类必须有 **中文注释**
- 提交前必须跑通 `mvn test`
- 修改记录到 `docs/REVIEW_LOG.md`
- 遵循 **专家会诊 → 编码 → Review → 测试 → 记录 → GitLab+GitHub 双推** 流程
- 文档更新门控：新增/修改 Tool、API、Config、Prompt → 必须同步更新契约文档

---

## 模块开发顺序 (Milestone)

```
M0: 地基 — v2.x 基线、23 Plugin、ChatMemory、SSE 流式
M1: 智能引擎 — L1-L4 意图路由、AtlasBrain 决策、StateGraph 编排、6 Worker、109 Tool
  M1.5: HITL SSE 后端闭环（前端弹窗代码完成，待 M3 联调）
M2: 查询全覆盖与质量加固 — 35+ 单元测试、Query E2E ≥95%、硬编码清理
M3: 写操作 + HITL 前端联调 — ThreadLocal→State 重构、浏览器验证 HITL
M4: Plan-and-Execute + Reflection — 多步任务拆解、自我修正循环
M5: 长期 Memory + MCP + 可观测性 — Redis/Chroma、Micrometer、Guardrails
```

> 详细路线图见项目根目录 `ROADMAP.md`

---

## 测试账号

- sysadmin / SuperAdmin@2035
- zhaotiandi / ninePwd!

---

## 常见问题

### WSL僵尸进程
- 永远不要用 `mvn spring-boot:run` 跑长服务
- 永远用 `java -jar` 方式启动
- 检查端口: `netstat -ano | grep 8300` (Windows)
- 杀进程: `taskkill /PID <pid> /F` (Windows)

### kube-manager连接
- WSL Mirrored模式: `localhost:8100` 直连Windows主机
- 检查状态: `curl http://localhost:8100/api/login -X POST ...`
- CLOSE_WAIT风暴 = 后端线程池耗尽，不是网络问题
