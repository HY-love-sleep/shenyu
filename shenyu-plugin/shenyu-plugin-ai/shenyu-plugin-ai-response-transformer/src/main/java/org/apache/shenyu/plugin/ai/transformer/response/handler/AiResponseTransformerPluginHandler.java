package org.apache.shenyu.plugin.ai.transformer.response.handler;

import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.plugin.ai.common.handler.AbstractAiTransformerPluginHandler;
import org.apache.shenyu.plugin.ai.common.spring.ai.registry.AiModelFactoryRegistry;
import org.springframework.stereotype.Component;

@Component
public class AiResponseTransformerPluginHandler extends AbstractAiTransformerPluginHandler {

    public AiResponseTransformerPluginHandler(final AiModelFactoryRegistry registry) {
        super(registry);
    }

    @Override
    protected String getPluginName() {
        return PluginEnum.AI_RESPONSE_TRANSFORMER.getName();
    }
}
