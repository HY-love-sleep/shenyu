package org.apache.shenyu.plugin.sec.sensitive.handler;

import org.apache.shenyu.common.dto.PluginData;
import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.convert.rule.SensitiveSecurityHandle;
import org.apache.shenyu.common.utils.GsonUtils;
import org.apache.shenyu.plugin.base.cache.CommonHandleCache;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.base.utils.BeanHolder;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.plugin.sec.sensitive.redis.RedisConfigProperties;
import org.apache.shenyu.plugin.sec.sensitive.redis.RedisConnectionFactory;
import org.apache.shenyu.plugin.sec.sensitive.redis.ShenyuReactiveRedisTemplate;
import org.apache.shenyu.plugin.sec.sensitive.redis.serializer.ShenyuRedisSerializationContext;
import org.apache.shenyu.common.enums.PluginEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/7 10:38
 */
public class SensitiveSecurityPluginDataHandler implements PluginDataHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitiveSecurityPluginDataHandler.class);

    /** 缓存 Key = PluginEnum.SENSITIVE_SECURITY.getName() */
    public static final Supplier<CommonHandleCache<String, ReactiveRedisTemplate<String, String>>> REDIS_TEMPLATES =
            new BeanHolder<>(CommonHandleCache::new);

    /** 缓存每条规则的 Handle */
    public static final Supplier<CommonHandleCache<String, SensitiveSecurityHandle>> CACHED_HANDLE =
            new BeanHolder<>(CommonHandleCache::new);

    @Override
    public void handlerPlugin(final PluginData pluginData) {
        if (pluginData == null || !Boolean.TRUE.equals(pluginData.getEnabled())) {
            return;
        }
        RedisConfigProperties props = GsonUtils.getInstance()
                .fromJson(pluginData.getConfig(), RedisConfigProperties.class);
        if (Objects.isNull(props) || props.getHost() == null) {
            LOGGER.warn("SensitiveSecurityPlugin: RedisConfigProperties or url is null, skip init");
            return;
        }
        String pluginName = PluginEnum.SENSITIVE_SECURITY.getName();
        if (Objects.isNull(REDIS_TEMPLATES.get().obtainHandle(pluginName))) {
            RedisConnectionFactory factory = new RedisConnectionFactory(props);
            ReactiveRedisTemplate<String, String> template =
                    new ShenyuReactiveRedisTemplate<>(
                            factory.getLettuceConnectionFactory(),
                            ShenyuRedisSerializationContext.stringSerializationContext());
            REDIS_TEMPLATES.get().cachedHandle(pluginName, template);
            LOGGER.info("SensitiveSecurityPlugin: cached ReactiveRedisTemplate for {}", pluginName);
        }
    }

    @Override
    public void handlerRule(final RuleData ruleData) {
        Optional.ofNullable(ruleData.getHandle()).ifPresent(json -> {
            SensitiveSecurityHandle handle = GsonUtils.getInstance()
                    .fromJson(json, SensitiveSecurityHandle.class);
            if (Objects.isNull(handle)) {
                handle = SensitiveSecurityHandle.newDefaultInstance();
            }
            String key = CacheKeyUtils.INST.getKey(ruleData);
            CACHED_HANDLE.get().cachedHandle(key, handle);
        });
    }

    @Override
    public void removeRule(final RuleData ruleData) {
        CACHED_HANDLE.get().removeHandle(CacheKeyUtils.INST.getKey(ruleData));
    }

    @Override
    public String pluginNamed() {
        return PluginEnum.SENSITIVE_SECURITY.getName();
    }
}
