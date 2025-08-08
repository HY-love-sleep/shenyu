package org.apache.shenyu.common.dto.convert.plugin;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/8/8 10:32
 */
public class ContentMarkConfig {
    private String url;
    // docking watermark interface, although I don't know what the point of adding these parameters is...
    private String modelName;
    private String applicationName;
    private String serviceProvider;
    private String serviceUser;
    private Integer timeoutMs;
    // obligate
    private String accessKey;
    private String accessToken;

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

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
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
}
