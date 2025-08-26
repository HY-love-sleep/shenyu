package org.apache.shenyu.plugin.sec.content;

import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.plugin.base.AbstractShenyuPlugin;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.plugin.api.exception.ResponsiveException;
import org.apache.shenyu.plugin.api.utils.WebFluxResultUtils;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.plugin.sec.content.decorator.ContentSecurityRequestDecorator;
import org.apache.shenyu.plugin.sec.content.decorator.ContentSecurityResponseDecorator;
import org.apache.shenyu.plugin.sec.content.handler.ContentSecurityPluginDataHandler;
import org.apache.shenyu.plugin.sec.content.ContentSecurityService;
import org.apache.shenyu.plugin.sec.content.ContentSecurityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 14:25
 */
public class ContentSecurityPlugin extends AbstractShenyuPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityPlugin.class);
    private final List<HttpMessageReader<?>> messageReaders;
    private final ContentSecurityService contentSecurityService;

    public ContentSecurityPlugin(final List<HttpMessageReader<?>> readers, final ContentSecurityService contentSecurityService) {
        this.messageReaders = readers;
        this.contentSecurityService = contentSecurityService;
    }

    @Override
    protected Mono<Void> doExecute(ServerWebExchange exchange,
                                   ShenyuPluginChain chain,
                                   SelectorData selector,
                                   RuleData rule) {
        ContentSecurityHandle handle = ContentSecurityPluginDataHandler.CACHED_HANDLE
                .get().obtainHandle(CacheKeyUtils.INST.getKey(rule));
        if (handle == null) {
            LOG.info("ContentSecurityPlugin check by rule error, rule is null, pass from the plugin.");
            return chain.execute(exchange);
        }

        // pre check
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String promptBody = new String(bytes, StandardCharsets.UTF_8);

                    return contentSecurityService.checkText(promptBody, handle, "input")
                            .flatMap(resp -> {
                                if (!resp.isSuccess()) {
                                    String err = String.format(
                                            "{\"code\":1400,\"msg\":\"内容安全检测服务异常\",\"detail\":\"%s\"}", resp.getErrorMessage());
                                    exchange.getAttributes().put("SEC_ERROR", true);
                                    return WebFluxResultUtils.failedResult(
                                            new ResponsiveException(1400, err, exchange));
                                }
                                
                                if (!resp.isPassed()) {
                                    String err = String.format(
                                            "{\"code\":1400,\"msg\":\"内容不符合规范\",\"detail\":\"检测结果：%s，厂商：%s\"}", 
                                            resp.getRiskDescription(), resp.getVendor());
                                    exchange.getAttributes().put("SEC_ERROR", true);
                                    return WebFluxResultUtils.failedResult(
                                            new ResponsiveException(1400, err, exchange));
                                }
                                // post check
                                ServerWebExchange mutated = exchange.mutate()
                                        .request(new ContentSecurityRequestDecorator(exchange, bytes))
                                        .response(new ContentSecurityResponseDecorator(exchange, handle, contentSecurityService))
                                        .build();
                                return chain.execute(mutated);
                            });
                });

    }

    @Override
    public String named() {
        return PluginEnum.CONTENT_SECURITY.getName();
    }

    @Override
    public int getOrder() {
        return PluginEnum.CONTENT_SECURITY.getCode();
    }
}
