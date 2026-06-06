package com.atlas.tool.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Helm Release 写操作请求体白名单构造器。
 *
 * <p>成熟 kube-manager 的 install/upgrade/rollback 接口分别绑定 InstallBodyDTO、UpgradeBodyDTO、
 * RollbackBodyDTO。Agent 输入中会混有 orgId、token、approved 等服务端上下文，不能全量透传；
 * 因此这里只复制后端 DTO 明确允许的字段，并保留 JsonProperty 下划线命名。</p>
 */
final class HelmReleaseBodyBuilder {

    private static final Set<String> INSTALL_BODY_FIELDS = Set.of(
        "dry_run", "disable_hooks", "wait", "devel", "description", "atomic", "skip_crds",
        "sub_notes", "create_namespace", "dependency_update", "values", "set", "set_string",
        "ca_file", "cert_file", "key_file", "insecure_skip_verify", "keyring", "password",
        "repo", "username", "verify", "version", "locations", "useNodePort"
    );

    private static final Set<String> UPGRADE_BODY_FIELDS = Set.of(
        "dry_run", "disable_hooks", "wait", "devel", "description", "atomic", "skip_crds",
        "sub_notes", "create_namespace", "dependency_update", "values", "set", "set_string",
        "ca_file", "cert_file", "key_file", "insecure_skip_verify", "keyring", "password",
        "repo", "username", "verify", "version", "locations", "useNodePort", "force"
    );

    private static final Set<String> ROLLBACK_BODY_FIELDS = Set.of(
        "dry_run", "disable_hooks", "wait", "force", "recreate", "cleanup_on_fail", "history_max"
    );

    private HelmReleaseBodyBuilder() {
    }

    static Map<String, Object> installBody(Map<String, Object> params) {
        return bodyFrom(params, INSTALL_BODY_FIELDS);
    }

    static Map<String, Object> upgradeBody(Map<String, Object> params) {
        return bodyFrom(params, UPGRADE_BODY_FIELDS);
    }

    static Map<String, Object> rollbackBody(Map<String, Object> params) {
        return bodyFrom(params, ROLLBACK_BODY_FIELDS);
    }

    private static Map<String, Object> bodyFrom(Map<String, Object> params, Set<String> allowedFields) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (String field : allowedFields) {
            Object value = params.get(field);
            if (value != null) {
                body.put(field, value);
            }
        }
        return body;
    }
}
