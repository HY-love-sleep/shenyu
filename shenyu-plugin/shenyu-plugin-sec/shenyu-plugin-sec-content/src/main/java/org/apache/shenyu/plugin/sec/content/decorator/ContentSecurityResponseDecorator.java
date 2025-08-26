package org.apache.shenyu.plugin.sec.content.decorator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.base.decorator.GenericResponseDecorator;
import org.apache.shenyu.plugin.sec.content.ContentSecurityService;
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
 * @version 2.0
 */
public class ContentSecurityResponseDecorator extends GenericResponseDecorator {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityResponseDecorator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    // append state
    private static final int BATCH_SIZE = 20;
    private static final int WINDOW_SIZE = 100;
    
    // global State Manager
    private static final ConcurrentHashMap<String, DecoratorState> STATE_MAP = new ConcurrentHashMap<>();
    
    private final ContentSecurityHandle handle;
    private final String stateKey;
    private final ContentSecurityService contentSecurityService;

    public ContentSecurityResponseDecorator(ServerWebExchange exchange, ContentSecurityHandle handle, ContentSecurityService contentSecurityService) {
        super(
                exchange.getResponse(),
                exchange,
                BATCH_SIZE,
                buildProcessAndOutput(handle, exchange, contentSecurityService)
        );
        this.handle = handle;
        this.stateKey = exchange.getRequest().getId();
        this.contentSecurityService = contentSecurityService;

        LOG.info("ContentSecurityResponseDecorator 初始化 - 请求ID: {}, 厂商: {}, 批次大小: {}, 检测阈值: {}", 
                stateKey, handle.getVendor(), BATCH_SIZE, WINDOW_SIZE);
        
        // init state
        STATE_MAP.put(stateKey, new DecoratorState(exchange, WINDOW_SIZE, BATCH_SIZE));
    }

