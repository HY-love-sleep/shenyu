/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shenyu.plugin.ai.maas.proxy;

import org.apache.shenyu.common.constant.Constants;
import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.dto.convert.rule.MaasProxyHandle;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.common.enums.RpcTypeEnum;
import org.apache.shenyu.common.utils.Singleton;
import org.apache.shenyu.plugin.ai.common.config.AiCommonConfig;
import org.apache.shenyu.plugin.ai.maas.proxy.handler.MaasProxyPluginHandler;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.plugin.api.context.ShenyuContext;
import org.apache.shenyu.plugin.base.AbstractShenyuPlugin;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.plugin.base.utils.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MAAS proxy plugin for Yuanjing Agent API.
 */
public class MaasProxyPlugin extends AbstractShenyuPlugin {

    private final List<HttpMessageReader<?>> messageReaders;

    public MaasProxyPlugin(final List<HttpMessageReader<?>> messageReaders) {
        this.messageReaders = messageReaders;
    }

    @Override
    protected Mono<Void> doExecute(final ServerWebExchange exchange, final ShenyuPluginChain chain, final SelectorData selector, final RuleData rule) {
        AiCommonConfig aiCommonConfig = Optional.ofNullable(Singleton.INST.get(AiCommonConfig.class)).orElseGet(AiCommonConfig::new);

        final ShenyuContext shenyuContext = exchange.getAttribute(Constants.CONTEXT);
        Objects.requireNonNull(shenyuContext);

        final MaasProxyHandle selectorHandle = MaasProxyPluginHandler.SELECTOR_CACHED_HANDLE.get()
                .obtainHandle(CacheKeyUtils.INST.getKey(selector.getId(), Constants.DEFAULT_RULE));

        if (Objects.nonNull(selectorHandle)) {
            AiCommonConfig tmp = new AiCommonConfig();
            tmp.setBaseUrl(Optional.ofNullable(selectorHandle.getBaseUrl()).orElse(aiCommonConfig.getBaseUrl()));
            tmp.setApiKey(Optional.ofNullable(selectorHandle.getApiKey()).orElse(aiCommonConfig.getApiKey()));
            tmp.setModel(Optional.ofNullable(selectorHandle.getAppId()).orElse(aiCommonConfig.getModel()));
            tmp.setTemperature(Optional.ofNullable(selectorHandle.getTemperature()).orElse(aiCommonConfig.getTemperature()));
            tmp.setMaxTokens(Optional.ofNullable(selectorHandle.getMaxTokens()).orElse(aiCommonConfig.getMaxTokens()));
            tmp.setStream(Optional.ofNullable(selectorHandle.getStream()).orElse(aiCommonConfig.getStream()));
            aiCommonConfig = tmp;
        }

        shenyuContext.setRpcType(RpcTypeEnum.AI.getName());
        exchange.getAttributes().put(Constants.CONTEXT, shenyuContext);

        exchange.getAttributes().put(Constants.HTTP_DOMAIN, aiCommonConfig.getBaseUrl());
        exchange.getAttributes().put(Constants.HTTP_TIME_OUT, 60 * 3000L);
        exchange.getAttributes().put(Constants.HTTP_RETRY, 0);

        // Convert headers to MAAS style
        final HttpHeaders newHeaders = new HttpHeaders();
        exchange.getRequest().getHeaders().forEach((key, valueList) -> newHeaders.put(key, new ArrayList<>(valueList)));
        if (!newHeaders.containsKey(HttpHeaders.AUTHORIZATION)) {
            newHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + aiCommonConfig.getApiKey());
        }

        final ServerWebExchange mutated = exchange.mutate()
                .request(builder -> builder.headers(h -> {
                    h.clear();
                    h.putAll(newHeaders);
                }))
                .build();

        final AiCommonConfig finalConfig = aiCommonConfig;
        return ServerWebExchangeUtils.rewriteRequestBody(mutated, messageReaders,
                originalBody -> Mono.just(MaasRequestBodyConverter.convert(originalBody, finalConfig)))
            .flatMap(chain::execute);
    }

    @Override
    public int getOrder() {
        return PluginEnum.AI_MAAS_PROXY.getCode();
    }

    @Override
    public String named() {
        return PluginEnum.AI_MAAS_PROXY.getName();
    }
}


