package org.apache.shenyu.plugin.sec.mark;

import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.dto.convert.rule.ContentMarkHandle;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.plugin.base.AbstractShenyuPlugin;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.plugin.sec.mark.handler.ContentMarkPluginDataHandler;
import org.apache.shenyu.plugin.sec.mark.service.ContentMarkingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/21 17:52
 */
public class ContentMarkPlugin extends AbstractShenyuPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(ContentMarkPlugin.class);
    private final ContentMarkingService markingService;

    public ContentMarkPlugin(final ContentMarkingService markingService) {
        this.markingService = markingService;
    }

    @Override
    protected Mono<Void> doExecute(final ServerWebExchange exchange,
                                   final ShenyuPluginChain chain,
                                   final SelectorData selector,
                                   final RuleData rule) {
        ContentMarkHandle handle = ContentMarkPluginDataHandler.CACHED_HANDLE
                .get().obtainHandle(CacheKeyUtils.INST.getKey(rule));
        if (handle == null) {
            LOG.info("ContentMarkPlugin: handle not found, skip.");
            return chain.execute(exchange);
        }

        // wrap response
        ServerWebExchange mutated = exchange.mutate()
                .response(new ContentMarkResponseDecorator(exchange, markingService, handle))
                .build();
        return chain.execute(mutated);
    }

    @Override
    public String named() {
        return PluginEnum.CONTENT_MARK.getName();
    }

    @Override
    public int getOrder() {
        return PluginEnum.CONTENT_MARK.getCode();
    }

    /**
     * ResponseDecorator：stream output marked content
     */
    private static class ContentMarkResponseDecorator extends ServerHttpResponseDecorator {
        private final ServerWebExchange exchange;
        private final ContentMarkingService markingService;
        private final ContentMarkHandle handle;

        public ContentMarkResponseDecorator(ServerWebExchange exchange, ContentMarkingService service, ContentMarkHandle handle) {
            super(exchange.getResponse());
            this.exchange = exchange;
            this.markingService = service;
            this.handle = handle;
        }

        @Override
        public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
            DataBufferFactory bufferFactory = this.bufferFactory();
            Flux<DataBuffer> input = Flux.from(body);

            Flux<DataBuffer> marked = input.concatMap(chunk -> {
                String content = dataBufferToString(chunk);
                // 构造请求体
                ContentMarkingService.TextMarkRequest req = new ContentMarkingService.TextMarkRequest();
                // TODO: 填充请求参数，例 req.setContent(content); req.setAccessKey(handle.getAccessKey())...
                // 假设只传 content
                // req.setContent(content);

                // 调用服务
                return markingService.markText(req)
                        .map(resp -> {
                            // 这里 resp.getMarkedContent() 需你在 TextMarkResponse 实现
                            String markedContent = (resp == null || resp.toString() == null) ? content : resp.toString();
                            return stringToDataBuffer(markedContent, bufferFactory);
                        })
                        .onErrorResume(e -> {
                            LOG.error("ContentMarkPlugin: markText failed: {}", e.getMessage());
                            // 标记失败，返回原始内容
                            return Mono.just(stringToDataBuffer(content, bufferFactory));
                        });
            });

            return super.writeWith(marked);
        }

        // buffer to string
        private static String dataBufferToString(DataBuffer buffer) {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        // string to buffer
        private static DataBuffer stringToDataBuffer(String str, DataBufferFactory factory) {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = factory.wrap(bytes);
            return buffer;
        }
    }
}
