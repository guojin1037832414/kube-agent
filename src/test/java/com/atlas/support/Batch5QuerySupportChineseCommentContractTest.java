package com.atlas.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 5 query/path/body 支撑 helper 中文教学注释契约测试。
 *
 * <p>中文说明：这些 helper 不像 Controller 或 SafeToolExecutor 那样显眼，但它们负责把
 * LLM/Plan/前端传来的候选参数收敛成 kube-manager path/query/body。若注释丢失，后续开发者
 * 很容易把资源 ID、文件路径、充值 body 或默认参数误解成授权事实。</p>
 *
 * <p>安全边界：本测试只读取源码 marker，不调用 Tool、HTTP client、kube-manager、LLM、MCP、
 * audit 或 memory。它保护的是教学说明和禁止动作。</p>
 */
class Batch5QuerySupportChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.of(
        Path.of("src/main/java/com/atlas/tool/impl/DownloadTaskQuerySupport.java"),
        List.of("中文说明", "安全边界", "kube-manager path", "不是授权凭证",
            "sessionId", "ToolPermission"),
        Path.of("src/main/java/com/atlas/tool/impl/CoursewareQuerySupport.java"),
        List.of("中文说明", "安全边界", "URL path", "不是课程访问授权",
            "当前可信用户", "kube-manager 权限"),
        Path.of("src/main/java/com/atlas/tool/impl/TemplateQuerySupport.java"),
        List.of("中文说明", "安全边界", "路径片段", "不能来自 LLM 自行构造",
            "token/orgId", "敏感读取治理"),
        Path.of("src/main/java/com/atlas/tool/impl/TensorBoardQuerySupport.java"),
        List.of("中文说明", "安全边界", "deploymentId", "不代表用户有权读取",
            "敏感读取确认", "kube-manager 权限"),
        Path.of("src/main/java/com/atlas/tool/impl/FileStorageQuerySupport.java"),
        List.of("中文说明", "安全边界", "真实落点", "不是路径安全证明",
            "token", "release 字段"),
        Path.of("src/main/java/com/atlas/tool/impl/UserRiskMutationSupport.java"),
        List.of("中文说明", "安全边界", "高风险写操作", "body 白名单",
            "writeAllowed", "releaseDecision")
    );

    @Test
    void querySupportHelpers_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep query/path/body support teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
