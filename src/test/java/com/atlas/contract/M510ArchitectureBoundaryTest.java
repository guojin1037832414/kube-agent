package com.atlas.contract;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * M5.10 架构级边界契约测试。
 *
 * <p>本测试使用 ArchUnit 进行静态字节码/依赖分析，不使用 {@code @SpringBootTest}，
 * 不启动 Spring 容器，不注入 Bean，不调用 kube-manager，也不会执行任何真实删除/修改请求。</p>
 *
 * <p>职责划分：</p>
 * <ul>
 *   <li>M5.9 源码契约继续负责方法体语义：业务 get/post/delete 必须调用用户 Token 必填解析，
 *       且不得调用 sysadmin fallback 的 {@code resolveToken()}。</li>
 *   <li>M5.10 ArchUnit 契约负责结构级边界：哪些包/类可以依赖底层 HTTP 客户端，
 *       以及 Controller 是否绕过编排层直接依赖 Tool 实现。</li>
 * </ul>
 */
@AnalyzeClasses(
    packages = "com.atlas",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class M510ArchitectureBoundaryTest {

    /**
     * 规则一：除明确白名单外，生产代码不得直接依赖底层 HTTP 客户端。
     *
     * <p>允许例外：</p>
     * <ul>
     *   <li>{@code com.atlas.http..}：统一 kube-manager 数据面 HTTP 网关。</li>
     *   <li>{@code com.atlas.controller.AuthController}：登录代理入口，需要请求 kube-manager 登录接口。</li>
     *   <li>{@code com.atlas.intent.embedding.ModelDownloader}：外部 Embedding 模型下载，不访问 kube-manager 数据面。</li>
     * </ul>
     */
    @ArchTest
    static final ArchRule production_code_should_not_depend_on_direct_http_clients_outside_allowed_gateways =
        noClasses()
            .that()
            .resideOutsideOfPackages("com.atlas.http..")
            .and()
            .doNotHaveFullyQualifiedName("com.atlas.controller.AuthController")
            .and()
            .doNotHaveFullyQualifiedName("com.atlas.intent.embedding.ModelDownloader")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.client.RestClient")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
            .orShould()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.web.reactive.function.client..",
                "java.net.http..",
                "okhttp3..",
                "feign..",
                "org.springframework.cloud.openfeign..",
                "org.apache.hc.client5.http..",
                "org.apache.http.."
            )
            .because("白名单外生产类不能绕过统一 HTTP 网关直接访问外部或 kube-manager 数据面");

    /**
     * 规则二：Tool 层不得依赖底层 HTTP 客户端。
     *
     * <p>Tool 可以依赖 {@code KubeManagerHttpClient} 这类受控网关；禁止的是 RestClient/WebClient/JDK HTTP
     * 等底层客户端，避免未来新增 Tool 绕过 M5.8 用户 Token 必填边界。</p>
     */
    @ArchTest
    static final ArchRule tools_should_not_depend_on_direct_http_clients =
        noClasses()
            .that()
            .resideInAPackage("com.atlas.tool..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.client.RestClient")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
            .orShould()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.web.reactive.function.client..",
                "java.net.http..",
                "okhttp3..",
                "feign..",
                "org.springframework.cloud.openfeign..",
                "org.apache.hc.client5.http..",
                "org.apache.http.."
            )
            .because("Tool 层必须通过 KubeManagerHttpClient 等受控网关访问 kube-manager，不能直接持有底层 HTTP 客户端");

    /**
     * 规则三：Controller 不得直接依赖具体 Tool 实现。
     *
     * <p>Controller 应通过 Orchestrator、ReAct、ToolRegistry 或 Service 层触发能力，
     * 不能直接 new/注入 {@code com.atlas.tool.impl}，否则会绕过统一编排、权限和审计链路。</p>
     */
    @ArchTest
    static final ArchRule controllers_should_not_depend_on_tool_implementations =
        noClasses()
            .that()
            .resideInAPackage("com.atlas.controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.atlas.tool.impl..")
            .because("Controller 不能绕过编排层直接耦合具体 Tool 实现");
}
