package com.atlas.agent;

/**
 * Atlas 六大专业 Agent 常量枚举。
 *
 * <p>与 intents.yml 中的 {@code agent} 字段对齐。</p>
 *
 * @version 3.1.0
 */
public enum AtlasAgent {

    /** 查询 Agent — 节点/GPU/镜像/监控/概览 */
    QUERY("query"),
    /** 诊断 Agent — Pod 故障/日志分析 */
    DIAG("diag"),
    /** 部署 Agent — 实例/NIM/分布式创建、扩缩、删除 */
    DEPLOY("deploy"),
    /** 权限 Agent — 用户/角色/权限管理 */
    RBAC("rbac"),
    /** 存储 Agent — PVC/存储卷管理 */
    STORAGE("storage"),
    /** 网络 Agent — 带宽/Ingress/域名配置 */
    NETWORK("network");

    private final String code;

    AtlasAgent(String code) { this.code = code; }

    public String code() { return code; }

    /**
     * 按 code 反查枚举（忽略大小写）。
     */
    public static AtlasAgent fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (AtlasAgent a : values()) {
            if (a.code.equalsIgnoreCase(code)) return a;
        }
        return null;
    }
}
