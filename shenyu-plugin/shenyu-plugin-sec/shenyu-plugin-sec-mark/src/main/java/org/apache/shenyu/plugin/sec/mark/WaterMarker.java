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
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/23 14:28
 */
public class WaterMarker {
    private static final Logger LOG = LoggerFactory.getLogger(WaterMarker.class);
    private static final WebClient WEB_CLIENT = WebClient.builder().build();

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
            this.request = request;
            this.handle = handle;
        }

        @Override
        protected TextMarkResponse run() {
            try {
                return addMarkForTextInternal(request, handle)
                        .block(Duration.ofMillis(4900));
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
