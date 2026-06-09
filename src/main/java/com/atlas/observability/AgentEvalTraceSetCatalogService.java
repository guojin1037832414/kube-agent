package com.atlas.observability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads versioned golden/red-team trace set metadata for deterministic eval gates.
 */
@Service
public class AgentEvalTraceSetCatalogService {

    static final String CATALOG_RESOURCE = "observability/eval-trace-sets.json";
    static final String CATALOG_SOURCE = "classpath:" + CATALOG_RESOURCE;

    private final AgentEvalSuiteCatalogService evalSuiteCatalogService;
    private final ObjectMapper objectMapper;
    private final List<AgentEvalTraceSetDefinition> definitions;

    public AgentEvalTraceSetCatalogService(AgentEvalSuiteCatalogService evalSuiteCatalogService,
                                           ObjectMapper objectMapper) {
        this.evalSuiteCatalogService = evalSuiteCatalogService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.definitions = loadDefinitions();
    }

    public AgentEvalTraceSetCatalogResponse catalog() {
        return AgentEvalTraceSetCatalogResponse.of(CATALOG_SOURCE, definitions, privacyProof());
    }

    public Optional<AgentEvalTraceSetDefinition> findDefinition(String traceSetId) {
        String normalized = normalizeId(traceSetId);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return definitions.stream()
            .filter(definition -> normalized.equals(definition.id()))
            .findFirst();
    }

    public Optional<AgentEvalTraceSetGateArtifact> gate(String traceSetId, AgentEvalSuiteRequest request) {
        return findDefinition(traceSetId)
            .flatMap(definition -> gate(definition, request));
    }

    public AgentEvalTraceSetGateBundleArtifact gateBundle(AgentEvalSuiteRequest request) {
        List<AgentEvalTraceSetGateArtifact> gates = definitions.stream()
            .map(definition -> gate(definition, request)
                .orElseGet(() -> AgentEvalTraceSetGateArtifact.from(definition, null, request, CATALOG_SOURCE)))
            .toList();
        return AgentEvalTraceSetGateBundleArtifact.of(CATALOG_SOURCE, gates);
    }

    public Optional<AgentEvalTraceSetCurationReviewArtifact> curationReview(String traceSetId,
                                                                            AgentEvalSuiteRequest request) {
        return findDefinition(traceSetId)
            .flatMap(definition -> evalSuiteCatalogService.gate(definition.suiteId(), curationReviewRequest(request))
                .map(gate -> AgentEvalTraceSetCurationReviewArtifact.from(definition, gate, CATALOG_SOURCE)));
    }

    public Optional<AgentEvalTraceSetCatalogPatchProposalArtifact> catalogPatchProposal(String traceSetId,
                                                                                        AgentEvalSuiteRequest request) {
        return findDefinition(traceSetId)
            .flatMap(definition -> curationReview(definition.id(), request)
                .map(review -> AgentEvalTraceSetCatalogPatchProposalArtifact.from(
                    definition,
                    definitionIndex(definition.id()),
                    review,
                    CATALOG_SOURCE,
                    CATALOG_RESOURCE
                )));
    }

    private Optional<AgentEvalTraceSetGateArtifact> gate(AgentEvalTraceSetDefinition definition,
                                                        AgentEvalSuiteRequest request) {
        if (!evalSuiteCatalogService.runtimeExecutionAllowed(definition.suiteId())) {
            return Optional.of(AgentEvalTraceSetGateArtifact.runtimeDisabled(
                definition,
                request,
                CATALOG_SOURCE
            ));
        }
        if (!traceSetRuntimeExecutionAllowed(definition)) {
            return Optional.of(AgentEvalTraceSetGateArtifact.traceSetRuntimeDisabled(
                definition,
                request,
                CATALOG_SOURCE
            ));
        }
        AgentEvalSuiteRequest suiteRequest = new AgentEvalSuiteRequest(
            definition.traceIds(),
            request != null ? request.limit() : null,
            request != null ? request.minimumScore() : null,
            request != null ? request.failOnWarnings() : null
        );
        return evalSuiteCatalogService.gate(definition.suiteId(), suiteRequest)
            .map(suiteGate -> AgentEvalTraceSetGateArtifact.from(definition, suiteGate, request, CATALOG_SOURCE));
    }

    private boolean traceSetRuntimeExecutionAllowed(AgentEvalTraceSetDefinition definition) {
        Map<String, Object> policy = definition != null ? definition.curationPolicy() : Map.of();
        if (Boolean.FALSE.equals(policy.get("suiteRuntimeExecutionAllowed"))) {
            return false;
        }
        return !Boolean.TRUE.equals(policy.get("catalogOnlyUntilReviewed"))
            || (definition != null && !definition.traceIds().isEmpty());
    }

