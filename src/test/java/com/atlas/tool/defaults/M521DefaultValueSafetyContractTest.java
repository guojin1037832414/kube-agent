package com.atlas.tool.defaults;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.21-81 默认值注入全局安全契约。
 *
 * <p>默认值可以帮助表单补全，但不能生成认证、租户、HITL、审计、发布或真实写入控制字段。
 * 这个测试覆盖当前 defaults.yml 和底层 {@link IntentDefaults} 过滤入口。</p>
 */
class M521DefaultValueSafetyContractTest {

    private static final Path DEFAULTS_YML = Path.of("src/main/resources/defaults.yml");
    private static final Set<String> PROTECTED_EXAMPLES = Set.of(
        "accessToken",
        "authToken",
        "token",
        "Authorization",
        "headers",
        "clientSecret",
        "secretKey",
        "privateKey",
        "organizationId",
        "orgId",
        "targetOrgId",
        "userId",
        "tenant",
        "confirmed",
        "confirmation",
        "humanConfirmed",
        "hitlApproved",
        "hitlConfirmation",
        "safeToPost",
        "writeAllowed",
        "writePermitted",
        "writeExecutionAllowed",
        "realHttpExecutionAllowed",
        "releaseApproved",
        "releaseEligible",
        "releaseDecision",
        "validationResult",
        "auditReceipt",
        "writeBodyRebuildReport",
        "readinessExecutionReport",
        "nimCreateReleased",
        "codeReleaseSwitch",
        "sourceGuardInstalled",
        "trustedPolicySource",
        "backendQuerySourceAllowedForRelease",
        "sysLogBackfillSourceAllowed",
        "sysAdmin",
        "isSysOrg",
        "licenseValid",
        "nvaieLicenseValid",
        "fallbackTool",
        "deploymentId",
        "success",
        "executed",
        "authoritative"
    );

    private static final Set<String> SAFE_FORM_DEFAULTS = Set.of(
        "cpuLimits",
        "memLimits",
        "gpuPercentLimits",
        "replicas",
        "bandwidth",
        "enableWebSsh",
        "autoScaleSwitch",
        "workers",
        "strategy",
        "role",
        "status",
        "storageClass",
        "accessMode"
    );

    @Test
    void defaultsYml_shouldNotDeclareProtectedControlKeysForAnyIntent()
        throws IOException {
        Map<String, Object> root = readYaml(DEFAULTS_YML);
        Map<String, Object> defaults = objectMap(root.get("defaults"));

        List<String> violations = defaults.entrySet().stream()
            .flatMap(intent -> collectProtectedDefaults(intent.getKey(), intent.getValue()).stream())
            .toList();

        assertThat(violations)
            .as("defaults.yml must remain form defaults only")
            .isEmpty();
    }

    @Test
    void intentDefaults_shouldStripProtectedKeysBeforeRegistryCanApplyThem() {
        IntentDefaults defaults = new IntentDefaults("any_create", Map.of(
            "replicas", 1,
            "displayName", "draft",
            "nested", Map.of(
                "token", "secret-token",
                "safeName", "kept"
            ),
            "list", List.of(
                Map.of("Authorization", "Bearer secret", "label", "kept")
            ),
            "confirmed", true,
            "writePermitted", true,
            "releaseDecision", Map.of("accepted", true),
            "sysAdmin", true,
            "licenseValid", true
        ));

        assertThat(defaults.parameters())
            .containsEntry("replicas", 1)
            .containsEntry("displayName", "draft")
            .doesNotContainKeys(
                "confirmed",
                "writePermitted",
                "releaseDecision",
                "sysAdmin",
                "licenseValid"
            );
        assertThat(objectMap(defaults.getDefault("nested")))
            .containsEntry("safeName", "kept")
            .doesNotContainKey("token");
        List<Map<String, Object>> list = listOfMaps(defaults.getDefault("list"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0))
            .containsEntry("label", "kept")
            .doesNotContainKey("Authorization");
    }

    @Test
    void protectedKeyExamples_shouldAllBeRecognizedAfterNormalization() {
        assertThat(PROTECTED_EXAMPLES)
            .allSatisfy(key -> assertThat(DefaultValueSafety.isProtectedDefaultKey(key))
                .as(key + " must not be default-injectable")
                .isTrue());
    }

    @Test
    void currentBusinessFormDefaults_shouldRemainAllowed() throws IOException {
        Map<String, Object> root = readYaml(DEFAULTS_YML);
        Map<String, Object> defaults = objectMap(root.get("defaults"));

        for (Map.Entry<String, Object> intent : defaults.entrySet()) {
            IntentDefaults sanitized = new IntentDefaults(intent.getKey(), objectMap(intent.getValue()));
            assertThat(sanitized.parameters())
                .as(intent.getKey() + " should keep current safe form defaults")
                .isEqualTo(objectMap(intent.getValue()));
            assertThat(sanitized.parameters().keySet())
                .allSatisfy(key -> assertThat(SAFE_FORM_DEFAULTS)
                    .as(key + " should remain an explicitly reviewed form default")
                    .contains(key));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYaml(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            assertThat(loaded).as(path + " should parse as a YAML map").isInstanceOf(Map.class);
            return objectMap(loaded);
        }
    }

    private Map<String, Object> objectMap(Object raw) {
        assertThat(raw).isInstanceOf(Map.class);
        Map<?, ?> rawMap = (Map<?, ?>) raw;
        Map<String, Object> copy = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private List<Map<String, Object>> listOfMaps(Object raw) {
        assertThat(raw).isInstanceOf(List.class);
        return ((List<?>) raw).stream()
            .map(this::objectMap)
            .toList();
    }

    private List<String> collectProtectedDefaults(String prefix, Object raw) {
        List<String> violations = new ArrayList<>();
        if (raw instanceof Map<?, ?> rawMap) {
            rawMap.forEach((key, value) -> {
                String textKey = String.valueOf(key);
                String path = prefix + "." + textKey;
                if (DefaultValueSafety.isProtectedDefaultKey(textKey)) {
                    violations.add(path);
                }
                violations.addAll(collectProtectedDefaults(path, value));
            });
        } else if (raw instanceof List<?> rawList) {
            for (int i = 0; i < rawList.size(); i++) {
                violations.addAll(collectProtectedDefaults(prefix + "[" + i + "]", rawList.get(i)));
            }
        }
        return violations;
    }
}
