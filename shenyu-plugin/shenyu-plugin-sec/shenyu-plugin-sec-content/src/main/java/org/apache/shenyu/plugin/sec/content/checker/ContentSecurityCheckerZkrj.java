package org.apache.shenyu.plugin.sec.content.checker;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.sec.content.ContentSecurityResult;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 15:23
 */
public class ContentSecurityCheckerZkrj implements ContentSecurityChecker {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityCheckerZkrj.class);
    //  reuse a singleton WebClient
    private static final WebClient WEB_CLIENT = WebClient.builder().build();

    /**
     * Call a third-party content security detection API to check the compliance of a given text.
     *
     * @param req The body of the request to be sent, including prompt and content
     * @return Asynchronous Mono, which produces a SafetyCheckResponse test result
     */
    public static Mono<SafetyCheckResponse> checkText(final SafetyCheckRequest req, final ContentSecurityHandle handle) {
        return Mono.fromCallable(() -> new ContentSecHystrixCommand(req, handle).execute());
    }

    @Override
    public Mono<ContentSecurityResult> checkText(Object request, ContentSecurityHandle handle) {
        if (!(request instanceof SafetyCheckRequest zkrjRequest)) {
            return Mono.error(new IllegalArgumentException("Request must be SafetyCheckRequest for ZKRJ vendor"));
        }

        return checkText(zkrjRequest, handle)
                .map(this::convertToResult)
                .onErrorResume(throwable -> {
                    LOG.error("ZKRJ content security check error", throwable);
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
                "ZKRJ接口业务失败，code=" + (response == null ? "null" : response.getCode()), 
                response == null ? "1500" : response.getCode(), 
                response == null ? "响应为空" : response.getMsg());
        }

        SafetyCheckData data = response.getData();
        if (data == null) {
            return ContentSecurityResult.error("zkrj", "ZKRJ响应数据为空", "1500", "响应数据为空");
        }

        String promptCategory = data.getPromptCategory();
        if ("违规".equals(promptCategory) || "疑似".equals(promptCategory)) {
            return ContentSecurityResult.failed("zkrj", promptCategory, 
                "检测结果：" + promptCategory, response);
        } else {
            return ContentSecurityResult.passed("zkrj", response);
        }
    }

    // HystrixCommand
    static class ContentSecHystrixCommand extends HystrixCommand<SafetyCheckResponse> {
        private final SafetyCheckRequest req;
        private final ContentSecurityHandle handle;

        ContentSecHystrixCommand(SafetyCheckRequest req, ContentSecurityHandle handle) {
            super(Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory.asKey("ContentSecurity"))
                    .andCommandKey(HystrixCommandKey.Factory.asKey("CheckText"))
                    .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("ContentSecurityPool"))
                    .andThreadPoolPropertiesDefaults(
                            HystrixThreadPoolProperties.Setter()
                                    .withCoreSize(Optional.ofNullable(handle.getHystrixThreadPoolCoreSize()).orElse(20))
                                    .withMaximumSize(Optional.ofNullable(handle.getHystrixThreadPoolMaxSize()).orElse(50))
                                    .withMaxQueueSize(Optional.ofNullable(handle.getHystrixThreadPoolQueueCapacity()).orElse(200))
                                    .withAllowMaximumSizeToDivergeFromCoreSize(Optional.ofNullable(handle.getAllowMaximumSizeToDivergeFromCoreSize()).orElse(Boolean.TRUE))
                                    .withKeepAliveTimeMinutes(1)
                                    .withQueueSizeRejectionThreshold(200)
                    )
                    .andCommandPropertiesDefaults(
                            HystrixCommandProperties.Setter()
                                    .withMetricsRollingStatisticalWindowInMilliseconds(Optional.ofNullable(handle.getBreakerSleepWindowInMilliseconds()).orElse(10000))
                                    .withExecutionTimeoutInMilliseconds(Optional.ofNullable(handle.getTimeoutInMilliseconds()).orElse(10000))
                                    .withCircuitBreakerEnabled(Optional.ofNullable(handle.getEnabled()).orElse(Boolean.TRUE))
                                    .withCircuitBreakerRequestVolumeThreshold(Optional.ofNullable(handle.getBreakerRequestVolumeThreshold()).orElse(30))
                                    .withCircuitBreakerErrorThresholdPercentage(Optional.ofNullable(handle.getBreakerErrorThresholdPercentage()).orElse(70))
                                    .withCircuitBreakerSleepWindowInMilliseconds(Optional.ofNullable(handle.getBreakerSleepWindowInMilliseconds()).orElse(15000))
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
        }

        @Override
        protected SafetyCheckResponse run() {
            try {
                long timeout = Math.max(2000, Optional.ofNullable(handle.getTimeoutInMilliseconds()).orElse(10000) - 2000);

                CompletableFuture<SafetyCheckResponse> future = checkTextInternal(req, handle)
                        .toFuture();
                
                SafetyCheckResponse resp = future.get(timeout, TimeUnit.MILLISECONDS);
                
                if (resp == null || !"200".equals(resp.getCode())) {
                    throw new RuntimeException("三方接口业务失败，code=" + (resp == null ? "null" : resp.getCode()));
                }
                return resp;
            } catch (TimeoutException e) {
                LOG.error("ZKRJ API call timeout", e);
                throw new RuntimeException("ZKRJ接口调用超时: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("ZKRJ API call interrupted", e);
                throw new RuntimeException("ZKRJ接口调用被中断: " + e.getMessage(), e);
            } catch (ExecutionException e) {
                LOG.error("ZKRJ API call execution failed", e);
                throw new RuntimeException("ZKRJ接口调用执行失败: " + e.getMessage(), e);
            } catch (Exception e) {
                LOG.error("ZKRJ API call failed", e);
                throw new RuntimeException("ZKRJ接口调用失败: " + e.getMessage(), e);
            }
        }

        @Override
        protected SafetyCheckResponse getFallback() {
            LOG.warn("Content security fallback by Hystrix.");
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
