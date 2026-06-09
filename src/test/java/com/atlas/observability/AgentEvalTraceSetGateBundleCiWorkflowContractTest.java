package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level CI contract for publishing deterministic Agent eval artifacts.
 */
class AgentEvalTraceSetGateBundleCiWorkflowContractTest {

    @Test
    void backendQualityWorkflow_shouldUploadTraceSetGateBundleArtifactDirectory() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/backend-quality.yml"));
        String generatorTest = Files.readString(Path.of(
            "src/test/java/com/atlas/observability/AgentEvalTraceSetGateBundleArtifactTest.java"
        ));

        assertThat(generatorTest)
            .contains("Path.of(\"target\", \"agent-eval\", \"trace-set-gate-bundle.json\")")
            .contains("agent-eval-trace-set-gate-bundle.v1")
            .contains("ciBlockingEnabled");
        assertThat(workflow)
            .contains("Run unit and contract tests")
            .contains("mvn -B verify")
            .contains("Upload quality artifacts")
            .contains("target/agent-eval/");
    }
}
