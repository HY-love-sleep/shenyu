package org.apache.shenyu.plugin.sec.mark;

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
        String endPoint = handle.getUrl();
        LOG.info("Calling watermark API:{}, contentLen={}", endPoint, request.getContent().length());

        return WEB_CLIENT.post()
                .uri(endPoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TextMarkResponse.class)
                .timeout(Duration.ofSeconds(handle.getTimeoutMs()))
                .doOnNext(resp -> {
                    LOG.info("watermark result:{}", resp);
                    Data data = resp.getData();
                    if (data != null) {
                        LOG.info("waterMarkDarkInfo:{}", data.getWaterMarkDarkInfo());
                    }
                })
                .doOnError(e -> LOG.error("call watermark API failed:{}", e.getMessage()))
                .onErrorResume(e -> {
                    LOG.warn("Watermark API error or timeout, fallback to original content. Error: {}", e.getMessage());
                    TextMarkResponse fallback = new TextMarkResponse();
                    fallback.setCode(-1);
                    fallback.setMessage("Watermark API error, content not marked: " + e.getMessage());
                    fallback.setSuccess(false);
                    Data data = new Data();
                    data.setContent(request.getContent());
                    data.setWaterMarkDarkInfo(null);
                    fallback.setData(data);
                    return Mono.just(fallback);
                });
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
