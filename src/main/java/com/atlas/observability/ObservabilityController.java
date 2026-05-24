package com.atlas.observability;

import com.atlas.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 可观测性控制器 — M5.20 最小诊断入口。
 *
 * <p>生产级指标仍通过 Spring Boot Actuator/Micrometer 暴露；本接口只返回 Atlas 维度的摘要快照，
 * 便于前端状态页或人工排障快速确认指标链路已经工作。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M5.20
 */
@RestController
@RequestMapping("/api/agent/observability")
public class ObservabilityController {

    private final AgentMetricsService metricsService;

    public ObservabilityController(AgentMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /** 查询 Agent 指标快照。 */
    @GetMapping("/snapshot")
    public ResponseEntity<ApiResponse<Map<String, Object>>> snapshot() {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.snapshot()));
    }
}
