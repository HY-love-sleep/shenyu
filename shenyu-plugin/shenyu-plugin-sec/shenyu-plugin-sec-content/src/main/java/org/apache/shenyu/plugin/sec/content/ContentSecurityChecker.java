package org.apache.shenyu.plugin.sec.content;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 15:23
 */
public class ContentSecurityChecker {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityChecker.class);
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
                                    .withCoreSize(10)
                                    .withMaximumSize(30)
                                    .withMaxQueueSize(100)
                                    .withAllowMaximumSizeToDivergeFromCoreSize(true)
                    )
                    .andCommandPropertiesDefaults(
                            HystrixCommandProperties.Setter()
                                    .withExecutionTimeoutInMilliseconds(5000)
                                    .withCircuitBreakerEnabled(true)
                                    .withCircuitBreakerRequestVolumeThreshold(10)
                                    .withCircuitBreakerErrorThresholdPercentage(50)
                                    .withCircuitBreakerSleepWindowInMilliseconds(10000)
                                    .withExecutionIsolationStrategy(
                                            HystrixCommandProperties.ExecutionIsolationStrategy.THREAD
                                    )
                    )
            );
            this.req = req;
            this.handle = handle;
        }

        @Override
        protected SafetyCheckResponse run() {
            // run in Hystrix threadPool
            SafetyCheckResponse resp = checkTextInternal(req, handle)
                    .block(Duration.ofMillis(4500));
            if (resp == null || !"200".equals(resp.getCode())) {
                throw new RuntimeException("三方接口业务失败，code=" + (resp == null ? "null" : resp.getCode()));
            }
            return resp;
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
