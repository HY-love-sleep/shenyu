package org.apache.shenyu.plugin.sec.content.handler;

import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.base.cache.CommonHandleCache;
import org.apache.shenyu.plugin.base.utils.BeanHolder;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.common.utils.GsonUtils;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 15:09
 */
public class ContentSecurityPluginDataHandler implements PluginDataHandler {

    // 缓存规则ID到ContentSecurityHandle的映射，使用ShenYu提供的通用缓存容器
    public static final Supplier<CommonHandleCache<String, ContentSecurityHandle>> CACHED_HANDLE =
            new BeanHolder<>(CommonHandleCache::new);

    @Override
    public void handlerRule(final RuleData ruleData) {
        Optional.ofNullable(ruleData.getHandle()).ifPresent(handleJson -> {
            ContentSecurityHandle handle = GsonUtils.getInstance().fromJson(handleJson, ContentSecurityHandle.class);
            CACHED_HANDLE.get().cachedHandle(CacheKeyUtils.INST.getKey(ruleData), handle);
        });
    }

    @Override
    public void removeRule(final RuleData ruleData) {
        Optional.ofNullable(ruleData.getHandle()).ifPresent(handleJson -> {
            CACHED_HANDLE.get().removeHandle(CacheKeyUtils.INST.getKey(ruleData));
        });
    }

    // todo: use plugin enum
    @Override
    public String pluginNamed() {
        return PluginEnum.CONTENT_SECURITY.getName();
    }

}
