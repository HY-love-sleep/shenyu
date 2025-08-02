package org.apache.shenyu.plugin.sec.content.decorator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.base.decorator.GenericResponseDecorator;
import org.apache.shenyu.plugin.sec.content.ContentSecurityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.reactivestreams.Publisher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yHong
 * @since 2025/7/9 14:25
 * @version 1.0
 * // todo: Extracted into a generic decorator
 */
public class ContentSecurityResponseDecorator extends GenericResponseDecorator {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityResponseDecorator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    // append state
    private static final int BATCH_SIZE = 20; // The number of tokens processed per batch
    private static final int WINDOW_SIZE = 200; // Cumulative content threshold
    
    // global State Manager
    private static final ConcurrentHashMap<String, DecoratorState> STATE_MAP = new ConcurrentHashMap<>();
    
    private final ContentSecurityHandle handle;
    private final String stateKey;

    public ContentSecurityResponseDecorator(ServerWebExchange exchange, ContentSecurityHandle handle) {
        super(
                exchange.getResponse(),
                exchange,
                BATCH_SIZE,
                buildProcessAndOutput(handle, exchange)
        );
        this.handle = handle;
        this.stateKey = exchange.getRequest().getId();
        
        // init state
        STATE_MAP.put(stateKey, new DecoratorState(exchange, WINDOW_SIZE, BATCH_SIZE));
    }

    private static BiFunction<List<String>, List<byte[]>, Flux<DataBuffer>> buildProcessAndOutput(
            ContentSecurityHandle handle, ServerWebExchange exchange) {
        return (sseLines, rawSseBytes) -> {
            String stateKey = exchange.getRequest().getId();
            DecoratorState state = STATE_MAP.get(stateKey);
            
            if (state == null) {
                LOG.error("Decorator state not found for request: {}", stateKey);
                return Flux.fromIterable(rawSseBytes)
                        .map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes));
            }
            
            // if it has already been intercepted, return the error response directly.
            if (state.isBlocked.get()) {
                exchange.getAttributes().put("SEC_ERROR", true);
                String errResp = "{\"code\":1401,\"msg\":\"返回内容违规\",\"detail\":\"检测结果：违规\"}";
                byte[] errBytes = errResp.getBytes(StandardCharsets.UTF_8);
                return Flux.just(exchange.getResponse().bufferFactory().wrap(errBytes));
            }

            if (sseLines.isEmpty()) {
                return Flux.fromIterable(rawSseBytes)
                        .map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes));
            }

            int currentCount = state.processCounter.incrementAndGet();

            // get current batch content
            StringBuilder currentBatchContent = new StringBuilder();
            int validLines = 0;
            for (String line : sseLines) {
                String chunkStr = line.trim();
                if (chunkStr.startsWith("data:")) {
                    String json = chunkStr.substring(5).trim();
                    if ("[DONE]".equals(json)) {
                        continue;
                    } else {
                        String content = extractContentFromOpenAIChunk(chunkStr);
                        if (content != null) {
                            currentBatchContent.append(content);
                            validLines++;
                        }
                    }
                }
            }

            String batchContent = currentBatchContent.toString();
            
            // add batch content to buffer
            state.slidingWindowBuffer.addContent(batchContent);
            
            // if the cumulative buffer has not yet reached the detection threshold, output directly.
            if (!state.slidingWindowBuffer.shouldCheck()) {
                return Flux.fromIterable(rawSseBytes)
                        .map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes));
            }

            String accumulatedContent = state.slidingWindowBuffer.getWindowContent();

            LOG.info("送审内容: {}", accumulatedContent);
            
            return ContentSecurityChecker
                    .checkText(
                            ContentSecurityChecker.SafetyCheckRequest.forContent(
                                    handle.getAccessKey(), handle.getAccessToken(), handle.getAppId(), accumulatedContent
                            ),
                            handle)
                    .flatMapMany(resp -> {
                        String cat = resp.getData() != null ? resp.getData().getContentCategory() : "";
                        if ("违规".equals(cat) || "疑似".equals(cat)) {
                            exchange.getAttributes().put("SEC_ERROR", true);
                            state.isBlocked.set(true);
                            String errResp = "{\"code\":1401,\"msg\":\"返回内容违规\",\"detail\":\"检测结果：" + cat + "\"}";
                            byte[] errBytes = errResp.getBytes(StandardCharsets.UTF_8);
                            return Flux.just(exchange.getResponse().bufferFactory().wrap(errBytes));
                        } else {
                            return Flux.fromIterable(rawSseBytes)
                                    .map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes));
                        }
                    })
                    .doOnError(e -> LOG.error("Error in content security check", e));
        };
    }

    private static String extractContentFromOpenAIChunk(String chunkJson) {
        try {
            String json = chunkJson.trim();
            if (json.startsWith("data:")) {
                json = json.substring(5).trim();
            }
            if ("[DONE]".equals(json)) {
                return null;
            }
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null && delta.has("content")) {
                    return delta.get("content").asText();
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to extract content from chunk: " + chunkJson, e);
        }
        return null;
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        return super.writeWith(body)
                .doFinally(signalType -> {
                    // 在响应完全结束后清理状态
                    STATE_MAP.remove(stateKey);
                });
    }

    /**
     * Decorator Status Class
     */
    private static class DecoratorState {
        private final ServerWebExchange exchange;
        private final AccumulativeContentBuffer slidingWindowBuffer;
        private final AtomicBoolean isBlocked;
        private final AtomicInteger processCounter = new AtomicInteger(0);

        public DecoratorState(ServerWebExchange exchange, int windowSize, int batchSize) {
            this.exchange = exchange;
            this.slidingWindowBuffer = new AccumulativeContentBuffer(windowSize);
            this.isBlocked = new AtomicBoolean(false);
        }
    }

    /**
     * Accumulative content buffer
     */
    private static class AccumulativeContentBuffer {
        private final int maxSize;
        private final StringBuilder contentBuffer;

        public AccumulativeContentBuffer(int maxSize) {
            this.maxSize = maxSize;
            this.contentBuffer = new StringBuilder();
        }

        /**
         * Add content to the buffer
         */
        public void addContent(String content) {
            if (content == null || content.isEmpty()) {
                return;
            }
            
            int oldSize = contentBuffer.length();
            contentBuffer.append(content);
            int newSize = contentBuffer.length();
            
            // 如果超过阈值，丢弃前面的内容
            if (newSize > maxSize) {
                int charsToRemove = newSize - maxSize;
                String removedContent = contentBuffer.substring(0, charsToRemove);
                contentBuffer.delete(0, charsToRemove);
            }
        }

        /**
         * Determine whether a detection should be performed
         */
        public boolean shouldCheck() {
            return contentBuffer.length() >= maxSize;
        }

        /**
         * Retrieve current cumulative content
         */
        public String getWindowContent() {
            String content = contentBuffer.toString();
            return content;
        }

        /**
         * Get the current buffer size
         */
        public int getCurrentSize() {
            return contentBuffer.length();
        }
    }
}
