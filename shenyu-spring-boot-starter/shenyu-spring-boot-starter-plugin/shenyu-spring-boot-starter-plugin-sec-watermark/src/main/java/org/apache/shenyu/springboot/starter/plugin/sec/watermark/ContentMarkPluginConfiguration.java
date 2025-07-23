package org.apache.shenyu.springboot.starter.plugin.sec.watermark;

import org.apache.shenyu.plugin.api.ShenyuPlugin;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.sec.mark.ContentMarkPlugin;
import org.apache.shenyu.plugin.sec.mark.handler.ContentMarkPluginDataHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/23 17:18
 */
@Configuration
@ConditionalOnProperty(value = "shenyu.plugins.content-mark.enabled", havingValue = "true", matchIfMissing = true)
public class ContentMarkPluginConfiguration {

    @Bean
    public ShenyuPlugin contentMarkPlugin(final ServerCodecConfigurer configurer) {
        return new ContentMarkPlugin(configurer.getReaders());
    }

    @Bean
    public PluginDataHandler contentMarkPluginDataHandler() {
        return new ContentMarkPluginDataHandler();
    }
}
