package org.apache.shenyu.plugin.sec.content.checker;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.sec.content.ContentSecurityResult;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import org.apache.shenyu.plugin.sec.content.metrics.SecurityMetricsCollector;
import io.micrometer.core.instrument.Timer;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 15:23
 */
public class ContentSecurityCheckerZkrj implements ContentSecurityChecker {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityCheckerZkrj.class);
    
    private final SecurityMetricsCollector metricsCollector;
    //  reuse a singleton WebClient
    private static final ConnectionProvider ZKRJ_PROVIDER = ConnectionProvider.builder("zkrj-pool")
            .maxConnections(1200)
            .pendingAcquireMaxCount(15000)
            .pendingAcquireTimeout(java.time.Duration.ofMillis(1500))
            .maxIdleTime(java.time.Duration.ofSeconds(30))
            .maxLifeTime(java.time.Duration.ofMinutes(2))
            .evictInBackground(java.time.Duration.ofSeconds(30))
            .build();

    private static final HttpClient ZKRJ_HTTP_CLIENT = HttpClient.create(ZKRJ_PROVIDER)
            .compress(true)
            .keepAlive(true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 800)
            .responseTimeout(java.time.Duration.ofMillis(1500))
            .doOnConnected(conn -> conn
                    .addHandlerLast(new ReadTimeoutHandler(2))
                    .addHandlerLast(new WriteTimeoutHandler(2))
            );

    private static final WebClient WEB_CLIENT = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(ZKRJ_HTTP_CLIENT))
            .build();
            
    public ContentSecurityCheckerZkrj() {
        this.metricsCollector = null;
    }
    
    public ContentSecurityCheckerZkrj(SecurityMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        if (metricsCollector != null) {
            metricsCollector.initConnectionPoolMetrics("zkrj-pool");
            metricsCollector.initThreadPoolMetrics("ContentSecurityPool");
        }
    }

    /**
     * Call a third-party content security detection API to check the compliance of a given text.
     *
     * @param req The body of the request to be sent, including prompt and content
     * @return Asynchronous Mono, which produces a SafetyCheckResponse test result
     */
    public static Mono<SafetyCheckResponse> checkText(final SafetyCheckRequest req, final ContentSecurityHandle handle) {
        return Mono.fromCallable(() -> new ContentSecHystrixCommand(req, handle, null).execute());
    }
    
    /**
     * 调用第三方内容安全检测API检查文本合规性（带监控）
     *
     * @param req 请求体，包含检测参数
     * @param handle 配置参数
     * @return 异步Mono，产生检测结果
     */
    public Mono<SafetyCheckResponse> checkTextWithMetrics(final SafetyCheckRequest req, final ContentSecurityHandle handle) {
        return Mono.fromCallable(() -> new ContentSecHystrixCommand(req, handle, this.metricsCollector).execute());
    }

    @Override
    public Mono<ContentSecurityResult> checkText(Object request, ContentSecurityHandle handle) {
        if (!(request instanceof SafetyCheckRequest zkrjRequest)) {
            return Mono.error(new IllegalArgumentException("Request must be SafetyCheckRequest for ZKRJ vendor"));
        }

        // 记录API调用指标
        Timer.Sample sample = null;
        if (metricsCollector != null) {
            metricsCollector.recordApiCall("zkrj", "checkText");
            sample = metricsCollector.startTimer();
        }

        final Timer.Sample finalSample = sample;
        
        return checkTextWithMetrics(zkrjRequest, handle)
                .map(response -> {
                    ContentSecurityResult result = this.convertToResult(response);
                    
                    // 记录成功指标
                    if (metricsCollector != null) {
                        metricsCollector.recordApiSuccess("zkrj", "checkText");
                        if (finalSample != null) {
                            metricsCollector.stopTimer(finalSample, "zkrj", "checkText");
                        }
                        
                        // 记录响应大小（如果有响应数据）
                        if (response != null) {
                            String responseStr = response.toString();
                            metricsCollector.recordResponseSize("zkrj", responseStr.length());
                        }
                    }
                    
                    return result;
                })
                .onErrorResume(throwable -> {
                    LOG.error("ZKRJ content security check error", throwable);
                    
                    // 记录失败指标
                    if (metricsCollector != null) {
                        String errorType = throwable.getClass().getSimpleName();
                        metricsCollector.recordApiFailure("zkrj", "checkText", errorType);
                        if (finalSample != null) {
                            metricsCollector.stopTimer(finalSample, "zkrj", "checkText");
                        }
                    }
                    
                    return Mono.just(ContentSecurityResult.error("zkrj", 
                        throwable.getMessage(), "1500", "内容安全检测服务不可用"));
                });
    }

    @Override
    public String getVendor() {
        return "zkrj";
    }

    @Override
    public boolean supports(String vendor) {
        return "zkrj".equalsIgnoreCase(vendor);
    }

    /**
     * 将ZKRJ的响应转换为统一的结果格式
     */
    private ContentSecurityResult convertToResult(SafetyCheckResponse response) {
        if (response == null || !"200".equals(response.getCode())) {
            return ContentSecurityResult.error("zkrj", 
                "Failed Call ZKRJ API，code=" + (response == null ? "null" : response.getCode()),
                response == null ? "1500" : response.getCode(), 
                response == null ? "response is null" : response.getMsg());
        }

        SafetyCheckData data = response.getData();
        if (data == null) {
            return ContentSecurityResult.error("zkrj", "ZKRJ's response is null'", "1500", "response is null");
        }

        // 根据zkrj接口文档， 依据PromptCategory来判断是否合规
        String promptCategory = data.getPromptCategory();
        if ("违规".equals(promptCategory) || "疑似".equals(promptCategory)) {
            return ContentSecurityResult.failed("zkrj", promptCategory, 
                "ZKRJ Test results：" + promptCategory, response);
        } else {
            return ContentSecurityResult.passed("zkrj", response);
        }
    }

    // HystrixCommand
    static class ContentSecHystrixCommand extends HystrixCommand<SafetyCheckResponse> {
        private final SafetyCheckRequest req;
        private final ContentSecurityHandle handle;
        private final SecurityMetricsCollector metricsCollector;

        ContentSecHystrixCommand(SafetyCheckRequest req, ContentSecurityHandle handle, SecurityMetricsCollector metricsCollector) {
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
        protected SafetyCheckResponse run() {
            try {
                long timeout = Math.max(2000, Optional.ofNullable(handle.getTimeoutInMilliseconds()).orElse(10000) - 2000);

                CompletableFuture<SafetyCheckResponse> future = checkTextInternal(req, handle)
                        .toFuture();
                
                SafetyCheckResponse resp = future.get(timeout, TimeUnit.MILLISECONDS);
                
                if (resp == null || !"200".equals(resp.getCode())) {
                    throw new RuntimeException("zkrj call failed，code=" + (resp == null ? "null" : resp.getCode()));
                }
                return resp;
            } catch (TimeoutException e) {
                LOG.error("ZKRJ API call timeout", e);
                throw new RuntimeException("ZKRJ Interface call timeout: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("ZKRJ API call interrupted", e);
                throw new RuntimeException("ZKRJ Interface call is interrupted: " + e.getMessage(), e);
            } catch (ExecutionException e) {
                LOG.error("ZKRJ API call execution failed", e);
                throw new RuntimeException("ZKRJ interface call failed to execute: " + e.getMessage(), e);
            } catch (Exception e) {
                LOG.error("ZKRJ API call failed", e);
                throw new RuntimeException("ZKRJ interface call failed: " + e.getMessage(), e);
            }
        }

        @Override
        protected SafetyCheckResponse getFallback() {
            LOG.warn("Content security fallback by Hystrix.");
            
            // 记录Hystrix fallback指标
            if (metricsCollector != null) {
                metricsCollector.recordApiFailure("zkrj", "checkText", "hystrix_fallback");
                
                // 检查fallback原因并记录
                if (isResponseTimedOut()) {
                    LOG.warn("Fallback reason: Response timed out");
                    metricsCollector.recordApiFailure("zkrj", "checkText", "timeout");
                } else if (isFailedExecution()) {
                    LOG.warn("Fallback reason: Execution failed", getFailedExecutionException());
                    metricsCollector.recordApiFailure("zkrj", "checkText", "execution_failed");
                } else if (isResponseRejected()) {
                    LOG.warn("Fallback reason: Response rejected");
                    metricsCollector.recordApiFailure("zkrj", "checkText", "rejected");
                    // 记录线程池拒绝
                    metricsCollector.incrementThreadPoolRejectedTasks("ContentSecurityPool");
                } else if (isCircuitBreakerOpen()) {
                    LOG.warn("Fallback reason: Circuit breaker is open");
                    metricsCollector.recordApiFailure("zkrj", "checkText", "circuit_breaker_open");
                }
            }
            
            SafetyCheckResponse resp = new SafetyCheckResponse();
            resp.setCode("1500");
            resp.setMsg("内容安全检测服务不可用(fallback by Hystrix)");
            SafetyCheckData data = new SafetyCheckData();
            data.setPromptCategory("接口异常");
            resp.setData(data);
            return resp;
        }
    }

    // true call
    private static Mono<SafetyCheckResponse> checkTextInternal(final SafetyCheckRequest req, final ContentSecurityHandle handle) {
        String endpoint = handle.getUrl();
        LOG.info("Calling content safety API [{}]", endpoint);
        return WEB_CLIENT.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(SafetyCheckResponse.class);
    }

    public static class SafetyCheckResponse {
        private String code;
        private String msg;
        private SafetyCheckData data;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public SafetyCheckData getData() {
            return data;
        }

        public void setData(SafetyCheckData data) {
            this.data = data;
        }

        @Override
        public String toString() {
            return "SafetyCheckResponse{" +
                    "code='" + code + '\'' +
                    ", msg='" + msg + '\'' +
                    ", data=" + data +
                    '}';
        }
    }

    /**
     * The structure of the data field in the response
     */
    public static class SafetyCheckData {
        private List<RiskItem> promptResult;
        private String promptCategory;
        private String answer;

        private List<RiskItem> contentResult;
        private String contentCategory;

        public List<RiskItem> getPromptResult() {
            return promptResult;
        }

        public void setPromptResult(List<RiskItem> promptResult) {
            this.promptResult = promptResult;
        }

        public String getPromptCategory() {
            return promptCategory;
        }

        public void setPromptCategory(String promptCategory) {
            this.promptCategory = promptCategory;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public List<RiskItem> getContentResult() {
            return contentResult;
        }

        public void setContentResult(List<RiskItem> contentResult) {
            this.contentResult = contentResult;
        }

        public String getContentCategory() {
            return contentCategory;
        }

        public void setContentCategory(String contentCategory) {
            this.contentCategory = contentCategory;
        }

        @Override
        public String toString() {
            return "SafetyCheckData{" +
                    "promptResult=" + promptResult +
                    ", promptCategory='" + promptCategory + '\'' +
                    ", answer='" + answer + '\'' +
                    ", contentResult=" + contentResult +
                    ", contentCategory='" + contentCategory + '\'' +
                    '}';
        }
    }

    public static class SafetyCheckRequest {

        private String accessKey;
        private String accessToken;
        private String appId;
        private String prompt;
        private String content;
        private String promptSceneCode;
        private String contentSceneCode;

        public SafetyCheckRequest() {
        }

        public SafetyCheckRequest(final String accessKey,
                                  final String accessToken,
                                  final String appId,
                                  final String prompt,
                                  final String content) {
            this.accessKey = accessKey;
            this.accessToken = accessToken;
            this.appId = appId;
            this.prompt = prompt;
            this.content = content;
        }

        // prompt-only
        public SafetyCheckRequest(final String accessKey,
                                  final String accessToken,
                                  final String appId,
                                  final String prompt) {
            this(accessKey, accessToken, appId, prompt, null);
        }

        // content-only
        public static SafetyCheckRequest forContent(final String accessKey,
                                                    final String accessToken,
                                                    final String appId,
                                                    final String content) {
            return new SafetyCheckRequest(accessKey, accessToken, appId, null, content);
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(final String accessKey) {
            this.accessKey = accessKey;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(final String accessToken) {
            this.accessToken = accessToken;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(final String appId) {
            this.appId = appId;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(final String prompt) {
            this.prompt = prompt;
        }

        public String getContent() {
            return content;
        }

        public void setContent(final String content) {
            this.content = content;
        }

        public String getPromptSceneCode() {
            return promptSceneCode;
        }

        public void setPromptSceneCode(final String promptSceneCode) {
            this.promptSceneCode = promptSceneCode;
        }

        public String getContentSceneCode() {
            return contentSceneCode;
        }

        public void setContentSceneCode(final String contentSceneCode) {
            this.contentSceneCode = contentSceneCode;
        }
    }

    public static class RiskItem {
        private String label;
        private Float confidence;
        private List<String> riskWords;
        private List<RiskItem> subResult;


        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Float getConfidence() {
            return confidence;
        }

        public void setConfidence(Float confidence) {
            this.confidence = confidence;
        }

        public List<String> getRiskWords() {
            return riskWords;
        }

        public void setRiskWords(List<String> riskWords) {
            this.riskWords = riskWords;
        }

        public List<RiskItem> getSubResult() {
            return subResult;
        }

        public void setSubResult(List<RiskItem> subResult) {
            this.subResult = subResult;
        }
    }

}
