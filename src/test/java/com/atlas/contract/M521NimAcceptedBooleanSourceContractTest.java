package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.21 NIM accepted boolean 源码级安全契约。
 *
 * <p>本测试只读取源码，不启动 Spring，不调用 LLM，不访问 kube-manager，也不会执行任何真实写操作。
 * 目标是防止未来生产代码把 {@code releaseDecisionGateReportAccepted=true} 当作 release approval 或
 * {@code writePermitted=true} 的充分条件。</p>
 */
class M521NimAcceptedBooleanSourceContractTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path REQUIREMENT_SUPPORT = Path.of(
        "src/main/java/com/atlas/tool/impl/NimCreateStateMachineReleaseDecisionRequirementSupport.java");

    /**
     * 旧 accepted boolean 只能由合同壳写出，生产代码不得读取或单独消费它。
     */
    @Test
    void productionCode_shouldNotConsumeReleaseDecisionGateReportAcceptedStandalone() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(MAIN_JAVA)) {
            paths
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> scanForStandaloneAcceptedConsumption(path, violations));
        }

        assertThat(violations)
            .as("releaseDecisionGateReportAccepted 是兼容可读性字段，生产代码不得 get/containsKey/条件判断单独消费: %s",
                violations)
            .isEmpty();
    }

    /**
     * 合同壳本身必须继续输出非权威伴随字段，避免只留下旧 boolean。
     */
    @Test
    void requirementSupport_shouldDeclareAcceptedBooleanAsCompatibilityOnly() throws IOException {
        String source = read(REQUIREMENT_SUPPORT);
        int legacyFlagIndex = source.indexOf("result.put(\"releaseDecisionGateReportAccepted\", inputAccepted)");
        int compatibilityIndex = source.indexOf(
            "result.put(\"releaseDecisionGateReportAcceptedFieldIsCompatibilityOnly\", true)");
        int authoritativeIndex = source.indexOf(
            "result.put(\"releaseDecisionGateReportAcceptedIsAuthoritative\", false)");
        int standaloneIndex = source.indexOf(
            "result.put(\"releaseDecisionGateReportAcceptedStandaloneConsumptionAllowed\", false)");
        int companionIndex = source.indexOf(
            "result.put(\"releaseDecisionGateReportAcceptedRequiredCompanionSignals\", List.of(");

        assertThat(legacyFlagIndex).as("旧兼容 boolean 仍需存在，便于读者理解 input shape acceptance").isGreaterThanOrEqualTo(0);
        assertThat(compatibilityIndex).as("必须标记该字段仅用于兼容/可读性").isGreaterThan(legacyFlagIndex);
        assertThat(authoritativeIndex).as("必须明确该字段不是权威 release 信号").isGreaterThan(legacyFlagIndex);
        assertThat(standaloneIndex).as("必须禁止单独消费该字段").isGreaterThan(legacyFlagIndex);
        assertThat(companionIndex).as("必须列出 scope/digest/write companion signals").isGreaterThan(legacyFlagIndex);
        assertThat(source)
            .contains("releaseDecisionGateReportAcceptanceScope=")
            .contains("realStateMachineReleaseDecisionGateReportAccepted=false")
            .contains("releaseDecisionGateDigestVerified=false")
            .contains("releaseDecisionDigestVerified=false")
            .contains("stateMachineCanSetWritePermittedNow=false")
            .contains("fallbackToReleaseDecisionGateReportAcceptedAllowed")
            .contains("RELEASE_DECISION_GATE_REPORT_ACCEPTED_FLAG_NOT_AUTHORITATIVE")
            .contains("treating releaseDecisionGateReportAccepted=true as release approval");
    }

    private void scanForStandaloneAcceptedConsumption(Path path, List<String> violations) {
        try {
            String source = read(path);
            if (!source.contains("releaseDecisionGateReportAccepted")) {
                return;
            }
            for (String line : source.lines().toList()) {
                String trimmed = line.trim();
                if (!trimmed.contains("releaseDecisionGateReportAccepted")) {
                    continue;
                }
                if (allowedContractOutputLine(path, trimmed)) {
                    continue;
                }
                if (looksLikeStandaloneConsumption(trimmed)) {
                    violations.add(path + " :: " + trimmed);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to scan source file: " + path, ex);
        }
    }

    private boolean allowedContractOutputLine(Path path, String trimmed) {
        if (!path.endsWith(REQUIREMENT_SUPPORT)) {
            return false;
        }
        return trimmed.startsWith("result.put(\"releaseDecisionGateReportAccepted\"")
            || trimmed.startsWith("result.put(\"releaseDecisionGateReportAcceptedFieldIsCompatibilityOnly\"")
            || trimmed.startsWith("result.put(\"releaseDecisionGateReportAcceptedIsAuthoritative\"")
            || trimmed.startsWith("result.put(\"releaseDecisionGateReportAcceptedStandaloneConsumptionAllowed\"")
            || trimmed.startsWith("result.put(\"releaseDecisionGateReportAcceptedRequiredCompanionSignals\"")
            || trimmed.startsWith("migration.put(\"releaseDecisionGateReportAcceptedStandaloneConsumptionAllowed\"")
            || trimmed.contains("\"treating releaseDecisionGateReportAccepted=true as release approval\"");
    }

    private boolean looksLikeStandaloneConsumption(String trimmed) {
        return trimmed.contains(".get(\"releaseDecisionGateReportAccepted\")")
            || trimmed.contains(".containsKey(\"releaseDecisionGateReportAccepted\")")
            || trimmed.contains("Boolean.TRUE.equals(")
            || trimmed.contains("Boolean.FALSE.equals(")
            || trimmed.contains("releaseDecisionGateReportAccepted\") ==")
            || trimmed.contains("releaseDecisionGateReportAccepted\") !=");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
