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
    public int getOrder() {
        // 确保在 aiProxy 之前、比 ContentSecurityPlugin 更前
        return PluginEnum.AI_PROXY.getCode() - 2;
    }

    @Override
    protected Mono<Void> doExecute(final ServerWebExchange exchange,
                                   final ShenyuPluginChain chain,
                                   final SelectorData selector,
                                   final RuleData rule) {

        // 1) 拿规则级配置
        SensitiveSecurityHandle handle = SensitiveSecurityPluginDataHandler.CACHED_HANDLE
                .get().obtainHandle(CacheKeyUtils.INST.getKey(rule));
        if (handle == null) {
            return chain.execute(exchange);
        }
        String redisKey = handle.getRedisKey();

        // 2) 拿插件级缓存的 ReactiveRedisTemplate
        ReactiveRedisTemplate<String, String> redisTemplate =
                SensitiveSecurityPluginDataHandler.REDIS_TEMPLATES
                        .get()
                        .obtainHandle(PluginEnum.SENSITIVE_SECURITY.getName());
        if (redisTemplate == null) {
            LOG.error("ReactiveRedisTemplate not available for SensitiveSecurityPlugin");
            return chain.execute(exchange);
        }

        // 3) 前置：异步读取 body，做 AC 树匹配
        return ServerWebExchangeUtils.rewriteRequestBody(exchange, readers, promptBody -> {
                    // 3.1) 看缓存里有没有已建好的树
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
                    // 3.2) 第一次：拉全量词表、构建树并缓存
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
                                    String err = String.format(
                                            "{\"code\":1500,\"msg\":\"请求包含敏感词\",\"detail\":%s}",
                                            hits);
                                    return Mono.error(new ResponsiveException(1500, err, exchange));
                                }
                                return Mono.just(promptBody);
                            });
                })
                // 4) 用“已缓存 body”的 exchange 调用下游
                .flatMap(chain::execute)
                // 5) 统一异常处理
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
}
