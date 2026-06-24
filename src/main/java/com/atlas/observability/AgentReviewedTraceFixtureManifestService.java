package com.atlas.observability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描 reviewed redacted trace fixture 文件的只读 manifest 服务。
 *
 * <p>中文说明：上一层 {@link AgentReviewedTraceFixtureIntakeContractService} 只定义 fixture 应该长什么样；
 * 本服务继续向前一步，读取 classpath 中约定的 fixture JSON 目录，告诉前端和人审者当前仓库是否已经
 * 放入可审查 fixture 文件。输入只来自 repo/classpath 文件，不来自请求体、LLM 或前端参数。</p>
 *
 * <p>安全边界：这是 manifest-only / read-only / classpath-scan-only 服务，不上传 fixture、不接收 caller
 * traceId、不修改 {@code eval-trace-sets.json}，不运行 eval/replay，不调用 Tool/MCP/LLM/RAG/kube-manager，
 * 不写 audit/memory，也不打开 CI blocking、release authority 或 Phase 2 NIM/HPC/Slurm/BCM 权力。</p>
 */
@Service
public class AgentReviewedTraceFixtureManifestService {

    static final String FIXTURE_RESOURCE_PATTERN = "classpath*:observability/reviewed-trace-fixtures/*.json";

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;
    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourcePatternResolver;
    private final Clock clock;

    public AgentReviewedTraceFixtureManifestService(AgentEvalTraceSetCatalogService traceSetCatalogService,
                                                    ObjectMapper objectMapper) {
        this(
            traceSetCatalogService,
            objectMapper,
            new PathMatchingResourcePatternResolver(),
            Clock.systemUTC()
        );
    }

    AgentReviewedTraceFixtureManifestService(AgentEvalTraceSetCatalogService traceSetCatalogService,
                                             ObjectMapper objectMapper,
                                             ResourcePatternResolver resourcePatternResolver,
                                             Clock clock) {
        this.traceSetCatalogService = traceSetCatalogService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.resourcePatternResolver = resourcePatternResolver != null
            ? resourcePatternResolver
            : new PathMatchingResourcePatternResolver();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * 返回 fixture manifest。
     *
     * <p>中文说明：这里会读取已经随代码提交的 fixture JSON 文件，并和 trace-set catalog 做覆盖关系比对。
     * 即使 fixture 文件齐全，本方法也只返回可审查 read model，不会把 traceId 写入目录。</p>
     */
    public AgentReviewedTraceFixtureManifestResponse manifest() {
        return AgentReviewedTraceFixtureManifestResponse.of(
            Instant.now(clock),
            traceSetCatalogService.catalog(),
            loadFixturePayloads(),
            FIXTURE_RESOURCE_PATTERN
        );
    }

    private List<Map<String, Object>> loadFixturePayloads() {
        try {
            Resource[] resources = resourcePatternResolver.getResources(FIXTURE_RESOURCE_PATTERN);
            return Arrays.stream(resources)
                .sorted(Comparator.comparing(this::resourceName))
                .map(this::readFixturePayload)
                .toList();
        } catch (IOException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("fixtureResource", FIXTURE_RESOURCE_PATTERN);
            error.put("parseError", "fixture-resource-scan-failed");
            return List.of(Map.copyOf(error));
        }
    }

    private Map<String, Object> readFixturePayload(Resource resource) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fixtureResource", resourceName(resource));
        try (InputStream input = resource.getInputStream()) {
            Map<String, Object> parsed = objectMapper.readValue(input, new TypeReference<>() {
            });
            if (parsed != null) {
                payload.putAll(parsed);
            }
        } catch (IOException | RuntimeException e) {
            payload.put("parseError", "fixture-json-parse-failed");
        }
        return immutablePayload(payload);
    }

    private String resourceName(Resource resource) {
        try {
            String filename = resource != null ? resource.getFilename() : null;
            return filename != null && !filename.isBlank() ? filename : String.valueOf(resource);
        } catch (RuntimeException e) {
            return "unknown-fixture-resource";
        }
    }

    private Map<String, Object> immutablePayload(Map<String, Object> payload) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                safe.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(safe);
    }
}
