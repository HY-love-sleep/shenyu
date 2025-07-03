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
        // 1. 拿到规则级配置
        ContentSecurityHandle handle = ContentSecurityPluginDataHandler.CACHED_HANDLE
                .get().obtainHandle(CacheKeyUtils.INST.getKey(rule));
        if (handle == null) {
            // 没配就放行
            return chain.execute(exchange);
        }

        // 2. 前置：异步检查 prompt
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
                                // 直接抛异常，到 onErrorResume 里去返回
                                String err = String.format(
                                        "{\"code\":1400,\"msg\":\"内容不符合规范\",\"detail\":\"检测结果：%s\"}",
                                        cat);
                                return Mono.error(new ResponsiveException(1400, err, exchange));
                            }
                            return Mono.just(promptBody);
                        })
        ).thenReturn(exchange);

        // 先执行业务链，包括 AiProxy 插件，它会在 exchange 上设置
        // Constants.CLIENT_RESPONSE_ATTR 属性，保留下游实际的 ClientResponse
        Mono<Void> callDownstream = chain.execute(exchange);

        // 然后在它完成之后，异步拿到这份 ClientResponse，读取响应体、检测并写回
        Mono<Void> postCheck = callDownstream.then(
                Mono.defer(() -> {
                    // 1) 从 exchange 上取出下游的 ClientResponse
                    ClientResponse clientResponse = exchange.getAttribute(Constants.CLIENT_RESPONSE_ATTR);
                    if (null == clientResponse) {
                        // 如果没有拿到，下游不是用 writeWith 写的，就不做拦截
                        return Mono.empty();
                    }
                    // 2) 读取响应体为 String
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(originalBody -> {
                                // 3) 异步调用安全检测
                                return ContentSecurityChecker
                                        .checkText(ContentSecurityChecker.SafetyCheckRequest.forContent(
                                                handle.getAccessKey(),
                                                handle.getAccessToken(),
                                                handle.getAppId(),
                                                originalBody), handle)
                                        .flatMap(resp -> {
                                            String cat = resp.getData().getContentCategory();
                                            // 4) 如果“违规”或“疑似”，返回拦截 JSON
                                            String toWrite;
                                            if ("违规".equals(cat) || "疑似".equals(cat)) {
                                                toWrite = String.format(
                                                        "{\"code\":1400,\"msg\":\"内容不符合规范\",\"detail\":\"检测结果：%s\"}",
                                                        cat);
                                            } else {
                                                // 合规就原样返回
                                                toWrite = originalBody;
                                            }
                                            // 5) 用 ResponseUtils 把内容写回客户端
                                            //    注意：这里把 publisher 换成 Mono.just(toWrite)
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

        // 6) 全链路合并，并处理前置检测抛出的异常
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
        return "ContentSecurityPlugin";
    }

    @Override
    public int getOrder() {
        return PluginEnum.AI_PROXY.getCode() - 1;
    }
}
