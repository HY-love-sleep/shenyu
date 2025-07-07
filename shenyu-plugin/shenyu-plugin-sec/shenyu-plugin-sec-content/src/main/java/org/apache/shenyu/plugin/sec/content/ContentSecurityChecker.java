package org.apache.shenyu.plugin.sec.content;

import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 15:23
 */
public class ContentSecurityChecker {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityChecker.class);
    // 可以复用单例 WebClient
    private static final WebClient WEB_CLIENT = WebClient.builder().build();

    /**
     * 调用第三方内容安全检测接口，检查给定文本的合规性。
     *
     * @param req 要发送的请求体，包括 prompt 和 content
     * @return 异步 Mono，产生 SafetyCheckResponse 检测结果
     */
    public static Mono<SafetyCheckResponse> checkText(final SafetyCheckRequest req, final ContentSecurityHandle handle) {
        String endpoint = handle.getUrl();
        LOG.info("Calling content safety API [{}], promptLen={} contentLen={}",
                endpoint,
                req.getPrompt() != null ? req.getPrompt().length() : 0,
                req.getContent() != null ? req.getContent().length() : 0);
        return WEB_CLIENT.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(SafetyCheckResponse.class)
                .doOnNext(resp -> {
                    LOG.info("zkrj res:{}", resp);
                    SafetyCheckData d = resp.getData();
                    if (d != null) {
                        LOG.info("=> promptCategory={} contentCategory={}",
                                d.getPromptCategory(), d.getContentCategory());
                    }
                })
                .doOnError(e -> LOG.error("safety-check error: {}", e.getMessage()));

    }

    public static class SafetyCheckResponse {
        // 接口返回的 code 是字符串形式的 "200"、"401" 等
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
     * 响应中 data 字段的结构
     */
    public static class SafetyCheckData {
        // 用户指令检测结果列表
        private List<RiskItem> promptResult;
        // 最终分类："合规" / "疑似" / "违规"
        private String promptCategory;
        // 对于“违规”或“疑似”指令的正向答复
        private String answer;

        // 生成内容检测结果列表
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
    }

    public static class SafetyCheckRequest {

        // 必填：接口凭证
        private String accessKey;
        private String accessToken;
        private String appId;
        // 用户指令
        private String prompt;
        // 模型生成内容
        private String content;
        // 可选：场景编码
        private String promptSceneCode;
        private String contentSceneCode;

        public SafetyCheckRequest() {
        }

        // 通用构造器
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

        // prompt-only 构造
        public SafetyCheckRequest(final String accessKey,
                                  final String accessToken,
                                  final String appId,
                                  final String prompt) {
            this(accessKey, accessToken, appId, prompt, null);
        }

        // content-only 构造
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
