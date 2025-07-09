package org.apache.shenyu.plugin.sec.content.decotator;

import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.sec.content.ContentSecurityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
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
        LOG.info("ContentSecurityResponseDecorator.writeWith called!");
        // Empty bodies and non-flux types are supported
        if (body == null) {
            LOG.warn("writeWith called with null body!");
            return getDelegate().writeWith(Mono.empty());
        }
        // Aggregate the entire response body
        return DataBufferUtils.join(body)
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String originalBody = new String(bytes, StandardCharsets.UTF_8);
                    LOG.info("Original LLM response body: {}", originalBody);

                    // Construct request detection
                    ContentSecurityChecker.SafetyCheckRequest req = ContentSecurityChecker.SafetyCheckRequest.forContent(
                            handle.getAccessKey(), handle.getAccessToken(), handle.getAppId(), originalBody
                    );

                    return ContentSecurityChecker.checkText(req, handle)
                            .defaultIfEmpty(null)
                            .flatMap(resp -> {
                                String cat = (resp != null && resp.getData() != null) ? resp.getData().getContentCategory() : null;
                                String toWrite;
                                if ("违规".equals(cat) || "疑似".equals(cat)) {
                                    LOG.warn("检测到响应内容违规，cat={}，响应被拦截！", cat);
                                    toWrite = String.format(
                                            "{\"code\":1400,\"msg\":\"内容不符合规范\",\"detail\":\"检测结果：%s\"}", cat
                                    );
                                } else {
                                    toWrite = originalBody;
                                }
                                byte[] outBytes = toWrite.getBytes(StandardCharsets.UTF_8);
                                DataBuffer outBuffer = bufferFactory().wrap(outBytes);
                                LOG.info("响应最终输出内容：{}", toWrite);
                                return getDelegate().writeWith(Mono.just(outBuffer));
                            });
                });
    }
}
