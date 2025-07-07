package org.apache.shenyu.common.dto.convert.rule;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/7 10:38
 */
public class SensitiveSecurityHandle {

    /**
     * Redis 中存敏感词集合的 key。
     */
    private String redisKey = "shenyu:sensitive:words";

    public String getRedisKey() {
        return redisKey;
    }

    public void setRedisKey(final String redisKey) {
        this.redisKey = redisKey;
    }

    /**
     * 提供一个默认实例，Admin-端未配置时可用。
     */
    public static SensitiveSecurityHandle newDefaultInstance() {
        return new SensitiveSecurityHandle();
    }
}
