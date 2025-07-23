package org.apache.shenyu.plugin.sec.mark;

import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.dto.convert.rule.ContentMarkHandle;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.plugin.base.AbstractShenyuPlugin;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.plugin.sec.mark.decorator.ContentMarkResponseDecorator;
import org.apache.shenyu.plugin.sec.mark.handler.ContentMarkPluginDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/21 17:52
 */
public class ContentMarkPlugin extends AbstractShenyuPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(ContentMarkPlugin.class);
    private final List<HttpMessageReader<?>> messageReaders;

    public ContentMarkPlugin(final List<HttpMessageReader<?>> readers) {
        this.messageReaders = readers;
    }

    @Override
    protected Mono<Void> doExecute(final ServerWebExchange exchange,
                                   final ShenyuPluginChain chain,
                                   final SelectorData selector,
                                   final RuleData rule) {
        ContentMarkHandle handle = ContentMarkPluginDataHandler.CACHED_HANDLE
                .get().obtainHandle(CacheKeyUtils.INST.getKey(rule));
        if (handle == null) {
            LOG.info("ContentMarkPlugin: handle not found, skip.");
            return chain.execute(exchange);
        }

        // wrap response
        ServerWebExchange mutated = exchange.mutate()
                .response(new ContentMarkResponseDecorator(exchange, handle))
                .build();
        return chain.execute(mutated);
    }

    @Override
    public String named() {
        return PluginEnum.CONTENT_MARK.getName();
    }

    @Override
    public int getOrder() {
        return PluginEnum.CONTENT_MARK.getCode();
    }

}