    private static BiFunction<List<String>, List<byte[]>, Flux<DataBuffer>> buildProcessAndOutput(
            ContentSecurityHandle handle, ServerWebExchange exchange, ContentSecurityService contentSecurityService) {
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

            LOG.info("批次处理 - 请求ID: {}, 批次: {}, 当前批次内容长度: {}, 累积内容长度: {}, 是否达到检测阈值: {}", 
                    stateKey, currentCount, batchContent.length(), 
                    state.slidingWindowBuffer.getCurrentSize(), 
                    state.slidingWindowBuffer.shouldCheck());

            // 判断是否需要检测：达到阈值 或 强制检测条件
            boolean shouldCheck = state.slidingWindowBuffer.shouldCheck();
            
            if (!shouldCheck) {
                LOG.debug("未达到检测条件，直接输出内容");
                return Flux.fromIterable(rawSseBytes)
                        .map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes));
            }

            // 获取滑动窗口内容
            String slidingWindowContent = state.slidingWindowBuffer.getSlidingWindowContent(batchContent);
            
            LOG.info("滑动窗口详情 - 请求ID: {}, 批次: {}, 缓冲区大小: {}/{}, 检测模式: {}, 送审内容: {}", 
                    stateKey, currentCount, 
                    state.slidingWindowBuffer.getCurrentSize(), WINDOW_SIZE,
                    state.slidingWindowBuffer.isFirstCheck() ? "首次检测" : "滑动窗口",
                    slidingWindowContent);
            
            LOG.info("触发内容安全检测 - 请求ID: {}, 内容长度: {}, 检测类型: {}", 
                    stateKey, slidingWindowContent.length(), 
                    state.slidingWindowBuffer.isFirstCheck() ? "首次检测" : "阈值触发");
            
            return contentSecurityService.checkText(slidingWindowContent, handle, "output")
                    .flatMapMany(resp -> {
                        LOG.info("内容安全检测完成 - 请求ID: {}, 检测结果: {}, 是否通过: {}, 厂商: {}, 风险描述: {}", 
                                stateKey, resp.isSuccess() ? "成功" : "失败", 
                                resp.isPassed() ? "通过" : "不通过", 
                                resp.getVendor(), resp.getRiskDescription());
                        
                        if (!resp.isSuccess()) {
                            exchange.getAttributes().put("SEC_ERROR", true);
                            state.isBlocked.set(true);
                            String errResp = String.format("{\"code\":1401,\"msg\":\"内容安全检测服务异常\",\"detail\":\"%s，厂商：%s\"}", 
                                    resp.getErrorMessage(), resp.getVendor());
                            byte[] errBytes = errResp.getBytes(StandardCharsets.UTF_8);
                            return Flux.just(exchange.getResponse().bufferFactory().wrap(errBytes));
                        }
                        
                        if (!resp.isPassed()) {
                            exchange.getAttributes().put("SEC_ERROR", true);
                            state.isBlocked.set(true);
                            String errResp = String.format("{\"code\":1401,\"msg\":\"返回内容违规\",\"detail\":\"检测结果：%s，厂商：%s\"}", 
                                    resp.getRiskDescription(), resp.getVendor());
                            byte[] errBytes = errResp.getBytes(StandardCharsets.UTF_8);
                            return Flux.just(exchange.getResponse().bufferFactory().wrap(errBytes));
                        } else {
                            LOG.info("内容安全检测通过 - 请求ID: {}, 厂商: {}", stateKey, resp.getVendor());
                            
                            // 如果是第一次检测，标记为已完成
                            if (state.slidingWindowBuffer.isFirstCheck()) {
                                state.slidingWindowBuffer.markFirstCheckCompleted();
                            }
                            
                            return Flux.fromIterable(rawSseBytes)
                                    .map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes));
                        }
                    })
                    .doOnError(e -> LOG.error("内容安全检测异常 - 请求ID: {}", stateKey, e));
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
                    LOG.info("清理装饰器状态 - 请求ID: {}, 信号类型: {}", stateKey, signalType);
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
        private boolean isFirstCheck = true; // 标记是否是第一次检测

        public AccumulativeContentBuffer(int maxSize) {
            this.maxSize = maxSize;
            this.contentBuffer = new StringBuilder();
        }

        /**
         * Add content to the buffer
         * 添加内容到滑动窗口缓冲区，维护固定大小的滑动窗口
         */
        public void addContent(String content) {
            if (content == null || content.isEmpty()) {
                return;
            }
            
            // 先添加新内容
            contentBuffer.append(content);
            
            // 如果不是第一次检测，按照滑动窗口逻辑处理
            if (!isFirstCheck) {
                // 如果超过阈值，丢弃前面的内容，保持滑动窗口大小
                while (contentBuffer.length() > maxSize) {
                    int charsToRemove = Math.min(contentBuffer.length() - maxSize, 10);
                    String removedContent = contentBuffer.substring(0, charsToRemove);
                    contentBuffer.delete(0, charsToRemove);
                    
                    LOG.debug("滑动窗口超出大小限制，丢弃前{}个字符: '{}', 当前缓冲区大小: {}", 
                            charsToRemove, removedContent, contentBuffer.length());
                }
            }
        }

        /**
         * Determine whether a detection should be performed
         */
        public boolean shouldCheck() {
            // 第一次检测：只要有内容就检测
            if (isFirstCheck) {
                return contentBuffer.length() > 0;
            }
            // 后续检测：达到滑动窗口大小才检测
            return contentBuffer.length() >= maxSize;
        }

        /**
         * Retrieve current cumulative content
         */
        public String getWindowContent() {
            return contentBuffer.toString();
        }

        /**
         * Get the current buffer size
         */
        public int getCurrentSize() {
            return contentBuffer.length();
        }

        /**
         * Get the sliding window content for detection
         * 返回滑动窗口内容：第一次检测返回完整内容，后续按滑动窗口返回
         */
        public String getSlidingWindowContent(String currentBatchContent) {
            return contentBuffer.toString();
        }

        /**
         * Mark that the first check has been completed
         * 标记第一次检测已完成
         */
        public void markFirstCheckCompleted() {
            isFirstCheck = false;
            LOG.debug("第一次检测完成，切换到滑动窗口模式，当前缓冲区大小: {}", contentBuffer.length());
        }

        /**
         * Check if this is the first check
         */
        public boolean isFirstCheck() {
            return isFirstCheck;
        }

        /**
         * Get the current buffer content
         */
        public String getBufferContent() {
            return contentBuffer.toString();
        }
        
        /**
         * Get sliding window statistics for debugging
         */
        public String getSlidingWindowStats() {
            return String.format("缓冲区大小: %d/%d, 模式: %s", 
                    contentBuffer.length(), maxSize, 
                    isFirstCheck ? "首次检测" : "滑动窗口");
        }
        
        /**
         * Get detailed sliding window info for debugging
         */
        public String getDetailedSlidingWindowInfo() {
            return String.format("缓冲区大小: %d/%d, 模式: %s", 
                    contentBuffer.length(), maxSize, 
                    isFirstCheck ? "首次检测" : "滑动窗口");
        }
    }
}
