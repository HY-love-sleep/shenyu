package org.apache.shenyu.plugin.sec.mark.decorator;

import org.apache.shenyu.common.dto.convert.rule.ContentMarkHandle;
import org.apache.shenyu.plugin.base.decorator.GenericResponseDecorator;
import org.apache.shenyu.plugin.sec.mark.WaterMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/23 17:00
 */
public class ContentMarkResponseDecorator extends GenericResponseDecorator {
    private static final Logger LOG = LoggerFactory.getLogger(ContentMarkResponseDecorator.class);

    public ContentMarkResponseDecorator(ServerWebExchange exchange, ContentMarkHandle handle) {
        super(
                exchange.getResponse(),
                exchange,
                10,
                buildMarkAndOutput(handle, exchange.getResponse())
        );
    }

    private static BiFunction<List<String>, List<byte[]>, Flux<DataBuffer>> buildMarkAndOutput(ContentMarkHandle handle, ServerHttpResponse response) {
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
                return Flux.fromIterable(rawSseBytes)
                        .map(bytes -> response.bufferFactory().wrap(bytes));
            }

            WaterMarker.TextMarkRequest req = new WaterMarker.TextMarkRequest();
            req.setContent(batchContent);
            req.setContentNumber("obligate ContentNumber");
            req.setUserInput("obligate UserInput");
            req.setDataType("文本");
            req.setModelName(handle.getModelName());
            req.setApplicationName(handle.getApplicationName());
            req.setServiceProvider(handle.getServiceProvider());
            req.setServiceUser(handle.getServiceUser());
            req.setRequestIdentification(UUID.randomUUID().toString());

            return WaterMarker
                    .addMarkForText(req, handle)
                    .flatMapMany(resp -> {
                        String marked = (resp != null && resp.getData() != null && resp.getData().getContent() != null)
                                ? resp.getData().getContent()
                                : batchContent;

                        String result = "data: " + marked + "\n\n";
                        return Flux.just(response.bufferFactory().wrap(result.getBytes(StandardCharsets.UTF_8)));
                    });
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
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                com.fasterxml.jackson.databind.JsonNode delta = choices.get(0).get("delta");
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
