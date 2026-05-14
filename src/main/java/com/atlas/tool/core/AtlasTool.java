package com.atlas.tool.core;

import java.util.Map;

/**
 * Atlas Tool 通用接口。
 *
 * <p>所有 kube-manager API 调用工具均实现此接口。</p>
 */
public interface AtlasTool {

    /**
     * 执行 Tool 任务。
     *
     * @param params LLM 提取或用户填充的参数（已回填默认值）µap
     * @return 结果韆，反馈给 LLM
     */
    Map<String, Object> execute(Map<String, Object> params);
}
