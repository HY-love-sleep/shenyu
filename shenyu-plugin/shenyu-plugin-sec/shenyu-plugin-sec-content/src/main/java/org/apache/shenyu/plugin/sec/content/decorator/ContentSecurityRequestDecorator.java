package org.apache.shenyu.plugin.sec.content.decorator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

/**
 * The RequestDecorator of the body is cached so that the body can be consumed repeatedly
 * @author yHong
 * @since 2025/7/9 15:55
 * @version 1.0
 */
public class ContentSecurityRequestDecorator extends ServerHttpRequestDecorator {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityRequestDecorator.class);

    private final byte[] bodyBytes;
    private final DataBufferFactory bufferFactory;

    public ContentSecurityRequestDecorator(ServerWebExchange exchange, byte[] bodyBytes) {
        super(exchange.getRequest());
        this.bodyBytes = bodyBytes;
        this.bufferFactory = exchange.getResponse().bufferFactory();
    }

    @Override
    public Flux<DataBuffer> getBody() {
        // Here, the DataBuffer is regenerated every time it is consumed to ensure that it is compatible with multiple consumptions
        LOG.debug("ContentSecurityRequestDecorator.getBody called, size={}", bodyBytes.length);
        DataBuffer buffer = bufferFactory.wrap(bodyBytes);
        return Flux.just(buffer);
    }

    public String getBodyAsString() {
        return new String(bodyBytes, StandardCharsets.UTF_8);
    }
}
