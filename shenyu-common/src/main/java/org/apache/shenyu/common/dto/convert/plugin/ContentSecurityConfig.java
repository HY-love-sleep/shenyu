package org.apache.shenyu.common.dto.convert.plugin;

import java.util.Objects;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 16:52
 */
public class ContentSecurityConfig {

    private String accessKey;
    private String accessToken;
    private String appId;
    private String url;

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
    public String getUrl() {
        return url;
    }
    public void setUrl(final String url) {
        this.url = url;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentSecurityConfig)) return false;
        ContentSecurityConfig that = (ContentSecurityConfig) o;
        return Objects.equals(accessKey, that.accessKey) &&
                Objects.equals(accessToken, that.accessToken) &&
                Objects.equals(appId, that.appId) &&
                Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessKey, accessToken, appId, url);
    }

    @Override
    public String toString() {
        return "ContentSecurityPluginConfig{" +
                "accessKey='" + accessKey + '\'' +
                ", accessToken='***'" +
                ", appId='" + appId + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
