package org.apache.shenyu.plugin.sec.mark;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import org.apache.shenyu.common.dto.convert.rule.ContentMarkHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/23 14:28
 */
public class WaterMarker {
    private static final Logger LOG = LoggerFactory.getLogger(WaterMarker.class);
    private static final ConnectionProvider WM_PROVIDER = ConnectionProvider.builder("watermark-pool")
            .maxConnections(800)
            .pendingAcquireMaxCount(8000)
            .pendingAcquireTimeout(java.time.Duration.ofSeconds(2))
            .maxIdleTime(java.time.Duration.ofSeconds(30))
            .maxLifeTime(java.time.Duration.ofMinutes(2))
            .evictInBackground(java.time.Duration.ofSeconds(30))
            .build();

    private static final HttpClient WM_HTTP_CLIENT = HttpClient.create(WM_PROVIDER)
            .compress(true)
            .keepAlive(true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .responseTimeout(java.time.Duration.ofSeconds(4))
            .doOnConnected(conn -> conn
                    .addHandlerLast(new ReadTimeoutHandler(5))
                    .addHandlerLast(new WriteTimeoutHandler(5))
            );

    private static final WebClient WEB_CLIENT = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(WM_HTTP_CLIENT))
            .build();

    public static Mono<TextMarkResponse> addMarkForText(final TextMarkRequest request, final ContentMarkHandle handle) {
        return Mono.fromCallable(() -> new AddMarkHystrixCommand(request, handle).execute());
    }

    // HystrixCommand
    static class AddMarkHystrixCommand extends HystrixCommand<TextMarkResponse> {
        private final TextMarkRequest request;
        private final ContentMarkHandle handle;

