package org.apache.shenyu.common.dto.convert.rule;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/21 17:23
 */
public class ContentMarkHandle {
    private String url;
    private Integer timeoutMs;
    // obligate
    private String accessKey;
    private String accessToken;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String toString() {
        return "ContentMarkHandle{" +
                "url='" + url + '\'' +
                ", timeoutMs=" + timeoutMs +
                ", accessKey='" + accessKey + '\'' +
                ", accessToken='" + accessToken + '\'' +
                '}';
    }
}
