package org.apache.shenyu.plugin.sec.content.checker;

import com.google.gson.JsonObject;
import com.netflix.hystrix.*;
import io.micrometer.core.instrument.Timer;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.sec.content.ContentSecurityResult;
import org.apache.shenyu.plugin.sec.content.metrics.SecurityMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/11/13 11:23
 */
public class ContentSecurityCheckerJD implements ContentSecurityChecker {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityCheckerJD.class);
    
    private final SecurityMetricsCollector metricsCollector;
    //  reuse a singleton WebClient
    private static final ConnectionProvider JD_PROVIDER = ConnectionProvider.builder("JD-pool")
            .maxConnections(1200)
            .pendingAcquireMaxCount(15000)
            .pendingAcquireTimeout(java.time.Duration.ofMillis(1500))
            .maxIdleTime(java.time.Duration.ofSeconds(30))
            .maxLifeTime(java.time.Duration.ofMinutes(2))
            .evictInBackground(java.time.Duration.ofSeconds(30))
            .metrics(true)
            .build();

    private static final HttpClient JD_HTTP_CLIENT = HttpClient.create(JD_PROVIDER)
            .compress(true)
            .keepAlive(true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 800)
            .responseTimeout(java.time.Duration.ofMillis(1500))
            .doOnConnected(conn -> conn
                    .addHandlerLast(new ReadTimeoutHandler(10))
                    .addHandlerLast(new WriteTimeoutHandler(5))
            );

    private static final WebClient WEB_CLIENT = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(JD_HTTP_CLIENT))
            .build();
            
    public ContentSecurityCheckerJD() {
        this.metricsCollector = null;
    }
    
    public ContentSecurityCheckerJD(SecurityMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        if (metricsCollector != null) {
            metricsCollector.initConnectionPoolMetrics("JD-pool");
            metricsCollector.initThreadPoolMetrics("ContentSecurityPool");
        }
    }

    /**
     * Call a third-party content security detection API to check the compliance of a given text.
     *
     * @param req The body of the request to be sent, including prompt and content
     * @return Asynchronous Mono, which produces a SafetyCheckResponse test result
     */
    public static Mono<JDCheckResponse> checkText(final JDCheckRequest req, final ContentSecurityHandle handle) {
        return Mono.fromCallable(() -> new ContentSecHystrixCommand(req, handle, null).execute());
    }
    
    /**
     * 调用第三方内容安全检测API检查文本合规性（带监控）
     *
     * @param req 请求体，包含检测参数
     * @param handle 配置参数
     * @return 异步Mono，产生检测结果
     */
    public Mono<JDCheckResponse> checkTextWithMetrics(final JDCheckRequest req, final ContentSecurityHandle handle) {
        return Mono.fromCallable(() -> new ContentSecHystrixCommand(req, handle, this.metricsCollector).execute());
    }

    @Override
    public Mono<ContentSecurityResult> checkText(Object request, ContentSecurityHandle handle) {
        if (!(request instanceof JDCheckRequest JDRequest)) {
            return Mono.error(new IllegalArgumentException("Request must be SafetyCheckRequest for JD vendor"));
        }

        // 记录API调用指标
        Timer.Sample sample = null;
        if (metricsCollector != null) {
            metricsCollector.recordApiCall("JD", "checkText");
            sample = metricsCollector.startTimer();
        }

        final Timer.Sample finalSample = sample;
        
        return checkTextWithMetrics(JDRequest, handle)
                .map(response -> {
                    ContentSecurityResult result = this.convertToResult(response);
                    
                    // 记录成功指标
                    if (metricsCollector != null) {
                        metricsCollector.recordApiSuccess("JD", "checkText");
                        if (finalSample != null) {
                            metricsCollector.stopTimer(finalSample, "JD", "checkText");
                        }
                        
                        // 记录响应大小（如果有响应数据）
                        if (response != null) {
                            String responseStr = response.toString();
                            metricsCollector.recordResponseSize("JD", responseStr.length());
                        }
                    }
                    
                    return result;
                })
                .onErrorResume(throwable -> {
                    LOG.error("JD content security check error", throwable);
                    
                    // 记录失败指标
                    if (metricsCollector != null) {
                        String errorType = throwable.getClass().getSimpleName();
                        metricsCollector.recordApiFailure("JD", "checkText", errorType);
                        if (finalSample != null) {
                            metricsCollector.stopTimer(finalSample, "JD", "checkText");
                        }
                    }
                    
                    return Mono.just(ContentSecurityResult.error("JD",
                        throwable.getMessage(), "1500", "内容安全检测服务不可用"));
                });
    }

    @Override
    public String getVendor() {
        return "JD";
    }

    @Override
    public boolean supports(String vendor) {
        return "JD".equalsIgnoreCase(vendor);
    }
    /**
     * 将JD的响应转换为统一的结果格式
     */
    private ContentSecurityResult convertToResult(JDCheckResponse response) {

        if (response == null) {
            return ContentSecurityResult.error("JD", "JD's response is null'", "1500", "response is null");
        }
        String id = response.getId();
        String model = response.getModel();
        List<RiskItem> riskItems = response.getResults();
        //flagged检测标识：false合规，true违规
        String flagged = riskItems.get(0).getFlagged();
        JsonObject categories = riskItems.get(0).getCategories();

        if ("true".equals(flagged)) {
            return ContentSecurityResult.failed("JD", categories.toString(),
                "JD Test results：" + categories, response);
        } else {
            return ContentSecurityResult.passed("JD", response);
        }
    }

    // HystrixCommand
    static class ContentSecHystrixCommand extends HystrixCommand<JDCheckResponse> {
        private final JDCheckRequest req;
        private final ContentSecurityHandle handle;
        private final SecurityMetricsCollector metricsCollector;

        ContentSecHystrixCommand(JDCheckRequest req, ContentSecurityHandle handle, SecurityMetricsCollector metricsCollector) {
            super(Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory.asKey("ContentSecurity"))
                    .andCommandKey(HystrixCommandKey.Factory.asKey("CheckText"))
                    .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("ContentSecurityPool"))
                    .andThreadPoolPropertiesDefaults(
                            HystrixThreadPoolProperties.Setter()
                                    .withCoreSize(Optional.ofNullable(handle.getHystrixThreadPoolCoreSize()).orElse(1000))
                                    .withMaximumSize(Optional.ofNullable(handle.getHystrixThreadPoolMaxSize()).orElse(1200))
                                    .withMaxQueueSize(Optional.ofNullable(handle.getHystrixThreadPoolQueueCapacity()).orElse(50))
                                    .withAllowMaximumSizeToDivergeFromCoreSize(Optional.ofNullable(handle.getAllowMaximumSizeToDivergeFromCoreSize()).orElse(Boolean.TRUE))
                                    .withKeepAliveTimeMinutes(1)
                                    .withQueueSizeRejectionThreshold(200)
                    )
                    .andCommandPropertiesDefaults(
                            HystrixCommandProperties.Setter()
                                    .withMetricsRollingStatisticalWindowInMilliseconds(Optional.ofNullable(handle.getStatisticalWindow()).orElse(5000))
                                    .withExecutionTimeoutInMilliseconds(Optional.ofNullable(handle.getTimeoutInMilliseconds()).orElse(1800))
                                    .withCircuitBreakerEnabled(Optional.ofNullable(handle.getEnabled()).orElse(Boolean.TRUE))
                                    .withCircuitBreakerRequestVolumeThreshold(Optional.ofNullable(handle.getBreakerRequestVolumeThreshold()).orElse(200))
                                    .withCircuitBreakerErrorThresholdPercentage(Optional.ofNullable(handle.getBreakerErrorThresholdPercentage()).orElse(60))
                                    .withCircuitBreakerSleepWindowInMilliseconds(Optional.ofNullable(handle.getBreakerSleepWindowInMilliseconds()).orElse(4000))
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
            this.req = req;
            this.handle = handle;
            this.metricsCollector = metricsCollector;
        }

        @Override
        protected JDCheckResponse run() {
            try {
                long timeout = Math.max(2000, Optional.ofNullable(handle.getTimeoutInMilliseconds()).orElse(10000) - 2000);

                CompletableFuture<JDCheckResponse> future = checkTextInternal(req, handle)
                        .toFuture();
                
                JDCheckResponse resp = future.get(timeout, TimeUnit.MILLISECONDS);
                
                if (resp == null || !"200".equals(resp.getId())) {
                    throw new RuntimeException("JD call failed，code=" + (resp == null ? "null" : resp.getId()));
                }
                return resp;
            } catch (TimeoutException e) {
                LOG.error("JD API call timeout", e);
                throw new RuntimeException("JD Interface call timeout: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("JDAPI call interrupted", e);
                throw new RuntimeException("JDInterface call is interrupted: " + e.getMessage(), e);
            } catch (ExecutionException e) {
                LOG.error("JD API call execution failed", e);
                throw new RuntimeException("JD interface call failed to execute: " + e.getMessage(), e);
            } catch (Exception e) {
                LOG.error("JD API call failed", e);
                throw new RuntimeException("JD interface call failed: " + e.getMessage(), e);
            }
        }

        @Override
        protected JDCheckResponse getFallback() {
            LOG.warn("Content security fallback by Hystrix.");
            
            // 记录Hystrix fallback指标
            if (metricsCollector != null) {
                metricsCollector.recordApiFailure("JD", "checkText", "hystrix_fallback");
                
                // 检查fallback原因并记录
                if (isResponseTimedOut()) {
                    LOG.warn("Fallback reason: Response timed out");
                    metricsCollector.recordApiFailure("JD", "checkText", "timeout");
                } else if (isFailedExecution()) {
                    LOG.warn("Fallback reason: Execution failed", getFailedExecutionException());
                    metricsCollector.recordApiFailure("JD", "checkText", "execution_failed");
                } else if (isResponseRejected()) {
                    LOG.warn("Fallback reason: Response rejected");
                    metricsCollector.recordApiFailure("JD", "checkText", "rejected");
                    // 记录线程池拒绝
                    metricsCollector.incrementThreadPoolRejectedTasks("ContentSecurityPool");
                } else if (isCircuitBreakerOpen()) {
                    LOG.warn("Fallback reason: Circuit breaker is open");
                    metricsCollector.recordApiFailure("JD", "checkText", "circuit_breaker_open");
                }
            }
            
            JDCheckResponse resp = new JDCheckResponse();
            resp.setId("1500");
            resp.setModel("内容安全检测服务不可用(fallback by Hystrix)");
            List<RiskItem> results = new ArrayList<>();
            RiskItem riskItem = new RiskItem();
            riskItem.setFlagged("false");
            results.add(riskItem);
            resp.setModel("");
            resp.setResults(results);
            return resp;
        }
    }

    // true call
    private static Mono<JDCheckResponse> checkTextInternal(final JDCheckRequest req, final ContentSecurityHandle handle) {
        String endpoint = handle.getUrl();
        LOG.info("Calling content safety API [{}]", endpoint);
        return WEB_CLIENT.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(JDCheckResponse.class);
    }

    public static class JDCheckRequest {

        private String id;
        private String model;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

    }

    public static class JDCheckResponse {
        private String id;
        private String model;
        private List<RiskItem> results;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<RiskItem> getResults() {
            return results;
        }

        public void setResults(List<RiskItem> results) {
            this.results = results;
        }

        @Override
        public String toString() {
            return "JDCheckResponse{" +
                    "id='" + id + '\'' +
                    ", model='" + model + '\'' +
                    ", results=" + results +
                    '}';
        }
    }

    public static class RiskItem {
        private String flagged;
        private JsonObject categories;
        private JsonObject category_scores;
        private JsonObject category_applied_input_types;
        public String getFlagged() {
            return flagged;
        }

        public void setFlagged(String flagged) {
            this.flagged = flagged;
        }

        public JsonObject getCategories() {
            return categories;
        }

        public void setCategories(JsonObject categories) {
            this.categories = categories;
        }

        public JsonObject getCategory_scores() {
            return category_scores;
        }

        public void setCategory_scores(JsonObject category_scores) {
            this.category_scores = category_scores;
        }

        public JsonObject getCategory_applied_input_types() {
            return category_applied_input_types;
        }

        public void setCategory_applied_input_types(JsonObject category_applied_input_types) {
            this.category_applied_input_types = category_applied_input_types;
        }

        @Override
        public String toString() {
            return "RiskItem{" +
                    "flagged='" + flagged + '\'' +
                    ", categories=" + categories +
                    ", category_scores=" + category_scores +
                    ", category_applied_input_types=" + category_applied_input_types +
                    '}';
        }
    }



    public static class categories {

    }

    public static class category_scores {

    }

}
