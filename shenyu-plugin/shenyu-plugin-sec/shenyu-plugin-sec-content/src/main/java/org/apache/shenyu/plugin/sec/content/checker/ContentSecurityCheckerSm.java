package org.apache.shenyu.plugin.sec.content.checker;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.sec.content.ContentSecurityResult;
import org.apache.shenyu.common.utils.GsonUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

/**
 * 数美内容安全检测器
 * @author yHong
 * @version 1.0
 * @since 2025/8/21 9:40
 */
public class ContentSecurityCheckerSm implements ContentSecurityChecker {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityCheckerSm.class);
    private static final WebClient WEB_CLIENT = WebClient.builder().build();

    private static final Gson GSON = new GsonBuilder()
            .create();

    /**
     * 调用数美内容安全检测API检查文本合规性
     *
     * @param req 请求体，包含检测参数
     * @param handle 配置参数
     * @return 异步Mono，产生检测结果
     */
    public static Mono<SmTextCheckResponse> checkText(final SmTextCheckRequest req, final ContentSecurityHandle handle) {
        return Mono.fromCallable(() -> new ContentSecHystrixCommand(req, handle).execute());
    }

    @Override
    public Mono<ContentSecurityResult> checkText(Object request, ContentSecurityHandle handle) {
        if (!(request instanceof SmTextCheckRequest smRequest)) {
            return Mono.error(new IllegalArgumentException("Request must be SmTextCheckRequest for Shumei vendor"));
        }

        return checkText(smRequest, handle)
                .map(this::convertToResult)
                .onErrorResume(throwable -> {
                    LOG.error("Shumei content security check error", throwable);
                    return Mono.just(ContentSecurityResult.error("shumei", 
                        throwable.getMessage(), "1500", "内容安全检测服务不可用"));
                });
    }

    @Override
    public String getVendor() {
        return "shumei";
    }

    @Override
    public boolean supports(String vendor) {
        return "shumei".equalsIgnoreCase(vendor);
    }

    /**
     * 将数美的响应转换为统一的结果格式
     */
    private ContentSecurityResult convertToResult(SmTextCheckResponse response) {
        if (response == null || response.getCode() != 1100) {
            return ContentSecurityResult.error("shumei", 
                "数美接口业务失败，code=" + (response == null ? "null" : response.getCode()), 
                response == null ? "1500" : String.valueOf(response.getCode()), 
                response == null ? "响应为空" : response.getMessage());
        }

        // 根据数美接口文档，使用riskLevel字段判断内容安全性
        String riskLevel = response.getRiskLevel();
        if (riskLevel == null) {
            return ContentSecurityResult.error("shumei", "数美响应riskLevel为空", "1500", "riskLevel为空");
        }

        // 根据数美接口返回示例：
        // - "REJECT" 表示内容有风险，需要拒绝
        // - "PASS" 表示内容正常，可以通过
        if ("REJECT".equalsIgnoreCase(riskLevel)) {
            // 存在风险
            String riskDescription = response.getRiskDescription() != null ? response.getRiskDescription() : "未知风险";
            return ContentSecurityResult.failed("shumei", riskLevel, riskDescription, response);
        } else if ("PASS".equalsIgnoreCase(riskLevel)) {
            // 无风险
            return ContentSecurityResult.passed("shumei", response);
        } else {
            // 其他情况，可能是REVIEW等状态，暂时按有风险处理
            String riskDescription = response.getRiskDescription() != null ? response.getRiskDescription() : "需要人工审核";
            return ContentSecurityResult.failed("shumei", riskLevel, riskDescription, response);
        }
    }

    // HystrixCommand
    static class ContentSecHystrixCommand extends HystrixCommand<SmTextCheckResponse> {
        private final SmTextCheckRequest req;
        private final ContentSecurityHandle handle;

        ContentSecHystrixCommand(SmTextCheckRequest req, ContentSecurityHandle handle) {
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
        protected SmTextCheckResponse run() {
            try {
                long timeout = Math.max(2000, Optional.ofNullable(handle.getTimeoutInMilliseconds()).orElse(10000) - 2000);

                CompletableFuture<SmTextCheckResponse> future = checkTextInternal(req, handle)
                        .toFuture();
                
                SmTextCheckResponse resp = future.get(timeout, TimeUnit.MILLISECONDS);
                
                // 只要响应不为null就认为网络调用成功，业务逻辑在convertToResult中处理
                if (resp == null) {
                    throw new RuntimeException("数美接口返回空响应");
                }
                return resp;
            } catch (TimeoutException e) {
                LOG.error("Shumei API call timeout", e);
                throw new RuntimeException("数美接口调用超时: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Shumei API call interrupted", e);
                throw new RuntimeException("数美接口调用被中断: " + e.getMessage(), e);
            } catch (ExecutionException e) {
                LOG.error("Shumei API call execution failed", e);
                throw new RuntimeException("数美接口调用执行失败: " + e.getMessage(), e);
            } catch (Exception e) {
                LOG.error("Shumei API call failed", e);
                throw new RuntimeException("数美接口调用失败: " + e.getMessage(), e);
            }
        }

        @Override
        protected SmTextCheckResponse getFallback() {
            LOG.warn("Content security fallback triggered by Hystrix. Command: {}, Group: {}, ThreadPool: {}", 
                    getCommandKey().name(), getCommandGroup().name(), getThreadPoolKey().name());
            
            // 记录导致fallback的原因
            if (isResponseTimedOut()) {
                LOG.warn("Fallback reason: Response timed out");
            } else if (isFailedExecution()) {
                LOG.warn("Fallback reason: Execution failed", getFailedExecutionException());
            } else if (isResponseRejected()) {
                LOG.warn("Fallback reason: Response rejected");
            } else if (isCircuitBreakerOpen()) {
                LOG.warn("Fallback reason: Circuit breaker is open");
            }
            
            SmTextCheckResponse resp = new SmTextCheckResponse();
            resp.setCode(1500);
            resp.setMessage("内容安全检测服务不可用(fallback by Hystrix)");
            return resp;
        }
    }

    // 实际调用
    private static Mono<SmTextCheckResponse> checkTextInternal(final SmTextCheckRequest req, final ContentSecurityHandle handle) {
        String endpoint = handle.getUrl();
        LOG.info("Calling Shumei content safety API [{}]", endpoint);
        
        // 使用Gson手动序列化，确保null字段不被包含
        String requestBody = GSON.toJson(req);
        LOG.debug("Shumei API request: {}", requestBody);
        
        return WEB_CLIENT.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(responseBody -> {
                    try {
                        LOG.info("Shumei API response: {}", responseBody);
                        // 使用Gson手动解析JSON
                        return GsonUtils.getInstance().fromJson(responseBody, SmTextCheckResponse.class);
                    } catch (Exception e) {
                        LOG.error("Failed to parse Shumei API response: {}", responseBody, e);
                        throw new RuntimeException("Failed to parse Shumei API response: " + e.getMessage(), e);
                    }
                });
    }

    /**
     * 数美文本检测请求参数
     */
    public static class SmTextCheckRequest {
        private String accessKey;
        private String appId;
        private String eventId;
        private String type;
        private SmTextCheckData data;

        public SmTextCheckRequest() {
        }

        public SmTextCheckRequest(final String accessKey, final String appId, final String eventId, 
                                final String type, final String text, final String tokenId) {
            this.accessKey = accessKey;
            this.appId = appId;
            this.eventId = eventId;
            this.type = type;
            this.data = new SmTextCheckData();
            this.data.setText(text);
            this.data.setTokenId(tokenId);
        }

        // 仅检测文本
        public static SmTextCheckRequest forText(final String accessKey, final String appId, 
                                              final String eventId, final String type, 
                                              final String text, final String tokenId) {
            return new SmTextCheckRequest(accessKey, appId, eventId, type, text, tokenId);
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public SmTextCheckData getData() {
            return data;
        }

        public void setData(SmTextCheckData data) {
            this.data = data;
        }
    }

    /**
     * 数美文本检测数据参数
     */
    public static class SmTextCheckData {
        private String text;
        private String relateText;
        private String tokenId;
        private String ip;
        private String deviceId;
        private String nickname;
        private SmTextCheckExtra extra;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getRelateText() {
            return relateText;
        }

        public void setRelateText(String relateText) {
            this.relateText = relateText;
        }

        public String getTokenId() {
            return tokenId;
        }

        public void setTokenId(String tokenId) {
            this.tokenId = tokenId;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public SmTextCheckExtra getExtra() {
            return extra;
        }

        public void setExtra(SmTextCheckExtra extra) {
            this.extra = extra;
        }
    }

    /**
     * 数美文本检测扩展参数
     */
    public static class SmTextCheckExtra {
        private String topic;
        private String atId;
        private String room;
        private String receiveTokenId;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getAtId() {
            return atId;
        }

        public void setAtId(String atId) {
            this.atId = atId;
        }

        public String getRoom() {
            return room;
        }

        public void setRoom(String room) {
            this.room = room;
        }

        public String getReceiveTokenId() {
            return receiveTokenId;
        }

        public void setReceiveTokenId(String receiveTokenId) {
            this.receiveTokenId = receiveTokenId;
        }
    }

    /**
     * 数美文本检测响应
     */
    public static class SmTextCheckResponse {
        private List<SmTextCheckLabel> allLabels;
        private SmTextCheckAuxInfo auxInfo;
        private List<Object> businessLabels;
        private Integer code;
        private String message;
        private Integer finalResult;
        private Integer resultType;
        private String requestId;
        private String riskDescription;
        private SmTextCheckRiskDetail riskDetail;
        private String riskLabel1;
        private String riskLabel2;
        private String riskLabel3;
        private String riskLevel;

        public List<SmTextCheckLabel> getAllLabels() {
            return allLabels;
        }

        public void setAllLabels(List<SmTextCheckLabel> allLabels) {
            this.allLabels = allLabels;
        }

        public SmTextCheckAuxInfo getAuxInfo() {
            return auxInfo;
        }

        public void setAuxInfo(SmTextCheckAuxInfo auxInfo) {
            this.auxInfo = auxInfo;
        }

        public List<Object> getBusinessLabels() {
            return businessLabels;
        }

        public void setBusinessLabels(List<Object> businessLabels) {
            this.businessLabels = businessLabels;
        }

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

        public Integer getFinalResult() {
            return finalResult;
        }

        public void setFinalResult(Integer finalResult) {
            this.finalResult = finalResult;
        }

        public Integer getResultType() {
            return resultType;
        }

        public void setResultType(Integer resultType) {
            this.resultType = resultType;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getRiskDescription() {
            return riskDescription;
        }

        public void setRiskDescription(String riskDescription) {
            this.riskDescription = riskDescription;
        }

        public SmTextCheckRiskDetail getRiskDetail() {
            return riskDetail;
        }

        public void setRiskDetail(SmTextCheckRiskDetail riskDetail) {
            this.riskDetail = riskDetail;
        }

        public String getRiskLabel1() {
            return riskLabel1;
        }

        public void setRiskLabel1(String riskLabel1) {
            this.riskLabel1 = riskLabel1;
        }

        public String getRiskLabel2() {
            return riskLabel2;
        }

        public void setRiskLabel2(String riskLabel2) {
            this.riskLabel2 = riskLabel2;
        }

        public String getRiskLabel3() {
            return riskLabel3;
        }

        public void setRiskLabel3(String riskLabel3) {
            this.riskLabel3 = riskLabel3;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }
    }

    /**
     * 数美文本检测标签
     */
    public static class SmTextCheckLabel {
        private Double probability;
        private String riskDescription;
        private SmTextCheckRiskDetail riskDetail;
        private String riskLabel1;
        private String riskLabel2;
        private String riskLabel3;
        private String riskLevel;

        public Double getProbability() {
            return probability;
        }

        public void setProbability(Double probability) {
            this.probability = probability;
        }

        public String getRiskDescription() {
            return riskDescription;
        }

        public void setRiskDescription(String riskDescription) {
            this.riskDescription = riskDescription;
        }

        public SmTextCheckRiskDetail getRiskDetail() {
            return riskDetail;
        }

        public void setRiskDetail(SmTextCheckRiskDetail riskDetail) {
            this.riskDetail = riskDetail;
        }

        public String getRiskLabel1() {
            return riskLabel1;
        }

        public void setRiskLabel1(String riskLabel1) {
            this.riskLabel1 = riskLabel1;
        }

        public String getRiskLabel2() {
            return riskLabel2;
        }

        public void setRiskLabel2(String riskLabel2) {
            this.riskLabel2 = riskLabel2;
        }

        public String getRiskLabel3() {
            return riskLabel3;
        }

        public void setRiskLabel3(String riskLabel3) {
            this.riskLabel3 = riskLabel3;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }
    }

    /**
     * 数美文本检测风险详情
     */
    public static class SmTextCheckRiskDetail {
        private List<SmTextCheckMatchedList> matchedLists;
        private List<SmTextCheckRiskSegment> riskSegments;

        public List<SmTextCheckMatchedList> getMatchedLists() {
            return matchedLists;
        }

        public void setMatchedLists(List<SmTextCheckMatchedList> matchedLists) {
            this.matchedLists = matchedLists;
        }

        public List<SmTextCheckRiskSegment> getRiskSegments() {
            return riskSegments;
        }

        public void setRiskSegments(List<SmTextCheckRiskSegment> riskSegments) {
            this.riskSegments = riskSegments;
        }
    }

    /**
     * 数美文本检测匹配名单
     */
    public static class SmTextCheckMatchedList {
        private String name;
        private List<SmTextCheckWord> words;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<SmTextCheckWord> getWords() {
            return words;
        }

        public void setWords(List<SmTextCheckWord> words) {
            this.words = words;
        }
    }

    /**
     * 数美文本检测敏感词
     */
    public static class SmTextCheckWord {
        private String word;
        private List<Integer> position;

        public String getWord() {
            return word;
        }

        public void setWord(String word) {
            this.word = word;
        }

        public List<Integer> getPosition() {
            return position;
        }

        public void setPosition(List<Integer> position) {
            this.position = position;
        }
    }

    /**
     * 数美文本检测风险片段
     */
    public static class SmTextCheckRiskSegment {
        private String segment;
        private List<Integer> position;

        public String getSegment() {
            return segment;
        }

        public void setSegment(String segment) {
            this.segment = segment;
        }

        public List<Integer> getPosition() {
            return position;
        }

        public void setPosition(List<Integer> position) {
            this.position = position;
        }
    }

    /**
     * 数美文本检测辅助信息
     */
    public static class SmTextCheckAuxInfo {
        private List<SmTextCheckContactResult> contactResult;
        private String filteredText;

        public List<SmTextCheckContactResult> getContactResult() {
            return contactResult;
        }

        public void setContactResult(List<SmTextCheckContactResult> contactResult) {
            this.contactResult = contactResult;
        }

        public String getFilteredText() {
            return filteredText;
        }

        public void setFilteredText(String filteredText) {
            this.filteredText = filteredText;
        }
    }

    /**
     * 数美文本检测联系方式结果
     */
    public static class SmTextCheckContactResult {
        private String contactString;
        private Integer contactType;

        public String getContactString() {
            return contactString;
        }

        public void setContactString(String contactString) {
            this.contactString = contactString;
        }

        public Integer getContactType() {
            return contactType;
        }

        public void setContactType(Integer contactType) {
            this.contactType = contactType;
        }
    }
}
