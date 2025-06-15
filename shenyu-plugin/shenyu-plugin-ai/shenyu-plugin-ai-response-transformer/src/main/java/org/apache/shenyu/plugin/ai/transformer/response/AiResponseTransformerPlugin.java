package org.apache.shenyu.plugin.ai.transformer.response;

import org.apache.shenyu.common.dto.convert.plugin.AiTransformerConfig;
import org.apache.shenyu.common.dto.convert.rule.AiTransformerHandle;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.plugin.ai.common.plugin.AbstractAiTransformerPlugin;
import org.apache.shenyu.plugin.ai.transformer.response.handler.AiResponseTransformerPluginHandler;
import org.apache.shenyu.plugin.ai.transformer.response.template.AiResponseTransformerTemplate;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.apache.shenyu.plugin.ai.transformer.response.template.AiResponseTransformerTemplate.ResponseParts;

/**
 * AI Response Transformer 插件：基于 AbstractAiTransformerPlugin 实现
 */
public class AiResponseTransformerPlugin extends AbstractAiTransformerPlugin {

    public AiResponseTransformerPlugin(final List<HttpMessageReader<?>> readers,
                                       final AiResponseTransformerPluginHandler handler) {
        super(readers, handler);
    }

    @Override
    public int getOrder() {
        return PluginEnum.AI_RESPONSE_TRANSFORMER.getCode();
    }

    @Override
    public String named() {
        return PluginEnum.AI_RESPONSE_TRANSFORMER.getName();
    }

    /**
     * 与 Request 插件对称，合并规则句柄到全局 config
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
     * 构造 Response 模板：这里需要先把下游返回的完整 HTTP/1.1 响应文本
     *（含状态行+headers+空行+body）保存在 exchange 属性 "original_full_response"。
     */
    @Override
    protected org.apache.shenyu.plugin.ai.common.template.AbstractAiTransformerTemplate createTemplate(final String userContent,
                                                                                                       final ServerWebExchange exchange) {
        String full = (String) exchange.getAttribute("original_full_response");
        return new AiResponseTransformerTemplate(userContent, full);
    }

    /**
     * AI 返回后，解析出 statusLine、headers、body 并写回响应
     */
    @Override
    protected Mono<Void> handleAiResponse(final ServerWebExchange exchange,
                                          final String aiResponse,
                                          final List<HttpMessageReader<?>> readers,
                                          final ShenyuPluginChain chain) {
        ResponseParts parts = AiResponseTransformerTemplate.parseResponse(aiResponse);
        // 1. 状态码
        String[] status = parts.getStatusLine().split(" ");
        int code = status.length >= 2
                ? Integer.parseInt(status[1])
                : HttpStatus.OK.value();
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(code));
        // 2. headers
        HttpHeaders h = exchange.getResponse().getHeaders();
        h.clear();
        h.putAll(parts.getHeaders());
        // 3. body
        DataBufferFactory fac = exchange.getResponse().bufferFactory();
        DataBuffer buf = fac.wrap(parts.getBody().getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf))
                .then(chain.execute(exchange));
    }
}
