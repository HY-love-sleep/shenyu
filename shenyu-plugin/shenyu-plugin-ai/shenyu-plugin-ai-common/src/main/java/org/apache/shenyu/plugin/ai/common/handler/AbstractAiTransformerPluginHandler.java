package org.apache.shenyu.plugin.ai.common.handler;

import org.apache.shenyu.common.dto.PluginData;
import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.convert.plugin.AiTransformerConfig;
import org.apache.shenyu.common.dto.convert.rule.AiTransformerHandle;
import org.apache.shenyu.common.enums.AiModelProviderEnum;
import org.apache.shenyu.common.utils.GsonUtils;
import org.apache.shenyu.common.utils.Singleton;
import org.apache.shenyu.plugin.ai.common.config.AiCommonConfig;
import org.apache.shenyu.plugin.ai.common.cache.ChatClientCache;
import org.apache.shenyu.plugin.ai.common.spring.ai.AiModelFactory;
import org.apache.shenyu.plugin.ai.common.spring.ai.registry.AiModelFactoryRegistry;
import org.apache.shenyu.plugin.base.cache.CommonHandleCache;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.base.utils.BeanHolder;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Abstract AI Transformer plugin processor, extracts the common logic.
 */
public abstract class AbstractAiTransformerPluginHandler implements PluginDataHandler {

    protected final AiModelFactoryRegistry aiModelFactoryRegistry;

    /** Universal rule cache */
    public static final Supplier<CommonHandleCache<String, AiTransformerHandle>> CACHED_HANDLE =
        new BeanHolder<>(CommonHandleCache::new);

    protected AbstractAiTransformerPluginHandler(final AiModelFactoryRegistry aiModelFactoryRegistry) {
        this.aiModelFactoryRegistry = aiModelFactoryRegistry;
    }

    public AiModelFactoryRegistry getAiModelFactoryRegistry() {
        return this.aiModelFactoryRegistry;
    }

    @Override
    public void handlerPlugin(final PluginData pluginData) {
        if (Objects.nonNull(pluginData) && pluginData.getEnabled()) {
            AiTransformerConfig cfg = GsonUtils.getInstance().fromJson(pluginData.getConfig(), AiTransformerConfig.class);
            if (cfg == null) {
                return;
            }
            AiModelFactory factory = aiModelFactoryRegistry.getFactory(
                    AiModelProviderEnum.getByName(cfg.getProvider()));
            // Initialize default client, key using "default"
            ChatClientCache.getInstance()
                          .init("default", factory.createAiModel(convertConfig(cfg)));
            // cache overall config
            Singleton.INST.single(AiTransformerConfig.class, cfg);
        }
    }

    @Override
    public void handlerRule(final RuleData ruleData) {
        Optional.ofNullable(ruleData.getHandle()).ifPresent(json -> {
            AiTransformerHandle handle = GsonUtils.getInstance().fromJson(json, AiTransformerHandle.class);
            CACHED_HANDLE.get().cachedHandle(CacheKeyUtils.INST.getKey(ruleData), handle);
        });
        ChatClientCache.getInstance().destroyClient(ruleData.getId());
    }

    @Override
    public void removeRule(final RuleData ruleData) {
        Optional.ofNullable(ruleData.getHandle()).ifPresent(json ->
            CACHED_HANDLE.get().removeHandle(CacheKeyUtils.INST.getKey(ruleData)));
        AiTransformerHandle handle = GsonUtils.getInstance().fromJson(ruleData.getHandle(), AiTransformerHandle.class);
        ChatClientCache.getInstance().destroyClient(ruleData.getId());
    }

    @Override
    public String pluginNamed() {
        return getPluginName();
    }

    /**
     * The subclass returns its corresponding PluginEnum name.
     */
    protected abstract String getPluginName();

    /**
     * Convert the general AiTransformerConfig to the underlying AiCommonConfig
     */
    public static AiCommonConfig convertConfig(final AiTransformerConfig cfg) {
        AiCommonConfig common = new AiCommonConfig();
        Optional.ofNullable(cfg.getProvider()).ifPresent(common::setProvider);
        Optional.ofNullable(cfg.getBaseUrl()).ifPresent(common::setBaseUrl);
        Optional.ofNullable(cfg.getApiKey()).ifPresent(common::setApiKey);
        Optional.ofNullable(cfg.getModel()).ifPresent(common::setModel);
        return common;
    }
}
