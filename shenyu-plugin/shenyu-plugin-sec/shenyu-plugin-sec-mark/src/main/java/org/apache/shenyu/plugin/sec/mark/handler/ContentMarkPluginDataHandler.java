package org.apache.shenyu.plugin.sec.mark.handler;

import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.convert.rule.ContentMarkHandle;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.common.utils.GsonUtils;
import org.apache.shenyu.plugin.base.cache.CommonHandleCache;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.base.utils.BeanHolder;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/21 17:34
 */
public class ContentMarkPluginDataHandler implements PluginDataHandler {

    public static final Supplier<CommonHandleCache<String, ContentMarkHandle>> CACHED_HANDLE =
            new BeanHolder<>(CommonHandleCache::new);

    @Override
    public void handlerRule(RuleData ruleData) {
        Optional.ofNullable(ruleData.getHandle()).ifPresent(handleJson -> {
            ContentMarkHandle contentMarkHandle = GsonUtils.getInstance().fromJson(handleJson, ContentMarkHandle.class);
            CACHED_HANDLE.get().cachedHandle(CacheKeyUtils.INST.getKey(ruleData), contentMarkHandle);
        });
    }

    @Override
    public void removeRule(RuleData ruleData) {
        Optional.ofNullable(ruleData.getHandle()).ifPresent(handleJson -> {
            CACHED_HANDLE.get().removeHandle(CacheKeyUtils.INST.getKey(ruleData));
        });
    }

    @Override
    public String pluginNamed() {
        return PluginEnum.CONTENT_MARK.getName();
    }
}
