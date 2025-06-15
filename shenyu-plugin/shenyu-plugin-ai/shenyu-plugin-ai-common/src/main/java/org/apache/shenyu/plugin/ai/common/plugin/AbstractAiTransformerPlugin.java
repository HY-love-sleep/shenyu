package org.apache.shenyu.plugin.ai.common.plugin;

import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.dto.convert.plugin.AiTransformerConfig;
import org.apache.shenyu.common.dto.convert.rule.AiTransformerHandle;
import org.apache.shenyu.common.utils.Singleton;
import org.apache.shenyu.plugin.ai.common.cache.ChatClientCache;
import org.apache.shenyu.plugin.ai.common.handler.AbstractAiTransformerPluginHandler;
import org.apache.shenyu.plugin.ai.common.spring.ai.registry.AiModelFactoryRegistry;
import org.apache.shenyu.plugin.ai.common.template.AbstractAiTransformerTemplate;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.plugin.base.AbstractShenyuPlugin;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.common.enums.AiModelProviderEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class AbstractAiTransformerPlugin extends AbstractShenyuPlugin {

    protected final List<HttpMessageReader<?>> messageReaders;
    private final AbstractAiTransformerPluginHandler handler;

    protected AbstractAiTransformerPlugin(final List<HttpMessageReader<?>> messageReaders,
                                          final AbstractAiTransformerPluginHandler handler) {
        this.messageReaders = messageReaders;
        this.handler = handler;
    }

    @Override
    protected Mono<Void> doExecute(final ServerWebExchange exchange,
                                   final ShenyuPluginChain chain,
                                   final SelectorData selector,
                                   final RuleData rule) {

        AiTransformerConfig config = Optional.ofNullable(Singleton.INST.get(AiTransformerConfig.class))
                .orElse(new AiTransformerConfig());

        AiTransformerHandle handle = AbstractAiTransformerPluginHandler.CACHED_HANDLE
                .get().obtainHandle(CacheKeyUtils.INST.getKey(rule));

        mergeHandleIntoConfig(handle, config);

        if (Stream.of(config.getBaseUrl(), config.getApiKey(), config.getProvider())
                .anyMatch(Objects::isNull)) {
            return chain.execute(exchange);
        }

        final ChatClient client = initOrGetClient(handle != null ? rule.getId() : "default", config);

        return createTemplate(config.getContent(), exchange)
                .assembleMessage()
                .flatMap(message ->
                        Mono.fromCallable(() -> client.prompt().user(message).call().content())
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(aiResponse ->
                                        handleAiResponse(exchange, aiResponse, messageReaders, chain)
                                )
                );
    }

    @Override
    public String named() {
        return handler.pluginNamed();
    }

    private ChatClient initOrGetClient(final String clientKey, final AiTransformerConfig config) {
        ChatClient existing = ChatClientCache.getInstance().getClient(clientKey);
        if (existing != null) {
            return existing;
        }
        AiModelFactoryRegistry registry = handler.getAiModelFactoryRegistry();
        ChatModel model = registry
                .getFactory(AiModelProviderEnum.getByName(config.getProvider()))
                .createAiModel(AbstractAiTransformerPluginHandler.convertConfig(config));
        return ChatClientCache.getInstance().init(clientKey, model);
    }

    /**
     * Merge non-empty fields in the rule to the global config
     */
    protected abstract void mergeHandleIntoConfig(AiTransformerHandle handle,
                                                  AiTransformerConfig config);

    /**
     * According to the exchange structure, construct the Request/Response template
     */
    protected abstract AbstractAiTransformerTemplate createTemplate(String userContent,
                                                                    ServerWebExchange exchange);

    /**
     * After receiving the AI response, how to modify the exchange and continue the chain
     */
    protected abstract Mono<Void> handleAiResponse(ServerWebExchange exchange,
                                                   String aiResponse,
                                                   List<HttpMessageReader<?>> readers,
                                                   ShenyuPluginChain chain);
}
