package com.atlas.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 1 中文教学注释契约测试。
 *
 * <p>中文说明：这一批覆盖 HTTP 入口、认证桥接、当前主体解析、登录会话和 HITL 恢复入口。
 * 这些文件是学习 kube-agent 安全链路的第一站，因此源码中必须保留中文设计说明和安全边界说明，
 * 让后续开发者理解“请求如何带着身份进入 Agent，以及为什么高风险执行必须 fail-closed”。</p>
 */
class Batch1ChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.of(
        Path.of("src/main/java/com/atlas/auth/AgentSecurityConfig.java"),
        List.of("中文说明", "安全边界", "门禁地图", "Observability 暴露审计"),
        Path.of("src/main/java/com/atlas/auth/AuthTokenFilter.java"),
        List.of("中文说明", "安全边界", "会话事实进入 Spring Security", "请求开始和结束都必须清理"),
        Path.of("src/main/java/com/atlas/auth/AgentPrincipal.java"),
        List.of("中文说明", "安全边界", "只读快照", "不要把前端传入"),
        Path.of("src/main/java/com/atlas/auth/AgentPrincipalResolver.java"),
        List.of("中文说明", "安全边界", "身份来源的“适配器”", "不能从请求体"),
        Path.of("src/main/java/com/atlas/auth/UserPermissionContext.java"),
        List.of("中文说明", "安全边界", "ThreadLocal 必须成对", "服务端缓存的权限快照"),
        Path.of("src/main/java/com/atlas/controller/AuthController.java"),
        List.of("中文说明", "安全边界", "登录成功但无法确认组织上下文", "不保存密码"),
        Path.of("src/main/java/com/atlas/controller/HITLController.java"),
        List.of("中文说明", "安全边界", "确认 marker", "服务端 marker", "clarify 只是补充信息"),
        Path.of("src/main/java/com/atlas/hitl/HitlGuard.java"),
        List.of("中文说明", "安全边界", "fail-closed", "执行前最后一道人工确认闸门")
    );

    @Test
    void batch1SecurityEntryFiles_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey());

            assertThat(source)
                .as(entry.getKey() + " should keep Batch 1 Chinese teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
