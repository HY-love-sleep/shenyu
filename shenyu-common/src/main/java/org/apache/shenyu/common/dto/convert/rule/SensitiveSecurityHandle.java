package org.apache.shenyu.common.dto.convert.rule;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/7 10:38
 */
public class SensitiveSecurityHandle {

    /**
     * Redis holds the key of the collection of sensitive words.
     */
    private String redisKey = "shenyu:sensitive:words";

    public String getRedisKey() {
        return redisKey;
    }

    public void setRedisKey(final String redisKey) {
        this.redisKey = redisKey;
    }

    /**
     * Provide a default instance that is available when the Admin-side is not configured.
     */
    public static SensitiveSecurityHandle newDefaultInstance() {
        return new SensitiveSecurityHandle();
    }
}
