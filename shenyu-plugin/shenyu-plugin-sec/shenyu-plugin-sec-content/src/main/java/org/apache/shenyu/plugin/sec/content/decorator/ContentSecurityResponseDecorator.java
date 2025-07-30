package org.apache.shenyu.plugin.sec.content.decorator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.base.decorator.GenericResponseDecorator;
import org.apache.shenyu.plugin.sec.content.ContentSecurityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.reactivestreams.Publisher;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * @author yHong
 * @since 2025/7/9 14:25
 * @version 1.0
 * // todo: Extracted into a generic decorator
 */
public class ContentSecurityResponseDecorator extends GenericResponseDecorator {
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityResponseDecorator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ContentSecurityHandle handle;

    public ContentSecurityResponseDecorator(ServerWebExchange exchange, ContentSecurityHandle handle) {
        super(
                exchange.getResponse(),
                exchange,
                10,
                buildProcessAndOutput(handle, exchange)
        );
        this.handle = handle;
    }

    private static BiFunction<List<String>, List<byte[]>, Flux<DataBuffer>> buildProcessAndOutput(
            ContentSecurityHandle handle, ServerWebExchange exchange) {
        return (sseLines, rawSseBytes) -> {
            StringBuilder contentBuilder = new StringBuilder();
            for (String line : sseLines) {
                String chunkStr = line.trim();
                if (chunkStr.startsWith("data:")) {
                    String json = chunkStr.substring(5).trim();
                    if ("[DONE]".equals(json)) {
                        continue;
                    } else {
                        String content = extractContentFromOpenAIChunk(chunkStr);
                        if (content != null) {
                            contentBuilder.append(content);
                        }
                    }
                }
            }
            String batchContent = contentBuilder.toString();

            if (batchContent.isEmpty()) {
                LOG.info("No actual content to check, output all {} raw sse bytes", rawSseBytes.size());
                return Flux.fromIterable(rawSseBytes)
                        .map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes));
            }

            return ContentSecurityChecker
                    .checkText(
                            ContentSecurityChecker.SafetyCheckRequest.forContent(
                                    handle.getAccessKey(), handle.getAccessToken(), handle.getAppId(), batchContent
                            ),
                            handle)
                    .flatMapMany(resp -> {
                        String cat = resp.getData() != null ? resp.getData().getContentCategory() : "";
                        if ("违规".equals(cat) || "疑似".equals(cat)) {
                            String errResp = "{\"code\":1401,\"msg\":\"返回内容违规\",\"detail\":\"检测结果：" + cat + "\"}";
                            byte[] errBytes = errResp.getBytes(StandardCharsets.UTF_8);
                            return Flux.just(exchange.getResponse().bufferFactory().wrap(errBytes));
                        } else {
                            LOG.info("Security check passed, output {} sse chunks", rawSseBytes.size());
                            return Flux.fromIterable(rawSseBytes)
                                    .map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes));
                        }
                    })
                    .doOnError(e -> LOG.error("Error in detectAndOutput", e));
        };
    }

    private static String extractContentFromOpenAIChunk(String chunkJson) {
        try {
            String json = chunkJson.trim();
            if (json.startsWith("data:")) {
                json = json.substring(5).trim();
            }
            if ("[DONE]".equals(json)) {
                return null;
            }
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null && delta.has("content")) {
                    return delta.get("content").asText();
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to extract content from chunk: " + chunkJson, e);
        }
        return null;
    }
}
