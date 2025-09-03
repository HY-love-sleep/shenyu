package org.apache.shenyu.springboot.starter.plugin.sec.watermark;

import org.apache.shenyu.plugin.api.ShenyuPlugin;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.sec.mark.ContentMarkPlugin;
import org.apache.shenyu.plugin.sec.mark.handler.ContentMarkPluginDataHandler;
import org.apache.shenyu.plugin.sec.mark.WaterMarker;
import org.apache.shenyu.plugin.sec.mark.metrics.SecurityMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.codec.ServerCodecConfigurer;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/23 17:18
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = "shenyu.plugins.content-mark.enabled", havingValue = "true", matchIfMissing = true)
public class ContentMarkPluginConfiguration {

    // 监控相关Bean
    @Bean("watermarkMetricsCollector")
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(name = "watermarkMetricsCollector")
    public SecurityMetricsCollector watermarkMetricsCollector(MeterRegistry meterRegistry) {
        return new SecurityMetricsCollector(meterRegistry);
    }
    
    @Bean
    @ConditionalOnBean(name = "watermarkMetricsCollector")
    @ConditionalOnMissingBean
    public WaterMarker waterMarker(SecurityMetricsCollector watermarkMetricsCollector) {
        return new WaterMarker(watermarkMetricsCollector);
    }
    
    @Bean
    @ConditionalOnMissingBean(name = "watermarkMetricsCollector")
    public WaterMarker waterMarkerWithoutMetrics() {
        return new WaterMarker();
    }

    @Bean
    public ShenyuPlugin contentMarkPlugin(final ServerCodecConfigurer configurer) {
        return new ContentMarkPlugin(configurer.getReaders());
    }

    @Bean
    public PluginDataHandler contentMarkPluginDataHandler() {
        return new ContentMarkPluginDataHandler();
    }
}
