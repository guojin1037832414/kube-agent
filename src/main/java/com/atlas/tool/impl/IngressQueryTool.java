package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询域名/Ingress Tool。
 *
 * <p>意图映射: {@code intentId = "ingress_query"}</p>
 * <p>Agent归属: network | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "ingress_query",
    agent = "network",
    intentId = "ingress_query",
    description = "查询域名/Ingress"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class IngressQueryTool extends BaseTool {

    public IngressQueryTool() {
        super("ingress_query", "查询域名/Ingress");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[ingress_query] 执行查询域名/Ingress");
        List<Map<String, Object>> items = new ArrayList<>();
                items.add(Map.of("name", "web-ingress", "host", "app.example.com", "path", "/", "service", "svc-web", "tls", true));
                items.add(Map.of("name", "api-ingress", "host", "api.example.com", "path", "/api", "service", "svc-api", "tls", true));
        
                Map<String, Object> data = Map.of(
                    "total", items.size(),
                    "list", items
                );
        
                String summary = "查询完成, 共 " + items.size() + " 条记录";
                return AtlasToolResult.ok(summary, data);
    }
}
