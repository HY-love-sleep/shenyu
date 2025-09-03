package org.apache.shenyu.springboot.starter.plugin.sec.content;


import org.apache.shenyu.plugin.api.ShenyuPlugin;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.sec.content.ContentSecurityPlugin;
import org.apache.shenyu.plugin.sec.content.handler.ContentSecurityPluginDataHandler;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityCheckerFactory;
import org.apache.shenyu.plugin.sec.content.ContentSecurityService;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityChecker;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityCheckerZkrj;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityCheckerSm;
import org.apache.shenyu.plugin.sec.content.metrics.SecurityMetricsCollector;
import org.apache.shenyu.plugin.sec.content.metrics.HystrixMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.codec.ServerCodecConfigurer;
import java.util.List;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 17:01
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = "shenyu.plugins.content-security.enabled", havingValue = "true", matchIfMissing = true)
public class ContentSecurityPluginConfiguration {
    
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityPluginConfiguration.class);

    public ContentSecurityPluginConfiguration() {
        LOG.info("ContentSecurityPluginConfiguration initialized");
    }

    // 监控相关Bean
    @Bean("contentSecurityMetricsCollector")
    @ConditionalOnMissingBean(name = "contentSecurityMetricsCollector")
    public SecurityMetricsCollector contentSecurityMetricsCollector(MeterRegistry meterRegistry) {
        LOG.info("Creating contentSecurityMetricsCollector with MeterRegistry: {}", meterRegistry.getClass().getSimpleName());
        return new SecurityMetricsCollector(meterRegistry);
    }
    
    @Bean("contentSecurityHystrixMetricsCollector")
    @ConditionalOnBean(name = "contentSecurityMetricsCollector")
    @ConditionalOnMissingBean(name = "contentSecurityHystrixMetricsCollector")
    public HystrixMetricsCollector contentSecurityHystrixMetricsCollector(SecurityMetricsCollector contentSecurityMetricsCollector) {
        return new HystrixMetricsCollector(contentSecurityMetricsCollector);
    }

    @Bean
    @ConditionalOnBean(name = "contentSecurityMetricsCollector")
    public ContentSecurityCheckerZkrj contentSecurityCheckerZkrj(SecurityMetricsCollector contentSecurityMetricsCollector) {
        return new ContentSecurityCheckerZkrj(contentSecurityMetricsCollector);
    }
    
    @Bean
    @ConditionalOnMissingBean(name = "contentSecurityMetricsCollector")
    public ContentSecurityCheckerZkrj contentSecurityCheckerZkrjWithoutMetrics() {
        return new ContentSecurityCheckerZkrj();
    }

    @Bean
    @ConditionalOnBean(name = "contentSecurityMetricsCollector")
    public ContentSecurityCheckerSm contentSecurityCheckerSm(SecurityMetricsCollector contentSecurityMetricsCollector) {
        return new ContentSecurityCheckerSm(contentSecurityMetricsCollector);
    }
    
    @Bean
    @ConditionalOnMissingBean(name = "contentSecurityMetricsCollector")
    public ContentSecurityCheckerSm contentSecurityCheckerSmWithoutMetrics() {
        return new ContentSecurityCheckerSm();
    }

    @Bean
    public ContentSecurityCheckerFactory contentSecurityCheckerFactory(List<ContentSecurityChecker> checkers) {
        return new ContentSecurityCheckerFactory(checkers);
    }

    @Bean
    public ContentSecurityService contentSecurityService(ContentSecurityCheckerFactory factory) {
        return new ContentSecurityService(factory);
    }

    @Bean
    public ShenyuPlugin contentSecurityPlugin(final ServerCodecConfigurer configurer, ContentSecurityService service) {
        return new ContentSecurityPlugin(configurer.getReaders(), service);
    }

    @Bean
    public PluginDataHandler contentSecurityPluginDataHandler() {
        return new ContentSecurityPluginDataHandler();
    }
}
