package org.apache.shenyu.plugin.sec.sensitive;

import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.dto.convert.rule.SensitiveSecurityHandle;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.plugin.api.exception.ResponsiveException;
import org.apache.shenyu.plugin.api.utils.WebFluxResultUtils;
import org.apache.shenyu.plugin.base.AbstractShenyuPlugin;
import org.apache.shenyu.plugin.base.cache.CommonHandleCache;
import org.apache.shenyu.plugin.base.utils.BeanHolder;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.plugin.base.utils.ServerWebExchangeUtils;
import org.apache.shenyu.plugin.sec.sensitive.ac.AhoCorasick;
import org.apache.shenyu.plugin.sec.sensitive.handler.SensitiveSecurityPluginDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


import java.util.List;
import java.util.function.Supplier;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 15:02
 */
public class SensitiveSecurityPlugin extends AbstractShenyuPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(SensitiveSecurityPlugin.class);

    /**
     * 缓存 Aho-Corasick 敏感词树，key 为插件名。
     */
    private static final Supplier<CommonHandleCache<String, AhoCorasick>> AC_TREES =
            new BeanHolder<>(CommonHandleCache::new);

    private final List<HttpMessageReader<?>> readers;

    public SensitiveSecurityPlugin(final List<HttpMessageReader<?>> readers) {
        this.readers = readers;
    }

    @Override
    protected Mono<Void> doExecute(final ServerWebExchange exchange,
                                   final ShenyuPluginChain chain,
                                   final SelectorData selector,
                                   final RuleData rule) {

        SensitiveSecurityHandle handle = SensitiveSecurityPluginDataHandler.CACHED_HANDLE
                .get().obtainHandle(CacheKeyUtils.INST.getKey(rule));
        if (handle == null) {
            return chain.execute(exchange);
        }
        String redisKey = handle.getRedisKey();

        // 2) get cached ReactiveRedisTemplate
        ReactiveRedisTemplate<String, String> redisTemplate =
                SensitiveSecurityPluginDataHandler.REDIS_TEMPLATES
                        .get()
                        .obtainHandle(PluginEnum.SENSITIVE_SECURITY.getName());
        if (redisTemplate == null) {
            LOG.error("ReactiveRedisTemplate not available for SensitiveSecurityPlugin");
            return chain.execute(exchange);
        }

        // 3) get body async
        return ServerWebExchangeUtils.rewriteRequestBody(exchange, readers, promptBody -> {
                    // use cached ac tree
                    AhoCorasick tree = AC_TREES.get().obtainHandle(PluginEnum.SENSITIVE_SECURITY.getName());
                    if (tree != null) {
                        List<String> hits = tree.search(promptBody);
                        if (!hits.isEmpty()) {
                            String err = String.format(
                                    "{\"code\":1500,\"msg\":\"请求包含敏感词\",\"detail\":%s}",
                                    hits);
                            return Mono.error(new ResponsiveException(1500, err, exchange));
                        }
                        return Mono.just(promptBody);
                    }
                    // build ac tree from redis and cache it
                    return redisTemplate.opsForSet()
                            .members(redisKey)
                            .collectList()
                            .flatMap(keywords -> {
                                AhoCorasick ac = new AhoCorasick();
                                keywords.forEach(ac::insert);
                                ac.build();
                                AC_TREES.get().cachedHandle(PluginEnum.SENSITIVE_SECURITY.getName(), ac);
                                List<String> hits = ac.search(promptBody);
                                if (!hits.isEmpty()) {
                                    // todo: this is a sample
                                    String err = String.format(
                                            "{\"code\":1500,\"msg\":\"请求包含敏感词\",\"detail\":%s}",
                                            hits);
                                    return Mono.error(new ResponsiveException(1500, err, exchange));
                                }
                                return Mono.just(promptBody);
                            });
                })
                // Call downstream with the Exchange of the "cached body".
                .flatMap(chain::execute)
                // Unified exception handling
                .onErrorResume(e -> {
                    if (e instanceof ResponsiveException) {
                        return WebFluxResultUtils.failedResult((ResponsiveException) e);
                    }
                    return Mono.error(e);
                });
    }

    @Override
    public String named() {
        return PluginEnum.SENSITIVE_SECURITY.getName();
    }

    @Override
    public int getOrder() {
        // before aiProxy and ContentSecurityPlugin
        return PluginEnum.SENSITIVE_SECURITY.getCode();
    }
}