    private AgentEvalSuiteRequest curationReviewRequest(AgentEvalSuiteRequest request) {
        return new AgentEvalSuiteRequest(
            curationCandidateTraceIds(request != null ? request.traceIds() : null),
            request != null ? request.limit() : null,
            request != null ? request.minimumScore() : null,
            request != null ? request.failOnWarnings() : null
        );
    }

    private List<String> curationCandidateTraceIds(List<String> traceIds) {
        if (traceIds == null || traceIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> accepted = new LinkedHashSet<>();
        for (String traceId : traceIds) {
            String candidate = AgentTraceContext.safeCandidateOrBlank(traceId);
            if (!candidate.isBlank() && !AgentTraceContext.w3cTraceIdOrBlank(candidate).isBlank()) {
                accepted.add(candidate);
            }
        }
        return List.copyOf(accepted);
    }

    private int definitionIndex(String traceSetId) {
        String normalized = normalizeId(traceSetId);
        for (int i = 0; i < definitions.size(); i++) {
            if (normalized.equals(definitions.get(i).id())) {
                return i;
            }
        }
        return -1;
    }

    private List<AgentEvalTraceSetDefinition> loadDefinitions() {
        ClassPathResource resource = new ClassPathResource(CATALOG_RESOURCE);
        if (resource.exists()) {
            try (InputStream input = resource.getInputStream()) {
                List<AgentEvalTraceSetDefinition> loaded = objectMapper.readValue(
                    input,
                    new TypeReference<List<AgentEvalTraceSetDefinition>>() {
                    }
                );
                if (loaded != null && !loaded.isEmpty()) {
                    return List.copyOf(loaded);
                }
            } catch (IOException | RuntimeException ignored) {
                return builtInDefinitions();
            }
        }
        return builtInDefinitions();
    }

    private List<AgentEvalTraceSetDefinition> builtInDefinitions() {
        return List.of(
            definition(
                "phase1-core-golden",
                "Phase 1 Core Golden Trace Set",
                "Stable golden evidence anchors for ordinary kube-manager read, diagnosis, replay, and safety workflows.",
                "release-gate-strict",
                List.of("phase1", "golden", "ci", "release-gate")
            ),
            definition(
                "phase1-redaction-regression",
                "Phase 1 Redaction Regression Trace Set",
                "Curated privacy regression anchors proving replay/eval outputs stay redacted-only.",
                "redaction-regression",
                List.of("phase1", "privacy", "security", "redaction")
            ),
            definition(
                "phase1-high-risk-prewrite",
                "Phase 1 High Risk Prewrite Trace Set",
                "High-risk PRE_EXECUTION and FINAL evidence anchors for write-like Tool decisions.",
                "high-risk-prewrite",
                List.of("phase1", "high-risk", "durable-audit", "hitl")
            ),
            definition(
                "phase1-red-team-safety",
                "Phase 1 Red Team Safety Trace Set",
                "Adversarial safety anchors for prompt-injection, protected parameter, and policy-bypass regression gates.",
                "release-gate-strict",
                List.of("phase1", "red-team", "safety", "policy")
            )
        );
    }

    private AgentEvalTraceSetDefinition definition(String id,
                                                   String title,
                                                   String purpose,
                                                   String suiteId,
                                                   List<String> tags) {
        Map<String, Object> curationPolicy = new LinkedHashMap<>();
        curationPolicy.put("versionedSource", CATALOG_SOURCE);
        curationPolicy.put("requiresRealAuditCapture", true);
        curationPolicy.put("placeholderTraceIds", false);
        curationPolicy.put("failClosedWhenEmpty", true);
        curationPolicy.put("requestTraceIdOverrideAllowed", false);
        return AgentEvalTraceSetDefinition.of(
            id,
            title,
            purpose,
            "Phase 1 top-tier kube-manager Agent Core",
            suiteId,
            List.of(),
            List.of(
                "Populate traceIds only with persisted redacted replay captures.",
                "Do not add synthetic trace IDs that CI would treat as real evidence.",
                "No raw principal, organization, conversation, endpoint, reason, or parameter values."
            ),
            tags,
            curationPolicy,
            privacyProof()
        );
    }

    private Map<String, Object> privacyProof() {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", true);
        proof.put("containsRawPrincipal", false);
        proof.put("containsRawOrganization", false);
        proof.put("containsRawConversation", false);
        proof.put("containsRawEndpoints", false);
        proof.put("containsRawReason", false);
        proof.put("containsRawParameterValues", false);
        proof.put("deterministic", true);
        proof.put("llmUsed", false);
        proof.put("externalCalls", false);
        proof.put("toolExecution", false);
        proof.put("kubeManagerCalls", false);
        return proof;
    }

    private String normalizeId(String id) {
        return id != null ? id.trim().toLowerCase(java.util.Locale.ROOT) : "";
    }
}
