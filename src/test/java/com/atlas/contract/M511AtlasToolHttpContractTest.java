package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.11 Atlas Tool HTTP 元数据契约测试。
 *
 * <p>本测试只做源码级静态检查，不启动 Spring 容器，不注入 Bean，不调用 kube-manager，
 * 因此不会触发任何真实 GET/POST/DELETE 数据面请求。</p>
 *
 * <p>设计边界：M5.11 采用“先小样本验证，再分批铺开”的治理方式。历史 Tool 的
 * {@code httpMethod} 默认仍为空，本测试暂不强迫 110 个 Tool 一次性补齐；但凡已经声明
 * {@code httpMethod} 的 Tool，都必须满足声明方法、业务风险语义与源码实际 HTTP 调用一致。</p>
 */
class M511AtlasToolHttpContractTest {

    /** Tool 实现类所在目录。 */
    private static final Path TOOL_IMPL_DIR = Path.of("src/main/java/com/atlas/tool/impl");

    /** Atlas Tool 抽象基类源码路径，用于识别子类可见的继承 HTTP Client 字段。 */
    private static final Path BASE_TOOL_FILE = Path.of("src/main/java/com/atlas/tool/core/BaseTool.java");

    /** 声明为无真实 HTTP 的占位方法。 */
    private static final String NONE_METHOD = "NONE";

    /** 匹配 AtlasToolMapping 注解块。 */
    private static final Pattern MAPPING_PATTERN = Pattern.compile("@AtlasToolMapping\\s*\\((.*?)\\)\\s*(?:@ToolPermission|public class)", Pattern.DOTALL);

    /** 匹配注解中的字符串属性。 */
    private static final Pattern STRING_ATTRIBUTE_PATTERN_TEMPLATE = Pattern.compile("%s\\s*=\\s*\\\"([^\\\"]*)\\\"");

    /** 匹配注解中的 apiEndpoints 数组，支持 endpoint 字符串内部继续包含 {orgId}/{id} 等占位符。 */
    private static final Pattern API_ENDPOINTS_PATTERN = Pattern.compile("apiEndpoints\\s*=\\s*\\{((?:\\s*\\\"[^\\\"]*\\\"\\s*,?)+)\\}", Pattern.DOTALL);

    /** 匹配注解中的 operationType 枚举。 */
    private static final Pattern OPERATION_TYPE_PATTERN = Pattern.compile("operationType\\s*=\\s*AtlasToolMapping\\.OperationType\\.([A-Z_]+)");

    /** 匹配注解中的 requiresConfirmation 布尔值。 */
    private static final Pattern REQUIRES_CONFIRMATION_PATTERN = Pattern.compile("requiresConfirmation\\s*=\\s*(true|false)");

    /** 匹配 KubeManagerHttpClient 字段名，兼容 httpClient/kubeManagerHttpClient 等变量。 */
    private static final Pattern CLIENT_FIELD_PATTERN = Pattern.compile("KubeManagerHttpClient\\s+(\\w+)\\s*;");

    /** 匹配继承 BaseTool 的 Tool 子类。 */
    private static final Pattern EXTENDS_BASE_TOOL_PATTERN = Pattern.compile("class\\s+\\w+\\s+extends\\s+BaseTool");

