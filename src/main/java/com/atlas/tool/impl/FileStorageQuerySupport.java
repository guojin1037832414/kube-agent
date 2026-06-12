package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.List;

/**
 * 文件/存储只读 Tool 的参数校验辅助。
 *
 * <p>中文说明：文件路径、PVC 名称、挂载信息都会影响后续部署和训练任务的真实落点。
 * 输入来自 LLM/Plan/前端参数 Map，输出会进入 kube-manager 文件/存储查询参数，因此不能把任意字段
 * 直接透传给 kube-manager。这里集中做最小白名单校验，避免每个 Tool 复制一份易漂移的参数处理逻辑。</p>
 *
 * <p>安全边界：当前方法只做“必填且非空”的最小收敛，不能证明文件路径、PVC 或挂载点属于当前用户。
 * 调用方仍必须依赖成熟后端列表返回、可信 token/orgId、kube-manager 权限和后续写操作门禁；
 * 也不能把 token、orgId、userId、sessionId、HITL、audit 或 release 字段作为文件/存储业务参数。</p>
 */
final class FileStorageQuerySupport {

    private FileStorageQuerySupport() {
    }

    /**
     * 读取必填字符串并做 trim。
     *
     * <p>中文说明：返回值仍只是业务候选参数，不是路径安全证明。对会进入 path/query/body 的字段，
     * 具体 Tool 还应结合后端白名单、ID 形状或可选项来源继续校验。</p>
     */
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

    /**
     * 将空白输入归一化为 null。
     *
     * <p>安全边界：这里不做 URL encode 或路径拼接，避免给调用方造成“已经安全可拼接”的错觉。</p>
     */
    private static String optionalTrimmedString(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isBlank() ? null : value;
    }
}
