package com.atlas.http;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 启用 Spring Retry — 必须在 @Configuration 类上加 @EnableRetry。
 *
 * <p>KubeManagerHttpClient 的方法上使用了 @Retryable 注解，
 * 需要此配置类激活重试机制。</p>
 */
@Configuration
@EnableRetry
public class HttpRetryConfig {
    // 仅作为 @EnableRetry 的载体，无需额外 Bean
}
