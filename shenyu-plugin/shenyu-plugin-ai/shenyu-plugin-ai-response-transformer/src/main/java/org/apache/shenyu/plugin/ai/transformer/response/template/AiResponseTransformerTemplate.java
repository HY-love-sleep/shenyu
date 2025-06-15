package org.apache.shenyu.plugin.ai.transformer.response.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.shenyu.plugin.ai.common.template.AbstractAiTransformerTemplate;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;
import org.springframework.http.HttpHeaders;

/**
 * AI Transformer template for HTTP responses.
 * The constructed JSON includes:
 *   "status_line": "...",
 *   "headers": { ... },
 *   "body": JSON or raw string
 */
public class AiResponseTransformerTemplate extends AbstractAiTransformerTemplate {

    public static final String SYS_CONTENT =
            "You are an expert in HTTP/1.1 protocol. Your response should contain only standard HTTP/1.1 message content.\n"
                    + "Please return a complete HTTP/1.1 response message, including:\n"
                    + "- Status line\n"
                    + "- Multiple headers (one per line)\n"
                    + "- A blank line\n"
                    + "- A JSON-formatted body (must be valid JSON)\n"
                    + "Do not include any extra comments or text.";

    private final String userContent;
    private final String originalFullResponse;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AiResponseTransformerTemplate(final String userContent,
                                         final String originalFullResponse) {
        super(SYS_CONTENT, userContent, "response");
        this.userContent = userContent;
        this.originalFullResponse = originalFullResponse;
    }

    @Override
    protected Mono<JsonNode> buildPayloadJson() {
        // 和之前一样，只负责把 originalFullResponse 解析成 JSON 负载
        String[] lines = Objects.requireNonNull(originalFullResponse).split("\\R");
        ObjectNode respNode = MAPPER.createObjectNode();
        // status_line
        respNode.put("status_line", lines.length > 0 ? lines[0] : "");
        // headers
        ObjectNode headersNode = MAPPER.createObjectNode();
        int i = 1;
        for (; i < lines.length; i++) {
            String ln = lines[i];
            if (ln.trim().isEmpty()) { i++; break; }
            int idx = ln.indexOf(':');
            if (idx > 0) {
                headersNode.put(ln.substring(0, idx).trim(), ln.substring(idx+1).trim());
            }
        }
        respNode.set("headers", headersNode);
        // body
        StringBuilder sb = new StringBuilder();
        for (; i < lines.length; i++) {
            sb.append(lines[i]).append('\n');
        }
        String bodyStr = sb.toString().trim();
        try {
            JsonNode bodyJson = MAPPER.readTree(bodyStr);
            respNode.set("body", bodyJson);
        } catch (Exception ex) {
            respNode.put("body", bodyStr);
        }
        return Mono.just(respNode);
    }

    /**
     * 用于解析 AI 返回的完整 HTTP/1.1 响应文本
     */
    public static class ResponseParts {
        private final String statusLine;
        private final HttpHeaders headers;
        private final String body;
        public ResponseParts(final String statusLine, final HttpHeaders headers, final String body) {
            this.statusLine = statusLine;
            this.headers = headers;
            this.body = body;
        }
        public String getStatusLine() { return statusLine; }
        public HttpHeaders getHeaders() { return headers; }
        public String getBody() { return body; }
    }

    /**
     * 把 AI 返回的完整 HTTP 响应文本解析成 ResponseParts
     */
    public static ResponseParts parseResponse(final String raw) {
        BufferedReader reader = new BufferedReader(new StringReader(raw));
        String line;
        String statusLine = null;
        HttpHeaders headers = new HttpHeaders();
        try {
            // 1. 读状态行
            if ((statusLine = reader.readLine()) == null) {
                return new ResponseParts("", headers, "");
            }
            // 2. 读 headers
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    break;
                }
                int idx = line.indexOf(':');
                if (idx > 0) {
                    headers.add(line.substring(0, idx).trim(), line.substring(idx+1).trim());
                }
            }
            // 3. 剩余作为 body
            StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            String body = sb.toString().trim();
            return new ResponseParts(statusLine, headers, body);
        } catch (IOException e) {
            // 万一出错，返回尽量多的信息
            return new ResponseParts(statusLine == null ? "" : statusLine, headers, "");
        }
    }
}
