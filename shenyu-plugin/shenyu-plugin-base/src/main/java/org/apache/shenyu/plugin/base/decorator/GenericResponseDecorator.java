package org.apache.shenyu.plugin.base.decorator;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/23 16:28
 */
public class GenericResponseDecorator extends ServerHttpResponseDecorator {
    private static final Logger LOG = LoggerFactory.getLogger(GenericResponseDecorator.class);

    private final int chunkBatchSize;
    private final BiFunction<List<String>, List<byte[]>, Flux<DataBuffer>> processAndOutput;

    public GenericResponseDecorator(ServerHttpResponse delegate,
                                    int chunkBatchSize,
                                    BiFunction<List<String>, List<byte[]>, Flux<DataBuffer>> processAndOutput) {
        super(delegate);
        this.chunkBatchSize = chunkBatchSize;
        this.processAndOutput = processAndOutput;
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        String contentType = getDelegate().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType == null ||
                (!contentType.toLowerCase().contains("json") && !contentType.toLowerCase().contains("event-stream"))) {
            return getDelegate().writeWith(body);
        }

        final StringBuilder sseLineBuffer = new StringBuilder();
        final List<String> sseLinesBuffer = new ArrayList<>();
        final List<byte[]> rawSseBytesBuffer = new ArrayList<>();

        Flux<DataBuffer> processed = Flux.from(body)
                .concatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    String str = new String(bytes, StandardCharsets.UTF_8);

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

                    for (String line : fullLines) {
                        sseLinesBuffer.add(line);
                        rawSseBytesBuffer.add((line + "\n\n").getBytes(StandardCharsets.UTF_8));
                    }

                    List<String> toProcessLines = new ArrayList<>();
                    List<byte[]> toOutputBytes = new ArrayList<>();
                    Flux<DataBuffer> out = Flux.empty();

                    while (sseLinesBuffer.size() >= chunkBatchSize) {
                        for (int i = 0; i < chunkBatchSize; i++) {
                            toProcessLines.add(sseLinesBuffer.remove(0));
                            toOutputBytes.add(rawSseBytesBuffer.remove(0));
                        }
                        out = out.concatWith(processAndOutput.apply(toProcessLines, toOutputBytes));
                        toProcessLines = new ArrayList<>();
                        toOutputBytes = new ArrayList<>();
                    }
                    return out;
                })
                .concatWith(Flux.defer(() -> {
                    if (!sseLinesBuffer.isEmpty()) {
                        List<String> toProcessLines = new ArrayList<>(sseLinesBuffer);
                        List<byte[]> toOutputBytes = new ArrayList<>(rawSseBytesBuffer);
                        sseLinesBuffer.clear();
                        rawSseBytesBuffer.clear();
                        return processAndOutput.apply(toProcessLines, toOutputBytes);
                    }
                    return Flux.empty();
                }));

        return getDelegate().writeWith(processed);
    }

    private int indexOfLineBreak(StringBuilder sb) {
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }
}