    @Test
    void declaredAtlasToolHttpMetadata_shouldMatchActualKubeManagerClientCalls() throws IOException {
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(TOOL_IMPL_DIR)) {
            files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith("Tool.java"))
                .sorted()
                .forEach(path -> verifyToolFile(path, violations));
        }

        assertThat(violations)
            .as("M5.11 Atlas Tool HTTP 元数据契约违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m516ReadExpansionEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("BareMetalAppListTool.java", "bare_metal_app_list", "/api/{orgId}/bare-metal-application"),
            new ExpectedEndpoint("ComposeListTool.java", "compose_list", "/api/{orgId}/compose"),
            new ExpectedEndpoint("HelmChartInfoTool.java", "helm_chart_info", "/api/{orgId}/helm/charts/single"),
            new ExpectedEndpoint("HelmChartSearchTool.java", "helm_chart_search", "/api/{orgId}/helm/repositories/charts"),
            new ExpectedEndpoint("HelmReleaseHistoryTool.java", "helm_release_history", "/api/{orgId}/helm/releases/{release}/histories"),
            new ExpectedEndpoint("HelmReleaseListTool.java", "helm_release_list", "/api/{orgId}/helm/releases"),
            new ExpectedEndpoint("HelmRepoListTool.java", "helm_repo_list", "/api/{orgId}/helm/repositories"),
            new ExpectedEndpoint("MpiJobDetailTool.java", "mpi_job_detail", "/api/{orgId}/mpi-job/{id}"),
            new ExpectedEndpoint("MpiJobListTool.java", "mpi_job_list", "/api/{orgId}/mpi-job"),
            new ExpectedEndpoint("CloudResourceListTool.java", "cloud_resource_list", "/api/{orgId}/cloud"),
            new ExpectedEndpoint("CurrencyQueryListTool.java", "currency_query_list", "/api/{orgId}/currency"),
            new ExpectedEndpoint("DeploymentDetailTool.java", "deployment_detail", "/api/{orgId}/deployment"),
            new ExpectedEndpoint("ImageDetailByNameTool.java", "image_detail_by_name", "/api/{orgId}/image/name"),
            new ExpectedEndpoint("NodeDetailTool.java", "node_detail", "/api/{orgId}/node"),
            new ExpectedEndpoint("EasyFlowAnalyzerListTool.java", "easy_flow_analyzer_list", "/api/{orgId}/easy-flow/analyzer"),
            new ExpectedEndpoint("EasyFlowFlowDetailTool.java", "easy_flow_flow_detail", "/api/{orgId}/easy-flow/flow/{flowId}"),
            new ExpectedEndpoint("EasyFlowFlowListTool.java", "easy_flow_flow_list", "/api/{orgId}/easy-flow/flow"),
            new ExpectedEndpoint("EasyFlowInstanceDetailTool.java", "easy_flow_instance_detail", "/api/{orgId}/easy-flow/instance/{instanceId}"),
            new ExpectedEndpoint("EasyFlowInstanceListTool.java", "easy_flow_instance_list", "/api/{orgId}/easy-flow/instance"),
            new ExpectedEndpoint("EasyFlowStageDetailTool.java", "easy_flow_stage_detail", "/api/{orgId}/easy-flow/flow/{flowId}/stage/{stageId}"),
            new ExpectedEndpoint("EasyFlowStageListTool.java", "easy_flow_stage_list", "/api/{orgId}/easy-flow/flow/{flowId}/stage")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.16", "READ", false, violations);
        }

        assertThat(violations)
            .as("M5.16 READ 扩面 endpoint 精确白名单违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m517InfrastructureReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("ClusterQueryTool.java", "cluster_query", "/api/{orgId}/hpc-job/cluster"),
            new ExpectedEndpoint("NodeQueryTool.java", "node_query", "/api/{orgId}/node"),
            new ExpectedEndpoint("NodeMetricsTool.java", "node_metrics", "/api/{orgId}/node"),
            new ExpectedEndpoint("GpuQueryTool.java", "gpu_query", "/api/{orgId}/node/all/gpu-map"),
            new ExpectedEndpoint("GpuMetricsTool.java", "gpu_metrics", "/api/{orgId}/node/all/gpu-map"),
            new ExpectedEndpoint("NetworkQueryTool.java", "network_query", "/api/{orgId}/dashboard/deployment"),
            new ExpectedEndpoint("PodQueryTool.java", "pod_status", "/api/{orgId}/pod"),
            new ExpectedEndpoint("NodeRemainingResourceTool.java", "node_remaining_resource", "/api/{orgId}/node/remaining"),
            new ExpectedEndpoint("MetricGpuServerInstantTool.java", "metric_gpu_server_instant", "/api/public/metric/prometheus/instant/server/gpu"),
            new ExpectedEndpoint("MetricCpuServerInstantTool.java", "metric_cpu_server_instant", "/api/public/metric/prometheus/instant/server/cpu"),
            new ExpectedEndpoint("MetricStorageServerInstantTool.java", "metric_storage_server_instant", "/api/public/metric/prometheus/instant/server/storage"),
            new ExpectedEndpoint("MetricPodInstantTool.java", "metric_pod_instant", "/api/public/metric/prometheus/instant/pod"),
            new ExpectedEndpoint("DaemonSetQueryTool.java", "daemonset_status", "/api/{orgId}/dashboard/deployment"),
            new ExpectedEndpoint("DeploymentQueryTool.java", "deployment_status", "/api/{orgId}/deployment"),
            new ExpectedEndpoint("ServiceQueryTool.java", "service_status", "/api/{orgId}/dashboard/resources"),
            new ExpectedEndpoint("IngressQueryTool.java", "ingress_query", "/api/{orgId}/dashboard/deployment"),
            new ExpectedEndpoint("ResourceMonitorTool.java", "resource_monitor", "/api/{orgId}/resource"),
            new ExpectedEndpoint("ResourcePresetListTool.java", "resource_preset_list", "/api/{orgId}/resource-preset"),
            new ExpectedEndpoint("ResourcePresetDetailTool.java", "resource_preset_detail", "/api/{orgId}/resource-preset/{resourcePresetId}"),
            new ExpectedEndpoint("DashboardEasyFlowCountTool.java", "dashboard_easy_flow_count", "/api/{orgId}/dashboard/easy-flow/count"),
            new ExpectedEndpoint("SlurmClusterListTool.java", "slurm_cluster_list", "/api/{orgId}/bcm/slurm-cluster"),
            new ExpectedEndpoint("SlurmNodeListTool.java", "slurm_node_list", "/api/{orgId}/slurm-node")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.17", "READ", false, violations);
        }

        assertThat(violations)
            .as("M5.17 基础设施 READ 扩面 endpoint 精确白名单违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m518SensitiveReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("LogQueryTool.java", "log_query", "/api/log"),
            new ExpectedEndpoint("EasyFlowInstanceLogAbstractTool.java", "easy_flow_instance_log_abstract", "/api/{orgId}/easy-flow/instance/{instanceId}/log/{stageCode}/abstract"),
            new ExpectedEndpoint("EasyFlowInstanceLogListTool.java", "easy_flow_instance_log_list", "/api/{orgId}/easy-flow/instance/{instanceId}/log/{stageCode}/list"),
            new ExpectedEndpoint("EasyFlowInstanceLogTool.java", "easy_flow_instance_log", "/api/{orgId}/easy-flow/instance/{instanceId}/log/{stageCode}"),
            new ExpectedEndpoint("UserQueryTool.java", "user_query", "/api/{orgId}/user"),
            new ExpectedEndpoint("PermissionMenuListTool.java", "permission_menu_list", "/api/{orgId}/permission/menu"),
            new ExpectedEndpoint("KubernetesDashboardLinkTool.java", "kubernetes_dashboard_link", "/api/external-link/kubernetes/dashboard"),
            new ExpectedEndpoint("LdapConfigListTool.java", "ldap_config_list", "/api/{orgId}/ldap"),
            new ExpectedEndpoint("RoleAssignableListTool.java", "role_assignable", "/api/{orgId}/role/assignable"),
            new ExpectedEndpoint("OrderListTool.java", "order_list", "/api/{orgId}/lease/order"),
            new ExpectedEndpoint("PodUseRecordListTool.java", "pod_use_record_list", "/api/{orgId}/pod-use/record"),
            new ExpectedEndpoint("PodUseBillListTool.java", "pod_use_bill_list", "/api/{orgId}/pod-use/bill"),
            new ExpectedEndpoint("CostConfigListTool.java", "cost_config_list", "/api/{orgId}/cost"),
            new ExpectedEndpoint("QuotaMyListTool.java", "quota_my_list", "/api/{orgId}/quota/my")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.18", "SENSITIVE_READ", true, violations);
        }

        assertThat(violations)
            .as("M5.18 敏感读取 endpoint 精确白名单违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521IndustryAppReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("IndustryAppTemplateListTool.java", "industry_app_template_list", "/api/{orgId}/industry-app/template"),
            new ExpectedEndpoint("IndustryAppTemplateDetailTool.java", "industry_app_template_detail", "/api/{orgId}/industry-app/template/{appId}"),
            new ExpectedEndpoint("IndustryAppTemplateApiDocTool.java", "industry_app_template_api_doc", "/api/{orgId}/industry-app/template/{appId}/api-doc"),
            new ExpectedEndpoint("IndustryAppInstanceListTool.java", "industry_app_instance_list", "/api/{orgId}/industry-app/instance"),
            new ExpectedEndpoint("IndustryAppInstanceApiHistoryTool.java", "industry_app_instance_api_history", "/api/{orgId}/industry-app/instance/{instanceId}/api-history"),
            new ExpectedEndpoint("IndustryAppResourcePresetListTool.java", "industry_app_resource_preset_list", "/api/{orgId}/industry-app/template/{appId}/resource-preset"),
            new ExpectedEndpoint("IndustryAppParamListTool.java", "industry_app_param_list", "/api/{orgId}/industry-app/template/{appId}/app-param")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "READ", false, violations);
        }

        assertThat(violations)
            .as("M5.21 行业应用 READ endpoint 精确白名单违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521SaleProductReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("PublicProductTypeListTool.java", "public_product_type_list", "/api/public/product/type"),
            new ExpectedEndpoint("PublicPostPayProductListTool.java", "public_post_pay_product_list", "/api/public/product/post-pay"),
            new ExpectedEndpoint("PublicPrePayProductListTool.java", "public_pre_pay_product_list", "/api/public/product/pre-pay"),
            new ExpectedEndpoint("PublicServerProductListTool.java", "public_server_product_list", "/api/public/product/server"),
            new ExpectedEndpoint("LeaseOrderAmountEstimateTool.java", "lease_order_amount_estimate", "/api/{orgId}/lease/order/count")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "READ", false, violations);
        }

        assertThat(violations)
            .as("M5.21 产品与租赁报价 READ endpoint 精确白名单违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521ProductConfigSensitiveReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("ProductTypeListTool.java", "product_type_list", "/api/{orgId}/product/type"),
            new ExpectedEndpoint("PostPayProductListTool.java", "post_pay_product_list", "/api/{orgId}/product/post-pay"),
            new ExpectedEndpoint("ServerConfigListTool.java", "server_config_list", "/api/{orgId}/server")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "SENSITIVE_READ", true, violations);
        }

        assertThat(violations)
            .as("M5.21 product config SENSITIVE_READ endpoint exact whitelist violations\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521HpcPreparationReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("HpcPartitionListTool.java", "hpc_partition_list", "/api/{orgId}/hpc-job/partition/{clusterId}"),
            new ExpectedEndpoint("HpcSbatchParameterListTool.java", "hpc_sbatch_parameter_list", "/api/{orgId}/hpc-job/sbatch_parameter/{category}")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "READ", false, violations);
        }

        assertThat(violations)
            .as("M5.21 HPC 作业准备数据 READ endpoint 精确白名单违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521HpcEnvironmentModuleSensitiveReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("HpcEnvironmentListTool.java", "hpc_environment_list", "/api/{orgId}/hpc-env/environments/{clusterId}"),
            new ExpectedEndpoint("HpcModuleListTool.java", "hpc_module_list", "/api/{orgId}/hpc-env/modules")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "SENSITIVE_READ", true, violations);
        }

        assertThat(violations)
            .as("M5.21 HPC environment/module SENSITIVE_READ endpoint exact whitelist violations\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521BcmAllocationSensitiveReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("BcmUserListTool.java", "bcm_user_list", "/api/{orgId}/bcm/users"),
            new ExpectedEndpoint("BcmSlurmNodeAllocationListTool.java", "bcm_slurm_node_allocation_list", "/api/{orgId}/bcm/all-slurm-nodes"),
            new ExpectedEndpoint("BcmBareMetalNodeAllocationListTool.java", "bcm_bare_metal_node_allocation_list", "/api/{orgId}/bcm/all-bare-metal-nodes")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "SENSITIVE_READ", true, violations);
        }

        assertThat(violations)
            .as("M5.21 BCM allocation SENSITIVE_READ endpoint exact whitelist violations\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521FileStorageSensitiveReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("FileVolumePathTool.java", "file_volume_path", "/api/{orgId}/file/volume-path"),
            new ExpectedEndpoint("FileUserVolumePathTool.java", "file_user_volume_path", "/api/{orgId}/file/volume-path/user"),
            new ExpectedEndpoint("FileUserExtraVolumePathTool.java", "file_user_extra_volume_path", "/api/{orgId}/file/volume-path/user-extra"),
            new ExpectedEndpoint("FileClaimedVolumeOptionListTool.java", "file_claimed_volume_option_list", "/api/{orgId}/file/claimed-volume-option"),
            new ExpectedEndpoint("FileStorageOptionTool.java", "file_storage_option", "/api/{orgId}/file/storage/option"),
            new ExpectedEndpoint("FileSelectStorageTool.java", "file_select_storage", "/api/{orgId}/file/selectStorage"),
            new ExpectedEndpoint("FileTrainStorageTool.java", "file_train_storage", "/api/{orgId}/file/train-storage")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "SENSITIVE_READ", true, violations);
        }

        assertThat(violations)
            .as("M5.21 file/storage SENSITIVE_READ endpoint exact whitelist violations\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521LegacyGetEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedReadEndpoints = List.of(
            new ExpectedEndpoint("ImageQueryTool.java", "image_query", "/api/{orgId}/image"),
            new ExpectedEndpoint("PytorchJobListTool.java", "pytorch_job_list", "/api/{orgId}/pytorch-job"),
            new ExpectedEndpoint("MigConfigListTool.java", "mig_config_list", "/api/mig/{gpuId}")
        );
        List<ExpectedEndpoint> expectedSensitiveReadEndpoints = List.of(
            new ExpectedEndpoint("DataSetListTool.java", "data_set_list", "/api/{orgId}/data-set"),
            new ExpectedEndpoint("FileListTool.java", "file_list", "/api/{orgId}/file"),
            new ExpectedEndpoint("DownloadTaskListTool.java", "download_task_list", "/api/{orgId}/download"),
            new ExpectedEndpoint("UploadStatusListTool.java", "upload_status_list", "/api/{orgId}/download/status/{id}"),
            new ExpectedEndpoint("DownloadTaskProgressTool.java", "download_task_progress", "/api/{orgId}/download/progress/{id}"),
            new ExpectedEndpoint("RegistryListTool.java", "registry_list", "/api/registry"),
            new ExpectedEndpoint("FileMaterialListTool.java", "file_material_list", "/api/{orgId}/material/folders"),
            new ExpectedEndpoint("InboxMessageListTool.java", "inbox_message_list", "/api/{orgId}/inbox-message")
        );

        for (ExpectedEndpoint expected : expectedReadEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "READ", false, violations);
        }
        for (ExpectedEndpoint expected : expectedSensitiveReadEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "SENSITIVE_READ", true, violations);
        }

        assertThat(violations)
            .as("M5.21 legacy GET endpoint exact whitelist violations\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521VirtualMachineReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("VirtualMachineListTool.java", "virtual_machine_list", "/api/{orgId}/virtual-machine"),
            new ExpectedEndpoint("VirtualMachineDetailTool.java", "virtual_machine_detail", "/api/{orgId}/virtual-machine/{name}")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "READ", false, violations);
        }

        assertThat(violations)
            .as("M5.21 虚拟机 READ endpoint 精确白名单违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521TemplateReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("TemplateListTool.java", "template_list", "/api/{orgId}/template"),
            new ExpectedEndpoint("TemplateDetailTool.java", "template_detail", "/api/{orgId}/template/{templateId}"),
            new ExpectedEndpoint("JobTemplateListTool.java", "job_template_list", "/api/{orgId}/train-job-template"),
            new ExpectedEndpoint("JobTemplateDetailTool.java", "job_template_detail", "/api/{orgId}/train-job-template/{templateId}")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "READ", false, violations);
        }

        assertThat(violations)
            .as("M5.21 模板详情 READ endpoint 精确白名单违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521CoursewareReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedReadEndpoints = List.of(
            new ExpectedEndpoint("CoursewareListTool.java", "courseware_list", "/api/{orgId}/courseware/list"),
            new ExpectedEndpoint("CoursewareDetailTool.java", "courseware_detail", "/api/{orgId}/courseware/info/{coursewareId}")
        );
        List<ExpectedEndpoint> expectedSensitiveReadEndpoints = List.of(
            new ExpectedEndpoint("CoursewareGradeListTool.java", "courseware_grade_list", "/api/{orgId}/courseware/grade/{coursewareId}"),
            new ExpectedEndpoint("CoursewareLearningStatusTool.java", "courseware_learning_status", "/api/{orgId}/learn/deployment/status/{coursewareId}")
        );

        for (ExpectedEndpoint expected : expectedReadEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "READ", false, violations);
        }
        for (ExpectedEndpoint expected : expectedSensitiveReadEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "SENSITIVE_READ", true, violations);
        }

        assertThat(violations)
            .as("M5.21 课件 READ endpoint 精确白名单违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m521TensorBoardSensitiveReadEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedEndpoint> expectedEndpoints = List.of(
            new ExpectedEndpoint("TensorBoardListTool.java", "tensorboard_list", "/api/{orgId}/tensorboard"),
            new ExpectedEndpoint("TensorBoardEnvironmentTool.java", "tensorboard_environment", "/api/{orgId}/tensorboard/data/environment"),
            new ExpectedEndpoint("TensorBoardRunsTool.java", "tensorboard_runs", "/api/{orgId}/tensorboard/data/runs"),
            new ExpectedEndpoint("TrainJobTensorBoardRunsTool.java", "trainjob_tensorboard_runs", "/api/{orgId}/tensorboard/trainjob-runs/{tensorBoardDeploymentId}")
        );

        for (ExpectedEndpoint expected : expectedEndpoints) {
            verifyExpectedEndpoint(expected, "M5.21", "SENSITIVE_READ", true, violations);
        }

        assertThat(violations)
            .as("M5.21 TensorBoard SENSITIVE_READ endpoint exact whitelist violations\n%s", String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void m519HighRiskMutationEndpoints_shouldMatchReviewedWhitelist() throws IOException {
        List<String> violations = new ArrayList<>();
        List<ExpectedRiskEndpoint> expectedEndpoints = List.of(
            new ExpectedRiskEndpoint("ComposeDeployCreateTool.java", "compose_deploy_create", "POST", "CREATE", "/api/{orgId}/compose/deploy"),
            new ExpectedRiskEndpoint("ComposeDeployDeleteTool.java", "compose_deploy_delete", "DELETE", "DELETE", "/api/{orgId}/compose/{composeId}"),
            new ExpectedRiskEndpoint("ComposeDeployUpdateTool.java", "compose_deploy_update", "PUT", "ACTION", "/api/{orgId}/compose/{composeId}"),
            new ExpectedRiskEndpoint("DeployCreateTool.java", "deploy_create_instance", "POST", "CREATE", "/api/{orgId}/deployment"),
            new ExpectedRiskEndpoint("DeployDeleteTool.java", "deploy_delete", "DELETE", "DELETE", "/api/{orgId}/deployment?name={name}"),
            new ExpectedRiskEndpoint("DeployScaleTool.java", "deploy_scale", "PATCH", "ACTION", "/api/{orgId}/deployment/scale"),
            new ExpectedRiskEndpoint("DistributedCreateTool.java", "distributed_create", "POST", "CREATE", "/api/{orgId}/bcm/slurm-cluster"),
            new ExpectedRiskEndpoint("ExperimentInstanceDeleteTool.java", "experiment_instance_delete", "NONE", "PLACEHOLDER", ""),
            new ExpectedRiskEndpoint("ExperimentInstanceStopTool.java", "experiment_instance_stop", "PUT", "ACTION", "/api/{orgId}/experiment/instance/shutdown/{id}"),
            new ExpectedRiskEndpoint("ExperimentStartTool.java", "experiment_start", "PUT", "ACTION", "/api/{orgId}/experiment/instance/start/{id}"),
            new ExpectedRiskEndpoint("HelmReleaseInstallTool.java", "helm_release_install", "POST", "CREATE", "/api/{orgId}/helm/releases/{release}"),
            new ExpectedRiskEndpoint("HelmReleaseRollbackTool.java", "helm_release_rollback", "PUT", "ACTION", "/api/{orgId}/helm/release/{release}/rollback/{version}"),
            new ExpectedRiskEndpoint("HelmReleaseDeleteTool.java", "helm_release_delete", "DELETE", "DELETE", "/api/{orgId}/helm/releases/{releaseName}"),
            new ExpectedRiskEndpoint("HelmReleaseUpgradeTool.java", "helm_release_upgrade", "PUT", "ACTION", "/api/{orgId}/helm/release/{release}/upgrade"),
            new ExpectedRiskEndpoint("HelmRepoAddTool.java", "helm_repo_add", "POST", "CREATE", "/api/{orgId}/helm/repositories"),
            new ExpectedRiskEndpoint("HelmRepoRemoveTool.java", "helm_repo_remove", "DELETE", "DELETE", "/api/{orgId}/helm/repositories/{repoName}"),
            new ExpectedRiskEndpoint("HelmRepoUpdateTool.java", "helm_repo_update", "PUT", "ACTION", "/api/{orgId}/helm/repositories"),
            new ExpectedRiskEndpoint("ImageDeleteTool.java", "image_delete", "DELETE", "DELETE", "/api/{orgId}/image/{imageId}?entirely={entirely}"),
            new ExpectedRiskEndpoint("ImagePullTool.java", "image_pull", "POST", "ACTION", "/api/{orgId}/image/pull"),
            new ExpectedRiskEndpoint("MpiJobAbortTool.java", "mpi_job_abort", "POST", "ACTION", "/api/{orgId}/mpi-job/{jobId}"),
            new ExpectedRiskEndpoint("MpiJobSubmitTool.java", "mpi_job_submit", "POST", "ACTION", "/api/{orgId}/mpi-job/submit/{mpiJobId}"),
            new ExpectedRiskEndpoint("NimCreateTool.java", "nim_create", "NONE", "PLACEHOLDER", ""),
            new ExpectedRiskEndpoint("PytorchJobSubmitTool.java", "pytorch_job_submit", "POST", "ACTION", "/api/{orgId}/pytorch-job/submit/{pyTorchJobId}"),
            new ExpectedRiskEndpoint("StorageCreateTool.java", "storage_create", "POST", "CREATE", "/api/{orgId}/file/storage"),
            new ExpectedRiskEndpoint("StorageDeleteTool.java", "storage_delete", "DELETE", "DELETE", "/api/{orgId}/file/deleteStorage?name={name}"),
            new ExpectedRiskEndpoint("UserCreateTool.java", "user_create", "POST", "CREATE", "/api/{orgId}/user"),
            new ExpectedRiskEndpoint("UserDeleteTool.java", "user_delete", "DELETE", "DELETE", "/api/{orgId}/user/{id}"),
            new ExpectedRiskEndpoint("UserEnableTool.java", "user_enable", "PUT", "ACTION", "/api/{orgId}/user/enable/{id}"),
            new ExpectedRiskEndpoint("UserDisableTool.java", "user_disable", "PUT", "ACTION", "/api/{orgId}/user/disable/{id}"),
            new ExpectedRiskEndpoint("UserRechargeTool.java", "user_recharge", "PUT", "ACTION", "/api/{orgId}/user/recharge")
        );

        for (ExpectedRiskEndpoint expected : expectedEndpoints) {
            verifyExpectedRiskEndpoint(expected, "M5.19", violations);
        }

        assertThat(violations)
            .as("M5.19 高风险写入/删除/动作 endpoint 精确白名单违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    /**
     * 校验单个 Tool 文件。
     *
     * <p>没有声明 {@code httpMethod} 的历史 Tool 暂时跳过；已经声明的 Tool 进入严格校验。
     * 这样可以在不一次性改动 110 个 Tool 的前提下，把新增/迁移 Tool 纳入强契约。</p>
     */
    private void verifyToolFile(Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            Matcher mappingMatcher = MAPPING_PATTERN.matcher(source);
            if (!mappingMatcher.find()) {
                return;
            }

            String annotation = mappingMatcher.group(1);
            String toolName = readStringAttribute(annotation, "name");
            String declaredMethod = readStringAttribute(annotation, "httpMethod").toUpperCase(Locale.ROOT);
            if (declaredMethod.isBlank()) {
                return;
            }

            String operationType = readOperationType(annotation);
            boolean requiresConfirmation = readRequiresConfirmation(annotation);
            Set<String> declaredEndpoints = readApiEndpoints(annotation);
            Set<String> clientFieldNames = readVisibleClientFieldNames(source);
            Set<String> actualMethods = readActualHttpMethods(source, clientFieldNames);

            verifyMethodConsistency(path, toolName, declaredMethod, actualMethods, violations);
            verifyRiskMetadata(path, toolName, declaredMethod, operationType, requiresConfirmation, violations);
            verifyEndpointMetadata(path, toolName, declaredMethod, declaredEndpoints, violations);
        } catch (IOException e) {
            violations.add(format(path, "READ_FILE_FAILED", "读取 Tool 源码失败: " + e.getMessage()));
        }
    }

    /**
     * 校验注解声明的 HTTP 方法与 doExecute 中真实 KubeManagerHttpClient 调用一致。
     */
    private void verifyMethodConsistency(Path path, String toolName, String declaredMethod,
                                         Set<String> actualMethods, List<String> violations) {
        if (NONE_METHOD.equals(declaredMethod)) {
            if (!actualMethods.isEmpty()) {
                violations.add(format(path, "PLACEHOLDER_CALLS_HTTP",
                    "tool=" + toolName + ", declared=NONE, actualMethods=" + actualMethods
                        + ", suggestion=占位 Tool 若已接入真实 HTTP，请改为真实 httpMethod 并更新 operationType"));
            }
            return;
        }

        if (actualMethods.isEmpty()) {
            violations.add(format(path, "MISSING_HTTP_CALL",
                "tool=" + toolName + ", declared=" + declaredMethod
                    + ", actualMethods=[], suggestion=非 NONE Tool 必须在 doExecute 中调用 KubeManagerHttpClient"));
            return;
        }

        if (!actualMethods.equals(Set.of(declaredMethod))) {
            violations.add(format(path, "HTTP_METHOD_MISMATCH",
                "tool=" + toolName + ", declared=" + declaredMethod + ", actualMethods=" + actualMethods
                    + ", suggestion=修正 @AtlasToolMapping.httpMethod 或真实 httpClient 调用"));
        }
    }

    /**
     * 校验风险元数据，避免把写入、删除、敏感读取、占位 Tool 暴露成普通只读 Tool。
     */
    private void verifyRiskMetadata(Path path, String toolName, String declaredMethod,
                                    String operationType, boolean requiresConfirmation,
                                    List<String> violations) {
        if (operationType.isBlank() || "UNKNOWN".equals(operationType)) {
            violations.add(format(path, "UNKNOWN_OPERATION_TYPE",
                "tool=" + toolName + ", declaredMethod=" + declaredMethod
                    + ", suggestion=已声明 HTTP 契约的 Tool 必须显式声明 operationType"));
            return;
        }

        if ("READ".equals(operationType) && requiresConfirmation) {
            violations.add(format(path, "PLAIN_READ_SHOULD_NOT_REQUIRE_CONFIRMATION",
                "tool=" + toolName + ", suggestion=普通 READ 应保持无确认；敏感读取请改用 SENSITIVE_READ"));
        }

        if (!"READ".equals(operationType) && !requiresConfirmation) {
            violations.add(format(path, "RISKY_TOOL_WITHOUT_CONFIRMATION",
                "tool=" + toolName + ", operationType=" + operationType
                    + ", suggestion=除普通 READ 外，SENSITIVE_READ/CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER 均必须 requiresConfirmation=true"));
        }

        if ("GET".equals(declaredMethod) && !Set.of("READ", "SENSITIVE_READ").contains(operationType)) {
            violations.add(format(path, "GET_METHOD_INVALID_OPERATION_TYPE",
                "tool=" + toolName + ", operationType=" + operationType
                    + ", suggestion=GET 只能标记为 READ 或 SENSITIVE_READ"));
        }

        if (Set.of("POST", "PUT", "PATCH", "DELETE").contains(declaredMethod)
            && Set.of("READ", "SENSITIVE_READ").contains(operationType)) {
            violations.add(format(path, "WRITE_METHOD_MARKED_READ",
                "tool=" + toolName + ", declaredMethod=" + declaredMethod
                    + ", operationType=" + operationType + ", suggestion=写入/删除类 HTTP 方法不得标记为 READ/SENSITIVE_READ"));
        }

        if ("DELETE".equals(declaredMethod) && !"DELETE".equals(operationType)) {
            violations.add(format(path, "DELETE_METHOD_NOT_DELETE_OPERATION",
                "tool=" + toolName + ", operationType=" + operationType
                    + ", suggestion=HTTP DELETE 必须标记为 DELETE operationType"));
        }

        if (Set.of("PUT", "PATCH").contains(declaredMethod) && !Set.of("UPDATE", "ACTION").contains(operationType)) {
            violations.add(format(path, "UPDATE_METHOD_INVALID_OPERATION_TYPE",
                "tool=" + toolName + ", declaredMethod=" + declaredMethod + ", operationType=" + operationType
                    + ", suggestion=PUT/PATCH 应标记为 UPDATE 或 ACTION"));
        }

        if ("POST".equals(declaredMethod) && !Set.of("CREATE", "UPDATE", "DELETE", "ACTION").contains(operationType)) {
            violations.add(format(path, "POST_METHOD_INVALID_OPERATION_TYPE",
                "tool=" + toolName + ", operationType=" + operationType
                    + ", suggestion=POST 应按业务语义标记为 CREATE/UPDATE/DELETE/ACTION"));
        }
    }

    /**
     * 校验 endpoint 元数据存在性。M5.11 小样本阶段暂不做复杂 Java 表达式路径反解。
     */
    private void verifyEndpointMetadata(Path path, String toolName, String declaredMethod,
                                        Set<String> declaredEndpoints, List<String> violations) {
        if (!NONE_METHOD.equals(declaredMethod) && declaredEndpoints.isEmpty()) {
            violations.add(format(path, "MISSING_API_ENDPOINTS",
                "tool=" + toolName + ", declaredMethod=" + declaredMethod
                    + ", suggestion=非 NONE Tool 必须声明 apiEndpoints，支持多路径 fallback"));
        }
    }

    /**
     * 校验人工 Review 后的 endpoint 精确白名单。
     *
     * <p>现有 HTTP 契约会校验方法和风险类型，但不会反解字符串拼接后的真实路径。
     * 因此这里用人工会诊确认过的白名单，重点防止动态尾段被写漏、detail 查询被臆造成错误路径，
     * 以及敏感 GET 被误标为普通 READ。</p>
     */
    private void verifyExpectedEndpoint(ExpectedEndpoint expected, String milestone,
                                        String expectedOperationType, boolean expectedRequiresConfirmation,
                                        List<String> violations) {
        Path path = TOOL_IMPL_DIR.resolve(expected.fileName());
        try {
            String source = Files.readString(path);
            Matcher mappingMatcher = MAPPING_PATTERN.matcher(source);
            if (!mappingMatcher.find()) {
                violations.add(format(path, "MISSING_ATLAS_TOOL_MAPPING", "tool=" + expected.toolName()));
                return;
            }

            String annotation = mappingMatcher.group(1);
            String actualToolName = readStringAttribute(annotation, "name");
            String declaredMethod = readStringAttribute(annotation, "httpMethod").toUpperCase(Locale.ROOT);
            String operationType = readOperationType(annotation);
            boolean requiresConfirmation = readRequiresConfirmation(annotation);
            Set<String> declaredEndpoints = readApiEndpoints(annotation);

            if (!expected.toolName().equals(actualToolName)) {
                violations.add(format(path, "TOOL_NAME_MISMATCH",
                    "expected=" + expected.toolName() + ", actual=" + actualToolName));
            }
            if (!"GET".equals(declaredMethod)) {
                violations.add(format(path, milestone + "_METHOD_NOT_GET",
                    "tool=" + expected.toolName() + ", actualMethod=" + declaredMethod));
            }
            if (!expectedOperationType.equals(operationType)) {
                violations.add(format(path, milestone + "_OPERATION_MISMATCH",
                    "tool=" + expected.toolName() + ", expectedOperationType=" + expectedOperationType
                        + ", actualOperationType=" + operationType));
            }
            if (requiresConfirmation != expectedRequiresConfirmation) {
                violations.add(format(path, milestone + "_CONFIRMATION_POLICY_MISMATCH",
                    "tool=" + expected.toolName() + ", expectedRequiresConfirmation=" + expectedRequiresConfirmation
                        + ", actualRequiresConfirmation=" + requiresConfirmation));
            }
            Set<String> actualEndpoints = declaredEndpoints.stream()
                .filter(endpoint -> endpoint != null && !endpoint.isBlank())
                .collect(Collectors.toSet());
            Set<String> expectedEndpoints = expected.endpoint().isBlank()
                ? Set.of()
                : Set.of(expected.endpoint());
            if (!actualEndpoints.equals(expectedEndpoints)) {
                violations.add(format(path, milestone + "_ENDPOINT_MISMATCH",
                    "tool=" + expected.toolName() + ", expected=[" + expected.endpoint()
                        + "], actual=" + declaredEndpoints));
            }
        } catch (IOException e) {
            violations.add(format(path, "READ_FILE_FAILED", "读取 endpoint 白名单 Tool 失败: " + e.getMessage()));
        }
    }

    /** endpoint 精确白名单条目。 */
    private record ExpectedEndpoint(String fileName, String toolName, String endpoint) {
    }

    /** 高风险 endpoint 精确白名单条目。 */
    private record ExpectedRiskEndpoint(String fileName, String toolName, String method,
                                        String operationType, String endpoint) {
    }

    /**
     * 校验人工 Review 后的高风险写入/删除/动作 endpoint 精确白名单。
     *
     * <p>M5.19 的核心不是“能不能调用成功”，而是所有真实 POST/DELETE Tool 都必须
     * 在进入 MCP 或外部 Agent 暴露前拥有可审计的业务风险语义，并强制 requiresConfirmation=true。</p>
     */
    private void verifyExpectedRiskEndpoint(ExpectedRiskEndpoint expected, String milestone,
                                            List<String> violations) {
        Path path = TOOL_IMPL_DIR.resolve(expected.fileName());
        try {
            String source = Files.readString(path);
            Matcher mappingMatcher = MAPPING_PATTERN.matcher(source);
            if (!mappingMatcher.find()) {
                violations.add(format(path, "MISSING_ATLAS_TOOL_MAPPING", "tool=" + expected.toolName()));
                return;
            }

            String annotation = mappingMatcher.group(1);
            String actualToolName = readStringAttribute(annotation, "name");
            String declaredMethod = readStringAttribute(annotation, "httpMethod").toUpperCase(Locale.ROOT);
            String operationType = readOperationType(annotation);
            boolean requiresConfirmation = readRequiresConfirmation(annotation);
            Set<String> declaredEndpoints = readApiEndpoints(annotation);

            if (!expected.toolName().equals(actualToolName)) {
                violations.add(format(path, "TOOL_NAME_MISMATCH",
                    "expected=" + expected.toolName() + ", actual=" + actualToolName));
            }
            if (!expected.method().equals(declaredMethod)) {
                violations.add(format(path, milestone + "_METHOD_MISMATCH",
                    "tool=" + expected.toolName() + ", expectedMethod=" + expected.method()
                        + ", actualMethod=" + declaredMethod));
            }
            if (!expected.operationType().equals(operationType)) {
                violations.add(format(path, milestone + "_OPERATION_MISMATCH",
                    "tool=" + expected.toolName() + ", expectedOperationType=" + expected.operationType()
                        + ", actualOperationType=" + operationType));
            }
            if (!requiresConfirmation) {
                violations.add(format(path, milestone + "_MISSING_CONFIRMATION",
                    "tool=" + expected.toolName() + ", operationType=" + operationType
                        + ", suggestion=高风险 Tool 必须 requiresConfirmation=true"));
            }
            Set<String> actualEndpoints = declaredEndpoints.stream()
                .filter(endpoint -> endpoint != null && !endpoint.isBlank())
                .collect(Collectors.toSet());
            Set<String> expectedEndpoints = expected.endpoint().isBlank()
                ? Set.of()
                : Set.of(expected.endpoint());
            if (!actualEndpoints.equals(expectedEndpoints)) {
                violations.add(format(path, milestone + "_ENDPOINT_MISMATCH",
                    "tool=" + expected.toolName() + ", expected=[" + expected.endpoint()
                        + "], actual=" + declaredEndpoints));
            }
        } catch (IOException e) {
            violations.add(format(path, "READ_FILE_FAILED", "读取高风险 endpoint 白名单 Tool 失败: " + e.getMessage()));
        }
    }

    /**
     * 读取当前 Tool 可见的 KubeManagerHttpClient 字段变量名。
     *
     * <p>M5.14 加固点：当前主干多数 Tool 仍在子类里声明 {@code KubeManagerHttpClient httpClient}，
     * 但后续如果把 HTTP Client 上收到 {@code BaseTool}，子类源码中就不再出现字段声明。
     * 本方法会在确认子类继承 {@code BaseTool} 后，额外合并基类中的 KubeManagerHttpClient 字段，
     * 避免契约测试误判“声明了 GET 但没有真实 HTTP 调用”。</p>
     */
    private Set<String> readVisibleClientFieldNames(String source) throws IOException {
        Set<String> names = new LinkedHashSet<>(readClientFieldNames(source));
        if (extendsBaseTool(source) && Files.exists(BASE_TOOL_FILE)) {
            names.addAll(readClientFieldNames(Files.readString(BASE_TOOL_FILE)));
        }
        return names;
    }

    /**
     * 判断当前源码是否为继承 BaseTool 的 Tool 子类。
     */
    private boolean extendsBaseTool(String source) {
        return EXTENDS_BASE_TOOL_PATTERN.matcher(source).find();
    }

    /**
     * 读取 KubeManagerHttpClient 字段变量名，确保不会误把其他对象的 get/post/delete 当成 HTTP 调用。
     */
    private Set<String> readClientFieldNames(String source) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = CLIENT_FIELD_PATTERN.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * 读取源码中真实调用的 KubeManagerHttpClient HTTP 方法集合。
     */
    private Set<String> readActualHttpMethods(String source, Set<String> clientFieldNames) {
        Set<String> methods = new LinkedHashSet<>();
        for (String fieldName : clientFieldNames) {
            Pattern callPattern = Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\.(get|post|delete|put|patch)\\s*\\(");
            Matcher matcher = callPattern.matcher(source);
            while (matcher.find()) {
                methods.add(matcher.group(1).toUpperCase(Locale.ROOT));
            }
        }
        return methods;
    }

    private String readStringAttribute(String annotation, String attributeName) {
        Pattern pattern = Pattern.compile(String.format(STRING_ATTRIBUTE_PATTERN_TEMPLATE.pattern(), attributeName));
        Matcher matcher = pattern.matcher(annotation);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private Set<String> readApiEndpoints(String annotation) {
        Matcher matcher = API_ENDPOINTS_PATTERN.matcher(annotation);
        if (!matcher.find()) {
            return Set.of();
        }
        Set<String> endpoints = new LinkedHashSet<>();
        Arrays.stream(matcher.group(1).split(","))
            .map(String::strip)
            .map(value -> value.replace("\"", ""))
            .filter(value -> !value.isBlank())
            .forEach(endpoints::add);
        return endpoints;
    }

    private String readOperationType(String annotation) {
        Matcher matcher = OPERATION_TYPE_PATTERN.matcher(annotation);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean readRequiresConfirmation(String annotation) {
        Matcher matcher = REQUIRES_CONFIRMATION_PATTERN.matcher(annotation);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }

    private String format(Path path, String type, String detail) {
        return "[" + type + "] file=" + path + ", " + detail;
    }
}
