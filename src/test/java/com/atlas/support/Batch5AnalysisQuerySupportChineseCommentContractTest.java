package com.atlas.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 5 分析/目录类 query helper 中文教学注释契约测试。
 *
 * <p>中文说明：本测试覆盖虚拟机、镜像目录、产品报价、行业应用和成本账单这些通用查询 helper。
 * 它们都是 kube-manager GET 前的参数收敛层，必须写清“只读筛选字段”和“不能透传控制面字段”的边界。</p>
 *
 * <p>安全边界：本测试只读源码，不调用 Tool、HTTP client、kube-manager、LLM、MCP、audit 或 memory。
 * 它只保护教学 marker，避免后续重构删除关键学习说明。</p>
 */
class Batch5AnalysisQuerySupportChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.of(
        Path.of("src/main/java/com/atlas/tool/impl/VirtualMachineQuerySupport.java"),
        List.of("中文说明", "安全边界", "VM 名称只是资源定位符", "不接入启动、停止、删除",
            "高风险 ToolPermission", "token、orgId"),
        Path.of("src/main/java/com/atlas/tool/impl/RepositoryCatalogQuerySupport.java"),
        List.of("中文说明", "安全边界", "敏感只读目录", "不是镜像拉取、部署创建、NIM 创建",
            "二期运行时恢复", "HITL 敏感读取确认"),
        Path.of("src/main/java/com/atlas/tool/impl/SaleProductQuerySupport.java"),
        List.of("中文说明", "安全边界", "订单创建、支付、状态流转", "只读 query",
            "writeAllowed", "releaseDecision"),
        Path.of("src/main/java/com/atlas/tool/impl/IndustryAppQuerySupport.java"),
        List.of("中文说明", "安全边界", "不创建实例、不调用 API", "requestBody",
            "releaseDecision", "只读 query/path 参数"),
        Path.of("src/main/java/com/atlas/tool/impl/FinancialAnalysisQuerySupport.java"),
        List.of("中文说明", "安全边界", "敏感只读能力", "不能表达用户身份、租户",
            "支付", "HITL 敏感读取确认")
    );

    @Test
    void analysisQueryHelpers_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep analysis query helper teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
