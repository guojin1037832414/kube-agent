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
 * 基于 JSONL 的脱敏审计只读模型。
 *
 * <p>中文说明：Phase 1 故意把第一版 durable store 做简单：追加式 JSONL + 有界反向扫描。
 * 查询合同藏在 {@link AgentAuditQueryService} 后面，未来换成数据库、搜索引擎或对象存储索引时，
 * Controller、Replay 和 Eval 不需要改变语义。</p>
 *
 * <p>安全边界：这是 admin-only 观测面的 redacted read model，不提供原文导出、不做全文搜索、
 * 不恢复 raw principal/raw reason/raw endpoints/raw parameter values，也不执行删除、轮转或 purge。
 * retention 当前只是 metadata-only，用来提醒学习项目下一阶段需要补合规生命周期。</p>
 */
@Service
public class JsonlAgentAuditQueryService implements AgentAuditQueryService {

    private final AgentAuditProperties properties;
    private final ObjectMapper objectMapper;
    private final Path path;

    public JsonlAgentAuditQueryService(AgentAuditProperties properties, ObjectMapper objectMapper) {
        this.properties = properties != null ? properties : new AgentAuditProperties();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.path = resolvePath(this.properties.getDurable().getPath());
    }

    /**
     * 判断 JSONL 读模型是否可用。
     *
     * <p>中文说明：只有启用 durable audit 且文件存在时才读取；不可用时上层可退回内存 ring buffer。</p>
     */
    public boolean available() {
        return properties.getDurable().isEnabled() && Files.isRegularFile(path);
    }

    @Override
    public AgentAuditQueryResponse findByAuditId(String auditId) {
        String normalizedAuditId = safeText(auditId);
        int maxPhaseRecords = boundedAuditIdMaxPhaseRecords();
        ScanResult scan = scan(boundedMaxScanRecords(), event -> normalizedAuditId.equals(event.auditId()), maxPhaseRecords);
        return AgentAuditQueryResponse.of(
            "auditId",
            normalizedAuditId,
            maxPhaseRecords,
            scan.truncated(),
            indexMetadata(),
            scan.events()
        );
    }

    @Override
    public AgentAuditQueryResponse findByTraceId(String traceId, int maxResults) {
        String normalizedTraceId = safeText(traceId);
        int boundedMaxResults = Math.max(1, Math.min(maxResults, boundedQueryMaxResults()));
        ScanResult scan = scan(
            boundedMaxScanRecords(),
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
    public AgentAuditQueryResponse recentEvents(int maxResults) {
        int boundedMaxResults = Math.max(1, Math.min(maxResults, boundedQueryMaxResults()));
        ScanResult scan = scan(
            boundedMaxScanRecords(),
            event -> true,
            boundedMaxResults
        );
        return AgentAuditQueryResponse.of(
            "recent",
            "newest-first",
            boundedMaxResults,
            scan.truncated(),
            indexMetadata(),
            scan.events()
        );
    }

    /**
     * 输出前端和排障需要的索引元信息。
     *
     * <p>安全边界：这里会明确声明 containsRaw* 均为 false，并暴露 scan/retention/export 的限制，
     * 避免使用者误以为当前 JSONL 已经具备完整合规存储能力。</p>
     */
    @Override
    public Map<String, Object> indexMetadata() {
        boolean available = available();
        AgentAuditProperties.Durable durable = properties.getDurable();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", "agent-audit-index.v1");
        metadata.put("backend", "jsonl-reverse-scan");
        metadata.put("storageType", "jsonl");
        metadata.put("durableRetention", durable.isEnabled());
        metadata.put("available", available);
        metadata.put("lookupFields", List.of("auditId", "traceId", "recent"));
        metadata.put("scanDirection", "newest-first");
        metadata.put("maxScanRecords", boundedMaxScanRecords());
        metadata.put("maxQueryResults", boundedQueryMaxResults());
        metadata.put("auditIdMaxPhaseRecords", boundedAuditIdMaxPhaseRecords());
        metadata.put("pathConfigured", durable.getPath() != null && !durable.getPath().isBlank());
        metadata.put("retention", retentionMetadata(durable));
        metadata.put("export", exportMetadata(durable));
        metadata.put("containsRawPrincipal", false);
        metadata.put("containsRawReason", false);
        metadata.put("containsRawParameterValues", false);
        metadata.put("containsRawEndpoints", false);
        if (available) {
            metadata.put("sizeBytes", fileSize());
        }
        return metadata;
    }

    private Map<String, Object> retentionMetadata(AgentAuditProperties.Durable durable) {
        return Map.of(
            "policyConfigured", true,
            "retentionDays", Math.max(1, durable.getRetentionDays()),
            "maxFileBytes", Math.max(1L, durable.getMaxFileBytes()),
            "enforcementMode", "metadata-only",
            "rotationImplemented", false,
            "purgeImplemented", false
        );
    }

    private Map<String, Object> exportMetadata(AgentAuditProperties.Durable durable) {
        String format = durable.getExportFormat() != null && !durable.getExportFormat().isBlank()
            ? durable.getExportFormat()
            : "jsonl-redacted";
        return Map.of(
            "enabled", durable.isExportEnabled(),
            "format", format,
            "directoryConfigured", durable.getExportDirectory() != null && !durable.getExportDirectory().isBlank(),
            "adminOnly", true,
            "redactedOnly", true,
            "downloadEndpointImplemented", false
        );
    }

    private int boundedMaxScanRecords() {
        return Math.max(1, Math.min(properties.getDurable().getQueryMaxScanRecords(), 100_000));
    }

    private int boundedQueryMaxResults() {
        return Math.max(1, Math.min(properties.getDurable().getQueryMaxResults(), 2_000));
    }

    private int boundedAuditIdMaxPhaseRecords() {
        return Math.max(1, Math.min(properties.getDurable().getAuditIdMaxPhaseRecords(), 100));
    }

    /**
     * 从最新记录开始做有界扫描。
     *
     * <p>中文说明：有界扫描保护本地文件不会因为审计量变大而拖垮管理接口；如果扫描被截断，
     * 响应会显式标记 truncated，让 eval 和前端知道证据覆盖不足。</p>
     */
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
    /**
     * 解析单行 durable audit 记录。
     *
     * <p>安全边界：只读取 JSONL 里已经脱敏的字段；解析失败直接跳过，不尝试从原始日志或外部系统补证据。</p>
     */
    private AgentAuditQueryEvent parseLine(String line) {
        try {
            Map<String, Object> record = objectMapper.readValue(line, Map.class);
            String outcome = safeText(record.get("outcome"));
            return new AgentAuditQueryEvent(
                safeText(record.get("auditId")),
                parseInstant(record.get("eventTime")),
                AgentAuditQueryEvent.recordPhase(safeText(record.get("recordPhase")), outcome),
                safeText(record.get("traceId")),
                safeText(record.get("intentId")),
                safeText(record.get("toolName")),
                safeText(record.get("source")),
                safeText(record.get("httpMethod")),
                safeText(record.get("operationType")),
                Boolean.TRUE.equals(record.get("requiresConfirmation")),
                outcome,
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
