/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shenyu.plugin.ai.maas.proxy;

import org.apache.shenyu.common.constant.Constants;
import org.apache.shenyu.common.utils.GsonUtils;
import org.apache.shenyu.plugin.ai.common.config.AiCommonConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Convert incoming request body to Yuanjing MAAS Agent API body.
 */
public final class MaasRequestBodyConverter {

    private MaasRequestBodyConverter() {
    }

    public static String convert(final String originalBody, final AiCommonConfig config) {
        // Assume original body may be OpenAI-like or custom; convert to MAAS structure
        Map<String, Object> src = GsonUtils.getInstance().convertToMap(originalBody);

        Map<String, Object> target = new HashMap<>();
        // Required
        // MAAS requires app_id + query + history + stream etc.
        Object appId = src.getOrDefault("app_id", config.getModel());
        target.put("app_id", appId);

        // query: prefer 'query', else build from messages
        Object query = src.get("query");
        if (Objects.isNull(query)) {
            // Build from OpenAI messages array
            Object messagesObj = src.get("messages");
            if (messagesObj instanceof List) {
                List<?> messages = (List<?>) messagesObj;
                if (!messages.isEmpty()) {
                    Object last = messages.get(messages.size() - 1);
                    Map<String, Object> lastMsg = GsonUtils.getInstance().convertToMap(GsonUtils.getInstance().toJson(last));
                    query = lastMsg.get("content");
                }
            }
        }
        target.put("query", query != null ? query : "");

        // history: prefer given, else convert messages
        Object history = src.get("history");
        if (Objects.isNull(history)) {
            Object messagesObj = src.get("messages");
            if (messagesObj instanceof List) {
                target.put("history", messagesObj);
            } else {
                target.put("history", List.of());
            }
        } else {
            target.put("history", history);
        }

        // stream: prefer provided else config
        Object stream = src.getOrDefault(Constants.STREAM, config.getStream());
        target.put("stream", stream);

        // conversation_id passthrough if exists
        if (src.containsKey("conversation_id")) {
            target.put("conversation_id", src.get("conversation_id"));
        }

        // kn_params, plugin_list, api_schema, api_auth, functioncallslist, etc. passthrough
        passthrough(src, target, "kn_params");
        passthrough(src, target, "plugin_list");
        passthrough(src, target, "api_schema");
        passthrough(src, target, "api_auth");
        passthrough(src, target, "funtioncallslist");

        // temperature/max_tokens/top_p etc. passthrough when present
        passthrough(src, target, "temperature");
        passthrough(src, target, "max_tokens");
        passthrough(src, target, "top_p");
        passthrough(src, target, "presence_penalty");
        passthrough(src, target, "frequency_penalty");

        return GsonUtils.getInstance().toJson(target);
    }

    private static void passthrough(final Map<String, Object> src, final Map<String, Object> target, final String key) {
        if (src.containsKey(key)) {
            target.put(key, src.get(key));
        }
    }
}


