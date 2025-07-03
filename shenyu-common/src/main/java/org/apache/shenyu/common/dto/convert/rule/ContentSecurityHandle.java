package org.apache.shenyu.common.dto.convert.rule;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 15:20
 */
public class ContentSecurityHandle {
    private String accessKey;
    private String accessToken;
    private String appId;
    private String url;
    // todo: add other field. like timeout

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

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Override
    public String toString() {
        return "ContentSecurityHandle{accessKey='" + accessKey +
                "', accessToken='***', appId='" + appId + "'}";
    }
}

