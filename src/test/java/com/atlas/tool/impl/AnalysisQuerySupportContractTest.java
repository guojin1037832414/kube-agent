package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分析/目录 query helper 行为契约测试。
 *
 * <p>中文说明：本测试直接调用 helper，保护它们在不启动 Spring、不执行 Tool 的情况下仍能
 * fail-closed：只复制白名单字段、限制分页、拒绝危险 path/query 形态，且不把控制面字段传给 kube-manager。</p>
 *
 * <p>安全边界：这里不访问真实 8100，不读取账单、镜像目录、行业应用实例或虚拟机；只验证本地参数收敛。</p>
 */
class AnalysisQuerySupportContractTest {

    @Test
    void virtualMachineName_shouldRejectPathAndQueryShapeBeforeEncoding() {
        assertThat(VirtualMachineQuerySupport.encodedVmName(Map.of("name", " vm-training-01 ")))
            .isEqualTo("vm-training-01");

        assertThatThrownBy(() -> VirtualMachineQuerySupport.encodedVmName(Map.of("name", "../vm")))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("虚拟机名称不合法");
        assertThatThrownBy(() -> VirtualMachineQuerySupport.encodedVmName(Map.of("name", "vm?debug=true")))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("虚拟机名称不合法");
    }

    @Test
    void repositoryCatalogQuery_shouldWhitelistFiltersAndRejectUnsafeRepository() {
        Map<String, Object> query = RepositoryCatalogQuerySupport.buildCatalogQuery(Map.of(
            "page", "2",
            "limit", "12",
            "displayName", " llama ",
            "status", " Ready ",
            "industryCategory", " Reasoning ",
            "aieSupported", "yes",
            "aieEssential", "0",
            "isOneClickDeploy", "true",
            "token", "forged",
            "writeAllowed", true
        ));

        assertThat(query)
            .containsEntry("page", "2")
            .containsEntry("limit", "12")
            .containsEntry("displayName", "llama")
            .containsEntry("aieSupported", true)
            .containsEntry("aieEssential", false)
            .containsEntry("isOneClickDeploy", true)
            .doesNotContainKeys("token", "writeAllowed");

        assertThat(RepositoryCatalogQuerySupport.buildRepositoryQuery(Map.of("repository", "nvidia/nim/llama")))
            .containsEntry("repository", "nvidia/nim/llama");
        assertThatThrownBy(() -> RepositoryCatalogQuerySupport.buildRepositoryQuery(
            Map.of("repository", "nvidia/cuda?token=secret")))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("repository 仅支持成熟后端返回的镜像目录标识");
    }

    @Test
    void saleProductQuery_shouldDiscardOrderPaymentAndWriteFields() {
        Map<String, Object> query = SaleProductQuerySupport.buildProductQuery(Map.of(
            "page", "3",
            "limit", "40",
            "productTypeCode", " gpu ",
            "resourceCode", " A800 ",
            "gpuPercentLimits", "100",
            "orderStatus", "paid",
            "paymentId", "pay-1",
            "writeAllowed", true,
            "releaseDecision", "approved"
        ));

        assertThat(query)
            .containsEntry("page", "3")
            .containsEntry("limit", "40")
            .containsEntry("productTypeCode", "gpu")
            .containsEntry("resourceCode", "A800")
            .containsEntry("gpuPercentLimits", "100")
            .doesNotContainKeys("orderStatus", "paymentId", "writeAllowed", "releaseDecision");

        assertThatThrownBy(() -> SaleProductQuerySupport.buildProductQuery(Map.of("limit", "5000")))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("limit 不得大于");
    }

    @Test
    void industryAppQuery_shouldRejectUnsafeIdsAndDropRequestBodies() {
        Map<String, Object> query = IndustryAppQuerySupport.buildApiHistoryQuery(Map.of(
            "page", "1",
            "limit", "20",
            "httpMethod", " POST ",
            "url", " /v1/infer ",
            "sinceSeconds", "3600",
            "requestBody", "{\"secret\":true}",
            "releaseDecision", "approved"
        ));

        assertThat(query)
            .containsEntry("httpMethod", "POST")
            .containsEntry("url", "/v1/infer")
            .containsEntry("sinceSeconds", "3600")
            .doesNotContainKeys("requestBody", "releaseDecision");

        assertThatThrownBy(() -> IndustryAppQuerySupport.positiveId(Map.of("appId", "42/extra"), "appId"))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("appId 仅支持正整数");
        assertThatThrownBy(() -> IndustryAppQuerySupport.buildApiHistoryQuery(Map.of("sinceSeconds", "999999")))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("sinceSeconds 不得大于");
    }

    @Test
    void financialQuery_shouldDiscardIdentityPaymentAndWriteFields() {
        Map<String, Object> query = FinancialAnalysisQuerySupport.buildPodUseBillQuery(Map.of(
            "page", "2",
            "limit", "100",
            "applicationName", " gpu-train ",
            "podStatus", " finish ",
            "userId", "777",
            "orgId", "100001",
            "paymentId", "pay-1",
            "writeAllowed", true
        ));

        assertThat(query)
            .containsEntry("page", "2")
            .containsEntry("limit", "100")
            .containsEntry("applicationName", "gpu-train")
            .containsEntry("podStatus", "finish")
            .doesNotContainKeys("userId", "orgId", "paymentId", "writeAllowed");

        assertThatThrownBy(() -> FinancialAnalysisQuerySupport.buildCostConfigQuery(Map.of("limit", "5000")))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("limit 不得大于");
    }
}
