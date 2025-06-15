package org.apache.shenyu.plugin.ai.common.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Mono;

/**
 * Abstract AI Transformer template, subclasses only need to implement buildPayloadJson()
 * to fill in the specific JSON payload, and then the parent class assembleMessage() will uniformly assemble:
 * {
 *   "system_prompt": "...",
 *   "user_prompt": "...",
 *   "{payloadFieldName}": { ... }
 * }
 */
public abstract class AbstractAiTransformerTemplate {

    protected final String systemPrompt;
    protected final String userPrompt;
    private final String payloadFieldName;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected AbstractAiTransformerTemplate(final String systemPrompt,
                                            final String userPrompt,
                                            final String payloadFieldName) {
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.payloadFieldName = payloadFieldName;
    }

    /**
     * Child class implementation: Construct a JsonNode payload based on request or response
     */
    protected abstract Mono<JsonNode> buildPayloadJson();

    /**
     * Template Method: Assemble the final string to be sent to AI
     */
    public Mono<String> assembleMessage() {
        return buildPayloadJson()
            .map(payload -> {
                ObjectNode root = objectMapper.createObjectNode();
                root.put("system_prompt", systemPrompt);
                root.put("user_prompt", userPrompt);
                root.set(payloadFieldName, payload);
                try {
                    return objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(root);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("AI request serialization failed", e);
                }
            });
    }
}
