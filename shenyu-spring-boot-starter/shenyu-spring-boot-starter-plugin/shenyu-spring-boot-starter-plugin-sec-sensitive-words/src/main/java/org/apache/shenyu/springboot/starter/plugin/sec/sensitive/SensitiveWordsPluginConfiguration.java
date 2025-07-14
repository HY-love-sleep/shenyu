package org.apache.shenyu.springboot.starter.plugin.sec.sensitive;

import org.apache.shenyu.plugin.api.ShenyuPlugin;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.sec.sensitive.SensitiveSecurityPlugin;
import org.apache.shenyu.plugin.sec.sensitive.handler.SensitiveSecurityPluginDataHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.codec.ServerCodecConfigurer;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/7 11:07
 */
@Configuration
@ConditionalOnProperty(value = "shenyu.plugins.sensitive-words.enabled",
        havingValue = "true", matchIfMissing = true)
public class SensitiveWordsPluginConfiguration {

    @Bean
    public ShenyuPlugin sensitiveSecurityPlugin(final ServerCodecConfigurer configurer) {
        return new SensitiveSecurityPlugin(configurer.getReaders());
    }

    /**
     * Subscribe and cache：
     * 1、plugin's RedisConfigProperties → ReactiveRedisTemplate
     * 2、rule's redisKey → SensitiveSecurityHandle
     */
    @Bean
    public PluginDataHandler sensitiveSecurityPluginDataHandler() {
        return new SensitiveSecurityPluginDataHandler();
    }
}
