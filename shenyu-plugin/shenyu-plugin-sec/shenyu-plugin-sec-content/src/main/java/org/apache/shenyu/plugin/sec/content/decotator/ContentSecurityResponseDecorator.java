package org.apache.shenyu.plugin.sec.content.decotator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
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

/**
 * @author yHong
 * @since 2025/7/9 14:25
 * @version 1.0
 */
public class ContentSecurityResponseDecorator extends ServerHttpResponseDecorator {
    // each 10 chunks will be checked, but dont hinder client sse output
    private final static Integer CHUNK_BATCH_SIZE  = 10;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityResponseDecorator.class);
    private final ServerWebExchange exchange;
    private final ContentSecurityHandle handle;

    public ContentSecurityResponseDecorator(ServerWebExchange exchange, ContentSecurityHandle handle) {
        super(exchange.getResponse());
        this.exchange = exchange;
        this.handle = handle;
    }

    /**
     * check chunked, streaming
     * @param body
     * @return
     */
    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        String contentType = getDelegate().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType == null ||
                (!contentType.toLowerCase().contains("json") && !contentType.toLowerCase().contains("event-stream"))) {
            return getDelegate().writeWith(body);
        }

        // Row splicing buffer ( across buffers)
        final StringBuilder sseLineBuffer = new StringBuilder();

        final List<String> sseLinesBuffer = new ArrayList<>();
        final List<byte[]> rawSseBytesBuffer = new ArrayList<>();

        Flux<DataBuffer> processed = Flux.from(body)
                .concatMap(dataBuffer -> {
                    // DataBuffer -> String
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    String str = new String(bytes, StandardCharsets.UTF_8);
                    DataBufferUtils.release(dataBuffer);

                    sseLineBuffer.append(str);

                    List<String> fullLines = new ArrayList<>();
                    int idx;
                    while ((idx = indexOfLineBreak(sseLineBuffer)) != -1) {
                        String line = sseLineBuffer.substring(0, idx);
                        if (idx > 0 && sseLineBuffer.charAt(idx - 1) == '\r') {
                            line = sseLineBuffer.substring(0, idx - 1);
                        }
                        sseLineBuffer.delete(0, idx + 1);
                        if (!line.trim().isEmpty()) {
                            fullLines.add(line);
                        }
                    }

                    // cache all full SSE lines and raw bytes
                    for (String line : fullLines) {
                        sseLinesBuffer.add(line);
                        rawSseBytesBuffer.add((line + "\n\n").getBytes(StandardCharsets.UTF_8));
                    }

                    // each batch of N lines is tested
                    List<String> toDetectLines = new ArrayList<>();
                    List<byte[]> toOutputBytes = new ArrayList<>();
                    Flux<DataBuffer> out = Flux.empty();

                    while (sseLinesBuffer.size() >= CHUNK_BATCH_SIZE) {
                        for (int i = 0; i < CHUNK_BATCH_SIZE; i++) {
                            toDetectLines.add(sseLinesBuffer.remove(0));
                            toOutputBytes.add(rawSseBytesBuffer.remove(0));
                        }
                        out = out.concatWith(detectAndOutput(toDetectLines, toOutputBytes));
                        toDetectLines = new ArrayList<>();
                        toOutputBytes = new ArrayList<>();
                    }

                    return out;
                })
                // end Processing: There are still scenes left that have not reached the N line
                .concatWith(Flux.defer(() -> {
                    if (!sseLinesBuffer.isEmpty()) {
                        List<String> toDetectLines = new ArrayList<>(sseLinesBuffer);
                        List<byte[]> toOutputBytes = new ArrayList<>(rawSseBytesBuffer);
                        sseLinesBuffer.clear();
                        rawSseBytesBuffer.clear();
                        return detectAndOutput(toDetectLines, toOutputBytes);
                    }
                    return Flux.empty();
                }));

        return getDelegate().writeWith(processed);
    }

    // Row splitting: Returns the position of the first\n or \r\n
    private int indexOfLineBreak(StringBuilder sb) {
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }

    private Flux<DataBuffer> detectAndOutput(List<String> sseLines, List<byte[]> rawSseBytes) {
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
                    .map(bytes -> getDelegate().bufferFactory().wrap(bytes));
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
                        return Flux.just(getDelegate().bufferFactory().wrap(errBytes));
                    } else {
                        LOG.info("Security check passed, output {} sse chunks", rawSseBytes.size());
                        return Flux.fromIterable(rawSseBytes)
                                .map(bytes -> getDelegate().bufferFactory().wrap(bytes));
                    }
                })
                .doOnError(e -> LOG.error("Error in detectAndOutput", e));
    }


    // get content
    private String extractContentFromOpenAIChunk(String chunkJson) {
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
            if (choices != null && choices.isArray() && choices.size() > 0) {
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
