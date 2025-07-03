package org.apache.shenyu.springboot.starter.plugin.sec.content;


import org.apache.shenyu.plugin.api.ShenyuPlugin;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.sec.content.ContentSecurityPlugin;
import org.apache.shenyu.plugin.sec.content.handler.ContentSecurityPluginDataHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 17:01
 */
@Configuration
@ConditionalOnProperty(value = "shenyu.plugins.content-security.enabled", havingValue = "true", matchIfMissing = true)
public class ContentSecurityPluginConfiguration {

    @Bean
    public ShenyuPlugin contentSecurityPlugin(final ServerCodecConfigurer configurer) {
        return new ContentSecurityPlugin(configurer.getReaders());
    }

    @Bean
    public PluginDataHandler contentSecurityPluginDataHandler() {
        return new ContentSecurityPluginDataHandler();
    }
}
