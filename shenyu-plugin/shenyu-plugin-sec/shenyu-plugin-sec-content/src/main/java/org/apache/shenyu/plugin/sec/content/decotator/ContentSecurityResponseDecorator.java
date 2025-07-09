package org.apache.shenyu.plugin.sec.content.decotator;

import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.sec.content.ContentSecurityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.reactivestreams.Publisher;

import java.nio.charset.StandardCharsets;

/**
 * @author yHong
 * @since 2025/7/9 14:25
 * @version 1.0
 */
public class ContentSecurityResponseDecorator extends ServerHttpResponseDecorator {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityResponseDecorator.class);
    private final ServerWebExchange exchange;
    private final ContentSecurityHandle handle;

    public ContentSecurityResponseDecorator(ServerWebExchange exchange, ContentSecurityHandle handle) {
        super(exchange.getResponse());
        this.exchange = exchange;
        this.handle = handle;
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        String contentType = getDelegate().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType == null || !contentType.toLowerCase().contains("json")) {
            return getDelegate().writeWith(body);
        }

        // Aggregate the entire response body
        return Flux.from(body)
                .collectList()
                .flatMap(dataBuffers -> {
                    int totalSize = dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
                    byte[] allBytes = new byte[totalSize];
                    int offset = 0;
                    for (DataBuffer buffer : dataBuffers) {
                        int count = buffer.readableByteCount();
                        buffer.read(allBytes, offset, count);
                        offset += count;
                        DataBufferUtils.release(buffer);
                    }
                    String bodyStr = (allBytes.length == 0) ? "" : new String(allBytes, StandardCharsets.UTF_8);

                    if (bodyStr.isEmpty()) {
                        return getDelegate().writeWith(Mono.just(getDelegate().bufferFactory().wrap(allBytes)));
                    }

                    if (handle == null) {
                        return getDelegate().writeWith(Mono.just(getDelegate().bufferFactory().wrap(allBytes)));
                    }

                    return ContentSecurityChecker
                            .checkText(ContentSecurityChecker.SafetyCheckRequest.forContent(
                                    handle.getAccessKey(),
                                    handle.getAccessToken(),
                                    handle.getAppId(),
                                    bodyStr), handle)
                            .onErrorResume(e -> {
                                return Mono.empty();
                            })
                            .flatMap(resp -> {
                                String cat = (resp != null && resp.getData() != null) ? resp.getData().getContentCategory() : "";
                                if ("违规".equals(cat) || "疑似".equals(cat)) {
                                    String errResp = "{\"code\":1401,\"msg\":\"返回内容违规\",\"detail\":\"检测结果：" + cat + "\"}";
                                    byte[] bytes = errResp.getBytes(StandardCharsets.UTF_8);
                                    return getDelegate().writeWith(Mono.just(getDelegate().bufferFactory().wrap(bytes)));
                                } else {
                                    return getDelegate().writeWith(Mono.just(getDelegate().bufferFactory().wrap(allBytes)));
                                }
                            })
                            .switchIfEmpty(getDelegate().writeWith(Mono.just(getDelegate().bufferFactory().wrap(allBytes))));

                });
    }

}
