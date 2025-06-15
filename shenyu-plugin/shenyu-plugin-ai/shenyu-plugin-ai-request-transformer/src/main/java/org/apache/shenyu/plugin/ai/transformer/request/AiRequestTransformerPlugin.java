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

package org.apache.shenyu.plugin.ai.transformer.request;

import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.dto.convert.plugin.AiTransformerConfig;
import org.apache.shenyu.common.dto.convert.rule.AiTransformerHandle;
import org.apache.shenyu.common.enums.AiModelProviderEnum;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.common.utils.GsonUtils;
import org.apache.shenyu.common.utils.Singleton;
import org.apache.shenyu.plugin.ai.common.plugin.AbstractAiTransformerPlugin;
import org.apache.shenyu.plugin.ai.common.spring.ai.registry.AiModelFactoryRegistry;
import org.apache.shenyu.plugin.ai.common.cache.ChatClientCache;
import org.apache.shenyu.plugin.ai.transformer.request.handler.AiRequestTransformerPluginHandler;
import org.apache.shenyu.plugin.ai.transformer.request.template.AiRequestTransformerTemplate;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.plugin.base.AbstractShenyuPlugin;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.plugin.base.utils.ServerWebExchangeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * this is ai request transformer plugin.
 */
public class AiRequestTransformerPlugin extends AbstractAiTransformerPlugin {

    public AiRequestTransformerPlugin(final List<HttpMessageReader<?>> readers,
                                      final AiRequestTransformerPluginHandler handler) {
        super(readers, handler);
    }

    @Override
    public int getOrder() {
        return PluginEnum.AI_REQUEST_TRANSFORMER.getCode();
    }

    @Override
    public String named() {
        return PluginEnum.AI_REQUEST_TRANSFORMER.getName();
    }

    /**
     * 原来在 doExecute 中把 handle 合并到 config 的逻辑，放到这里。
     */
    @Override
    protected void mergeHandleIntoConfig(final AiTransformerHandle handle,
                                         final AiTransformerConfig config) {
        if (Objects.nonNull(handle)) {
            Optional.ofNullable(handle.getProvider()).ifPresent(config::setProvider);
            Optional.ofNullable(handle.getBaseUrl()).ifPresent(config::setBaseUrl);
            Optional.ofNullable(handle.getApiKey()).ifPresent(config::setApiKey);
            Optional.ofNullable(handle.getModel()).ifPresent(config::setModel);
            Optional.ofNullable(handle.getContent()).ifPresent(config::setContent);
        }
    }

    /**
     * 原来 new AiRequestTransformerTemplate(...) 的那行。
     */
    @Override
    protected org.apache.shenyu.plugin.ai.common.template.AbstractAiTransformerTemplate createTemplate(final String userContent,
                                                                                                       final ServerWebExchange exchange) {
        return new AiRequestTransformerTemplate(userContent, exchange.getRequest());
    }

    /**
     * 原来在 doExecute 最后处理 AI 返回并替换 Header/Body 的那段逻辑。
     */
    @Override
    protected Mono<Void> handleAiResponse(final ServerWebExchange exchange,
                                          final String aiResponse,
                                          final List<HttpMessageReader<?>> readers,
                                          final ShenyuPluginChain chain) {
        return convertHeader(exchange, aiResponse)
                .flatMap(e -> convertBody(e, readers, aiResponse))
                .flatMap(chain::execute);
    }

    // —— 以下 static 工具方法均照原版一模一样 —— //

    private static Mono<ServerWebExchange> convertBody(final ServerWebExchange exchange,
                                                       final List<HttpMessageReader<?>> readers,
                                                       final String aiResponse) {
        MediaType mediaType = exchange.getRequest().getHeaders().getContentType();
        if (MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
            return ServerWebExchangeUtils.rewriteRequestBody(exchange, readers, s ->
                    Mono.just(extractJsonBodyFromHttpResponse(aiResponse)));
        } else if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType)) {
            return ServerWebExchangeUtils.rewriteRequestBody(exchange, readers, s ->
                    Mono.just(convertFormData(aiResponse)));
        }
        return Mono.just(exchange);
    }

    private static Mono<ServerWebExchange> convertHeader(final ServerWebExchange exchange,
                                                         final String aiResponse) {
        HttpHeaders headers = extractHeadersFromAiResponse(aiResponse);
        ServerHttpRequest newReq = exchange.getRequest().mutate().headers(h -> {
            h.clear();
            h.putAll(headers);
        }).build();
        return Mono.just(exchange.mutate().request(newReq).build());
    }

    private static String extractJsonBodyFromHttpResponse(final String aiResponse) {
        if (Objects.isNull(aiResponse) || aiResponse.isEmpty()) {
            return null;
        }
        String[] lines = aiResponse.split("\\R");
        int idx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                idx = i;
                break;
            }
        }
        if (idx < 0 || idx == lines.length - 1) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = idx + 1; i < lines.length; i++) {
            sb.append(lines[i]).append('\n');
        }
        String body = sb.toString().trim();
        return body;
    }

    private static String convertFormData(final String aiResponse) {
        String json = extractJsonBodyFromHttpResponse(aiResponse);
        Map<String, Object> map = org.apache.shenyu.common.utils.GsonUtils.getInstance().convertToMap(json);
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, Object> en : map.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                sb.append(URLEncoder.encode(en.getKey(), "UTF-8"))
                        .append("=")
                        .append(URLEncoder.encode(String.valueOf(en.getValue()), "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    private static HttpHeaders extractHeadersFromAiResponse(final String aiResponse) {
        HttpHeaders headers = new HttpHeaders();
        try (BufferedReader reader = new BufferedReader(new StringReader(aiResponse))) {
            String line;
            boolean started = false;
            while ((line = reader.readLine()) != null) {
                if (!started) {
                    if (line.startsWith("HTTP/1.1") ||
                            line.matches("^(GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD)\\s.*\\sHTTP/1.1$")) {
                        started = true;
                    }
                } else {
                    if (line.trim().isEmpty()) {
                        break;
                    }
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        String name = line.substring(0, idx).trim();
                        String val = line.substring(idx + 1).trim();
                        headers.add(name, val);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return headers;
    }
}
