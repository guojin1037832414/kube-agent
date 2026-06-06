package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.List;

/**
 * 文件/存储只读 Tool 的参数校验辅助。
 *
 * <p>文件路径、PVC 名称、挂载信息都会影响后续部署和训练任务的真实落点，不能把 LLM 传入的任意字段
 * 直接透传给 kube-manager。这里集中做最小白名单校验，避免每个 Tool 复制一份易漂移的参数处理逻辑。</p>
 */
final class FileStorageQuerySupport {

    private FileStorageQuerySupport() {
    }

    static String requiredTrimmedString(Object raw, String key, String businessName) {
        String value = optionalTrimmedString(raw);
        if (value == null) {
            throw new AtlasToolValidationException(
                "缺少必填参数: " + key + "（" + businessName + "）",
                "MISSING_FILE_STORAGE_PARAMETER",
                List.of("请提供 " + businessName + "，并确认它来自文件/存储列表或成熟前端可选项")
            );
        }
        return value;
    }

    private static String optionalTrimmedString(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isBlank() ? null : value;
    }
}
