package com.atlas.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSONL-backed redacted audit read model.
 *
 * <p>Phase 1 keeps the first durable store intentionally simple: append-only
 * JSONL plus a bounded reverse scan. The contract stays behind
 * {@link AgentAuditQueryService}, so a later database/search backend can replace
 * this class without changing controller semantics.</p>
 */
@Service
public class JsonlAgentAuditQueryService implements AgentAuditQueryService {

    private static final int DEFAULT_SCAN_LIMIT = 10_000;

    private final AgentAuditProperties properties;
    private final ObjectMapper objectMapper;
    private final Path path;

    public JsonlAgentAuditQueryService(AgentAuditProperties properties, ObjectMapper objectMapper) {
        this.properties = properties != null ? properties : new AgentAuditProperties();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.path = resolvePath(this.properties.getDurable().getPath());
    }

    public boolean available() {
        return properties.getDurable().isEnabled() && Files.isRegularFile(path);
    }

    @Override
    public AgentAuditQueryResponse findByAuditId(String auditId) {
        String normalizedAuditId = safeText(auditId);
        ScanResult scan = scan(DEFAULT_SCAN_LIMIT, event -> normalizedAuditId.equals(event.auditId()), 20);
        return AgentAuditQueryResponse.of(
            "auditId",
            normalizedAuditId,
            20,
            scan.truncated(),
            indexMetadata(),
            scan.events()
        );
    }

    @Override
    public AgentAuditQueryResponse findByTraceId(String traceId, int maxResults) {
        String normalizedTraceId = safeText(traceId);
        int boundedMaxResults = Math.max(1, Math.min(maxResults, 500));
        ScanResult scan = scan(
            DEFAULT_SCAN_LIMIT,
            event -> normalizedTraceId.equals(event.traceId()),
            boundedMaxResults
        );
        return AgentAuditQueryResponse.of(
            "traceId",
            normalizedTraceId,
            boundedMaxResults,
            scan.truncated(),
            indexMetadata(),
            scan.events()
        );
    }

    @Override
    public Map<String, Object> indexMetadata() {
        boolean available = available();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", "agent-audit-index.v1");
        metadata.put("backend", "jsonl-reverse-scan");
        metadata.put("storageType", "jsonl");
        metadata.put("durableRetention", properties.getDurable().isEnabled());
        metadata.put("available", available);
        metadata.put("lookupFields", List.of("auditId", "traceId"));
        metadata.put("scanDirection", "newest-first");
        metadata.put("maxScanRecords", DEFAULT_SCAN_LIMIT);
        metadata.put("pathConfigured", properties.getDurable().getPath() != null && !properties.getDurable().getPath().isBlank());
        metadata.put("containsRawPrincipal", false);
        metadata.put("containsRawReason", false);
        metadata.put("containsRawParameterValues", false);
        metadata.put("containsRawEndpoints", false);
        if (available) {
            metadata.put("sizeBytes", fileSize());
        }
        return metadata;
    }

    private ScanResult scan(int maxScanRecords, AuditEventPredicate predicate, int maxResults) {
        if (!available()) {
            return new ScanResult(List.of(), false);
        }
        List<AgentAuditQueryEvent> matches = new ArrayList<>();
        int scanned = 0;
        boolean truncated = false;
        for (String line : readLinesNewestFirst()) {
            if (scanned >= maxScanRecords) {
                truncated = true;
                break;
            }
            scanned++;
            AgentAuditQueryEvent event = parseLine(line);
            if (event == null || !predicate.matches(event)) {
                continue;
            }
            if (matches.size() >= maxResults) {
                truncated = true;
                break;
            }
            matches.add(event);
        }
        return new ScanResult(matches, truncated);
    }

    private List<String> readLinesNewestFirst() {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        } catch (IOException ex) {
            return List.of();
        }
        java.util.Collections.reverse(lines);
        return lines;
    }

    @SuppressWarnings("unchecked")
    private AgentAuditQueryEvent parseLine(String line) {
        try {
            Map<String, Object> record = objectMapper.readValue(line, Map.class);
            return new AgentAuditQueryEvent(
                safeText(record.get("auditId")),
                parseInstant(record.get("eventTime")),
                safeText(record.get("traceId")),
                safeText(record.get("intentId")),
                safeText(record.get("toolName")),
                safeText(record.get("source")),
                safeText(record.get("httpMethod")),
                safeText(record.get("operationType")),
                Boolean.TRUE.equals(record.get("requiresConfirmation")),
                safeText(record.get("outcome")),
                Boolean.TRUE.equals(record.get("executed")),
                Boolean.TRUE.equals(record.get("success")),
                safeInt(record.get("apiEndpointCount")),
                safeMap(record.get("reasonSummary")),
                safeMap(record.get("parameterSummary")),
                safeMap(record.get("telemetry"))
            );
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return null;
        }
    }

    private Instant parseInstant(Object value) {
        String text = safeText(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        return value instanceof Map<?, ?>
            ? Map.copyOf((Map<String, Object>) value)
            : Map.of();
    }

    private int safeInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(safeText(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private long fileSize() {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return 0;
        }
    }

    private Path resolvePath(String configuredPath) {
        String value = configuredPath != null && !configuredPath.isBlank()
            ? configuredPath
            : "target/agent-audit/agent-audit.jsonl";
        return Path.of(value).toAbsolutePath().normalize();
    }

    private String safeText(Object value) {
        return value != null ? value.toString() : "";
    }

    private interface AuditEventPredicate {
        boolean matches(AgentAuditQueryEvent event);
    }

    private record ScanResult(List<AgentAuditQueryEvent> events, boolean truncated) {
    }
}
