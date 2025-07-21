package org.apache.shenyu.plugin.sec.mark.service;

import reactor.core.publisher.Mono;

public interface ContentMarkingService {

    /**
     * Text add mark
     * @param request text request json
     * @return response
     */
    Mono<TextMarkResponse> markText(TextMarkRequest request);

    /**
     * request params
     */
    class TextMarkRequest {
        // TODO: fields and getter/setter

    }

    /**
     * response
     */
    class TextMarkResponse {
        // TODO: fields and getter/setter

    }
}
