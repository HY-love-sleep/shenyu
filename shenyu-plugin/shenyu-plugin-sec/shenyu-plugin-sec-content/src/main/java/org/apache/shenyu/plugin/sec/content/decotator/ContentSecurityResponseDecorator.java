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

    /**
     * check chunked, streaming
     * @param body
     * @return
     */
    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        String contentType = getDelegate().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType == null ||
                (!contentType.toLowerCase().contains("json") && !contentType.toLowerCase().contains("event-stream"))) {
            return getDelegate().writeWith(body);
        }

        return getDelegate().writeWith(
                Flux.from(body)
                        .concatMap(dataBuffer -> {
                            // read chunk
                            byte[] chunkBytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(chunkBytes);
                            DataBufferUtils.release(dataBuffer);
                            String chunk = new String(chunkBytes, StandardCharsets.UTF_8);

                            // check each chunk
                            return ContentSecurityChecker
                                    .checkText(ContentSecurityChecker.SafetyCheckRequest.forContent(
                                            handle.getAccessKey(), handle.getAccessToken(), handle.getAppId(), chunk
                                    ), handle)
                                    .flatMap(resp -> {
                                        String cat = resp.getData() != null ? resp.getData().getContentCategory() : "";
                                        if ("违规".equals(cat) || "疑似".equals(cat)) {
                                            String errResp = "{\"code\":1401,\"msg\":\"返回内容违规\",\"detail\":\"检测结果：" + cat + "\"}";
                                            byte[] errBytes = errResp.getBytes(StandardCharsets.UTF_8);
                                            return Mono.just(getDelegate().bufferFactory().wrap(errBytes));
                                        } else {
                                            return Mono.just(getDelegate().bufferFactory().wrap(chunkBytes));
                                        }
                                    });
                        })
        );
    }


}
