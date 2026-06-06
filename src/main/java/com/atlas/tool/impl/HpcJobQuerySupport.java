package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * HPC 作业准备数据 Tool 的路径参数校验工具。
 *
 * <p>HPC 控制面会把 clusterId、category 放到 URL path 中。这里先做白名单校验与编码，
 * 防止 LLM 生成的斜杠、上级目录或超长文本污染路径。</p>
 */
final class HpcJobQuerySupport {

    private HpcJobQuerySupport() {
    }

    static String positiveId(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            throw new AtlasToolValidationException(
                key + " 不能为空",
                "MISSING_HPC_ID",
                List.of("请先通过 cluster_query 查询可用 HPC 集群，再传入数字 ID")
            );
        }
        String value = String.valueOf(raw).trim();
        if (!value.matches("[1-9][0-9]*")) {
            throw new AtlasToolValidationException(
                key + " 仅支持正整数: " + value,
                "INVALID_HPC_ID",
                List.of("HPC 集群 ID 必须来自成熟后端返回的数字 ID，不能包含路径或脚本文本")
            );
        }
        return value;
    }

    static String categorySegment(Map<String, Object> params) {
        Object raw = params.get("category");
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            throw new AtlasToolValidationException(
                "category 不能为空",
                "MISSING_SBATCH_CATEGORY",
                List.of("请提供成熟后端支持的 sbatch 参数分类，例如 Basic Job Information")
            );
        }
        String value = String.valueOf(raw).trim();
        if (value.length() > 120 || !value.matches("[A-Za-z0-9 _.-]+")) {
            throw new AtlasToolValidationException(
                "category 只能包含字母、数字、空格、下划线、点和短横线: " + value,
                "INVALID_SBATCH_CATEGORY",
                List.of("不要在 category 中传入斜杠、反斜杠、命令或 URL 片段")
            );
        }
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