        AddMarkHystrixCommand(TextMarkRequest request, ContentMarkHandle handle) {
            super(Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory.asKey("WaterMark"))
                    .andCommandKey(HystrixCommandKey.Factory.asKey("AddMarkForText"))
                    .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("WaterMarkPool"))
                    .andThreadPoolPropertiesDefaults(
                            HystrixThreadPoolProperties.Setter()
                                    .withCoreSize(Optional.ofNullable(handle.getHystrixThreadPoolCoreSize()).orElse(800))
                                    .withMaximumSize(Optional.ofNullable(handle.getHystrixThreadPoolMaxSize()).orElse(1000))
                                    .withMaxQueueSize(Optional.ofNullable(handle.getHystrixThreadPoolQueueCapacity()).orElse(50))
                                    .withAllowMaximumSizeToDivergeFromCoreSize(true)
                                    .withKeepAliveTimeMinutes(1)
                                    .withQueueSizeRejectionThreshold(200)
                    )
                    .andCommandPropertiesDefaults(
                            HystrixCommandProperties.Setter()
                                    .withMetricsRollingStatisticalWindowInMilliseconds(Optional.ofNullable(handle.getBreakerSleepWindowInMilliseconds()).orElse(10000))
                                    .withExecutionTimeoutInMilliseconds(Optional.ofNullable(handle.getTimeoutInMilliseconds()).orElse(5000))
                                    .withCircuitBreakerEnabled(Optional.ofNullable(handle.getEnabled()).orElse(Boolean.TRUE))
                                    .withCircuitBreakerRequestVolumeThreshold(Optional.ofNullable(handle.getBreakerRequestVolumeThreshold()).orElse(150))
                                    .withCircuitBreakerErrorThresholdPercentage(Optional.ofNullable(handle.getBreakerErrorThresholdPercentage()).orElse(70))
                                    .withCircuitBreakerSleepWindowInMilliseconds(Optional.ofNullable(handle.getBreakerSleepWindowInMilliseconds()).orElse(8000))
                                    .withExecutionIsolationStrategy(
                                            HystrixCommandProperties.ExecutionIsolationStrategy.THREAD
                                    )
                                    .withFallbackEnabled(true)
                                    .withRequestLogEnabled(true)
                                    .withRequestCacheEnabled(true)
                                    .withMetricsRollingPercentileEnabled(true)
                                    .withMetricsRollingPercentileWindowInMilliseconds(60000)
                                    .withMetricsRollingPercentileWindowBuckets(6)
                    )
            );
            this.request = request;
            this.handle = handle;
        }

        @Override
        protected TextMarkResponse run() {
            try {
                // 设置合理的超时时间，确保有足够的缓冲时间
                long timeout = Math.max(2000, Optional.ofNullable(handle.getTimeoutInMilliseconds()).orElse(10000) - 2000);
                
                // 使用CompletableFuture避免阻塞，提高异步性能
                CompletableFuture<TextMarkResponse> future = addMarkForTextInternal(request, handle)
                        .toFuture();
                
                return future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                LOG.error("Watermark API call timeout", e);
                throw new RuntimeException("Watermark接口调用超时: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Watermark API call interrupted", e);
                throw new RuntimeException("Watermark接口调用被中断: " + e.getMessage(), e);
            } catch (ExecutionException e) {
                LOG.error("Watermark API call execution failed", e);
                throw new RuntimeException("Watermark接口调用执行失败: " + e.getMessage(), e);
            } catch (Exception e) {
                LOG.error("Watermark run error: {}", e.getMessage(), e);
                throw e;
            }
        }

        @Override
        protected TextMarkResponse getFallback() {
            LOG.warn("Watermark fallback by Hystrix.");
            TextMarkResponse fallback = new TextMarkResponse();
            fallback.setCode(-1);
            fallback.setMessage("Watermark API error, fallback by Hystrix");
            fallback.setSuccess(false);
            Data data = new Data();
            data.setContent(request.getContent());
            data.setWaterMarkDarkInfo(null);
            fallback.setData(data);
            return fallback;
        }
    }

    // true call
    private static Mono<TextMarkResponse> addMarkForTextInternal(final TextMarkRequest request, final ContentMarkHandle handle) {
        String endpoint = handle.getUrl();
        LOG.info("Calling watermark API [{}]", endpoint);
        String endPoint = handle.getUrl();
        return WEB_CLIENT.post()
                .uri(endPoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TextMarkResponse.class);
    }

    /**
     * request params
     */
    public static class TextMarkRequest {
        private String contentNumber;
        private String userInput;
        private String content;
        private String dataType;
        private String modelName;
        private String applicationName;
        private String serviceProvider;
        private String serviceUser;
        private String requestIdentification;

        public String getContentNumber() {
            return contentNumber;
        }

        public void setContentNumber(String contentNumber) {
            this.contentNumber = contentNumber;
        }

        public String getUserInput() {
            return userInput;
        }

        public void setUserInput(String userInput) {
            this.userInput = userInput;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getApplicationName() {
            return applicationName;
        }

        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }

        public String getServiceProvider() {
            return serviceProvider;
        }

        public void setServiceProvider(String serviceProvider) {
            this.serviceProvider = serviceProvider;
        }

        public String getServiceUser() {
            return serviceUser;
        }

        public void setServiceUser(String serviceUser) {
            this.serviceUser = serviceUser;
        }

        public String getRequestIdentification() {
            return requestIdentification;
        }

        public void setRequestIdentification(String requestIdentification) {
            this.requestIdentification = requestIdentification;
        }
    }

    /**
     * response
     */
    public static class TextMarkResponse {
        private Integer code;
        private String message;
        private Boolean success;
        private Data data;

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Boolean getSuccess() {
            return success;
        }

        public void setSuccess(Boolean success) {
            this.success = success;
        }

        public Data getData() {
            return data;
        }

        public void setData(Data data) {
            this.data = data;
        }
    }

    public static class Data {
        private String waterMarkDarkInfo;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        private String content;

        public String getWaterMarkDarkInfo() {
            return waterMarkDarkInfo;
        }

        public void setWaterMarkDarkInfo(String waterMarkDarkInfo) {
            this.waterMarkDarkInfo = waterMarkDarkInfo;
        }
    }
}
