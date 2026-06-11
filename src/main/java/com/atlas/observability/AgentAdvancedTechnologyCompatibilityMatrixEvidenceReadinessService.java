package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the advanced technology evidence-readiness view from existing read models only.
 */
@Service
public class AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService {

    private final AgentAdvancedTechnologyCompatibilityMatrixService compatibilityMatrixService;
    private final AgentReviewedEvalTraceEvidenceService reviewedEvalTraceEvidenceService;
    private final AgentMemoryRagReviewedTraceEvidenceManifestService memoryRagReviewedTraceEvidenceManifestService;
    private final Clock clock;

    @Autowired
    public AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService(
        AgentAdvancedTechnologyCompatibilityMatrixService compatibilityMatrixService,
        AgentReviewedEvalTraceEvidenceService reviewedEvalTraceEvidenceService,
        AgentMemoryRagReviewedTraceEvidenceManifestService memoryRagReviewedTraceEvidenceManifestService
    ) {
        this(
            compatibilityMatrixService,
            reviewedEvalTraceEvidenceService,
            memoryRagReviewedTraceEvidenceManifestService,
            Clock.systemUTC()
        );
    }

    AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService(
        AgentAdvancedTechnologyCompatibilityMatrixService compatibilityMatrixService,
        AgentReviewedEvalTraceEvidenceService reviewedEvalTraceEvidenceService,
        AgentMemoryRagReviewedTraceEvidenceManifestService memoryRagReviewedTraceEvidenceManifestService,
        Clock clock
    ) {
        this.compatibilityMatrixService = compatibilityMatrixService;
        this.reviewedEvalTraceEvidenceService = reviewedEvalTraceEvidenceService;
        this.memoryRagReviewedTraceEvidenceManifestService = memoryRagReviewedTraceEvidenceManifestService;
        this.clock = clock;
    }

    public AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse readiness() {
        return AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.of(
            Instant.now(clock),
            compatibilityMatrixService.matrix(),
            reviewedEvalTraceEvidenceService.evidence(),
            memoryRagReviewedTraceEvidenceManifestService.manifest()
        );
    }
}
