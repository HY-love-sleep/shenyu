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
    // hystrix thread pool
    private Integer hystrixThreadPoolCoreSize;
    private Integer hystrixThreadPoolMaxSize;
    private Integer hystrixThreadPoolQueueCapacity;
    private Boolean allowMaximumSizeToDivergeFromCoreSize;
    // hystrix command
    private Integer timeoutInMilliseconds;
    private Boolean enabled;
    private Integer statisticalWindow;
    private Integer breakerRequestVolumeThreshold;
    private Integer breakerErrorThresholdPercentage;
    private Integer breakerSleepWindowInMilliseconds;

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

    public Integer getHystrixThreadPoolCoreSize() {
        return hystrixThreadPoolCoreSize;
    }

    public void setHystrixThreadPoolCoreSize(Integer hystrixThreadPoolCoreSize) {
        this.hystrixThreadPoolCoreSize = hystrixThreadPoolCoreSize;
    }

    public Integer getHystrixThreadPoolMaxSize() {
        return hystrixThreadPoolMaxSize;
    }

    public void setHystrixThreadPoolMaxSize(Integer hystrixThreadPoolMaxSize) {
        this.hystrixThreadPoolMaxSize = hystrixThreadPoolMaxSize;
    }

    public Integer getHystrixThreadPoolQueueCapacity() {
        return hystrixThreadPoolQueueCapacity;
    }

    public void setHystrixThreadPoolQueueCapacity(Integer hystrixThreadPoolQueueCapacity) {
        this.hystrixThreadPoolQueueCapacity = hystrixThreadPoolQueueCapacity;
    }

    public Boolean getAllowMaximumSizeToDivergeFromCoreSize() {
        return allowMaximumSizeToDivergeFromCoreSize;
    }

    public void setAllowMaximumSizeToDivergeFromCoreSize(Boolean allowMaximumSizeToDivergeFromCoreSize) {
        this.allowMaximumSizeToDivergeFromCoreSize = allowMaximumSizeToDivergeFromCoreSize;
    }

    public Integer getTimeoutInMilliseconds() {
        return timeoutInMilliseconds;
    }

    public void setTimeoutInMilliseconds(Integer timeoutInMilliseconds) {
        this.timeoutInMilliseconds = timeoutInMilliseconds;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getStatisticalWindow() {
        return statisticalWindow;
    }

    public void setStatisticalWindow(Integer statisticalWindow) {
        this.statisticalWindow = statisticalWindow;
    }

    public Integer getBreakerRequestVolumeThreshold() {
        return breakerRequestVolumeThreshold;
    }

    public void setBreakerRequestVolumeThreshold(Integer breakerRequestVolumeThreshold) {
        this.breakerRequestVolumeThreshold = breakerRequestVolumeThreshold;
    }

    public Integer getBreakerErrorThresholdPercentage() {
        return breakerErrorThresholdPercentage;
    }

    public void setBreakerErrorThresholdPercentage(Integer breakerErrorThresholdPercentage) {
        this.breakerErrorThresholdPercentage = breakerErrorThresholdPercentage;
    }

    public Integer getBreakerSleepWindowInMilliseconds() {
        return breakerSleepWindowInMilliseconds;
    }

    public void setBreakerSleepWindowInMilliseconds(Integer breakerSleepWindowInMilliseconds) {
        this.breakerSleepWindowInMilliseconds = breakerSleepWindowInMilliseconds;
    }

    @Override
    public String toString() {
        return "ContentSecurityHandle{" +
                "accessKey='" + accessKey + '\'' +
                ", accessToken='" + accessToken + '\'' +
                ", appId='" + appId + '\'' +
                ", url='" + url + '\'' +
                ", hystrixThreadPoolCoreSize=" + hystrixThreadPoolCoreSize +
                ", hystrixThreadPoolMaxSize=" + hystrixThreadPoolMaxSize +
                ", hystrixThreadPoolQueueCapacity=" + hystrixThreadPoolQueueCapacity +
                ", allowMaximumSizeToDivergeFromCoreSize=" + allowMaximumSizeToDivergeFromCoreSize +
                ", timeoutInMilliseconds=" + timeoutInMilliseconds +
                ", enabled=" + enabled +
                ", statisticalWindow=" + statisticalWindow +
                ", breakerRequestVolumeThreshold=" + breakerRequestVolumeThreshold +
                ", breakerErrorThresholdPercentage=" + breakerErrorThresholdPercentage +
                ", breakerSleepWindowInMilliseconds=" + breakerSleepWindowInMilliseconds +
                '}';
    }
}

