package org.apache.shenyu.plugin.sec.content;

import org.apache.shenyu.common.constant.Constants;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.plugin.base.AbstractShenyuPlugin;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.plugin.api.exception.ResponsiveException;
import org.apache.shenyu.plugin.api.utils.WebFluxResultUtils;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;
import org.apache.shenyu.plugin.base.utils.ResponseUtils;
import org.apache.shenyu.plugin.base.utils.ServerWebExchangeUtils;
import org.apache.shenyu.plugin.sec.content.handler.ContentSecurityPluginDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 14:25
 */
public class ContentSecurityPlugin extends AbstractShenyuPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityPlugin.class);
    private final List<HttpMessageReader<?>> messageReaders;

    public ContentSecurityPlugin(final List<HttpMessageReader<?>> readers) {
        this.messageReaders = readers;
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

        // Asynchronous check prompt
        Mono<ServerWebExchange> preChecked = ServerWebExchangeUtils.rewriteRequestBody(
                exchange,
                messageReaders,
                promptBody -> ContentSecurityChecker
                        .checkText(new ContentSecurityChecker.SafetyCheckRequest(
                                handle.getAccessKey(),
                                handle.getAccessToken(),
                                handle.getAppId(),
                                promptBody), handle)
                        .flatMap(resp -> {
                            String cat = resp.getData().getPromptCategory();
                            if ("违规".equals(cat) || "疑似".equals(cat)) {
                                // Throw the exception directly and return it to onErrorResume
                                // todo: Normalized exception returns, Refer to the interface documentation
                                String err = String.format(
                                        "{\"code\":1400,\"msg\":\"内容不符合规范\",\"detail\":\"检测结果：%s\"}",
                                        cat);
                                return Mono.error(new ResponsiveException(1400, err, exchange));
                            }
                            return Mono.just(promptBody);
                        })
        ).thenReturn(exchange);

        // Execute chain
        Mono<Void> callDownstream = preChecked
                .flatMap(chain::execute);

        // Get the ClientResponse asynchronously, read the response body, detect it, and write it back
        Mono<Void> postCheck = callDownstream.then(
                Mono.defer(() -> {
                    // Take out the downstream ClientResponse from the exchange
                    ClientResponse clientResponse = exchange.getAttribute(Constants.CLIENT_RESPONSE_ATTR);
                    if (null == clientResponse) {
                        // If you don't get it, the downstream is not written with writeWith, so you won't intercept it
                        return Mono.empty();
                    }
                    // The read response body is String
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(originalBody -> {
                                // Asynchronous invocation of security detections
                                return ContentSecurityChecker
                                        .checkText(ContentSecurityChecker.SafetyCheckRequest.forContent(
                                                handle.getAccessKey(),
                                                handle.getAccessToken(),
                                                handle.getAppId(),
                                                originalBody), handle)
                                        .flatMap(resp -> {
                                            String cat = resp.getData().getContentCategory();
                                            String toWrite;
                                            // todo: like up
                                            if ("违规".equals(cat) || "疑似".equals(cat)) {
                                                toWrite = String.format(
                                                        "{\"code\":1400,\"msg\":\"内容不符合规范\",\"detail\":\"检测结果：%s\"}",
                                                        cat);
                                            } else {
                                                // Compliance is returned as is
                                                toWrite = originalBody;
                                            }
                                            // Use ResponseUtils to write the content back to the client
                                            return ResponseUtils.writeWith(
                                                    clientResponse,
                                                    exchange,
                                                    Mono.just(toWrite),
                                                    String.class
                                            );
                                        });
                            });
                })
        );

        // The end-to-end merges and handles the exceptions thrown by the pre-detection
        return callDownstream
                .then(postCheck)
                .onErrorResume(e -> {
                    if (e instanceof ResponsiveException) {
                        return WebFluxResultUtils.failedResult((ResponsiveException) e);
                    }
                    return Mono.error(e);
                });
    }

    @Override
    public String named() {
        return PluginEnum.CONTENT_SECURITY.getName();
    }

    @Override
    public int getOrder() {
        return PluginEnum.AI_PROXY.getCode() - 1;
    }
}
