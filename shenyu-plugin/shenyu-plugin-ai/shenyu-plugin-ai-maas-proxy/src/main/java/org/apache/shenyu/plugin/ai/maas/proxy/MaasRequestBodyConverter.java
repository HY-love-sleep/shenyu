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
import org.apache.shenyu.common.dto.convert.rule.MaasProxyHandle;

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

    public static String convert(final String originalBody, final AiCommonConfig config, final MaasProxyHandle handle) {
        // Assume original body may be OpenAI-like or custom; convert to MAAS structure
        Map<String, Object> src = GsonUtils.getInstance().convertToMap(originalBody);

        Map<String, Object> target = new HashMap<>();
        // Required
        // MAAS requires app_id + query + history + stream etc.
        Object appId = src.getOrDefault("app_id", handle != null && handle.getAppId() != null ? handle.getAppId() : config.getModel());
        target.put("app_id", appId);

        // input: prefer 'input', else fallback to 'query', else build from messages
        Object input = src.get("input");
        if (Objects.isNull(input)) {
            input = src.get("query");
        }
        if (Objects.isNull(input)) {
            // Build from OpenAI messages array
            Object messagesObj = src.get("messages");
            if (messagesObj instanceof List) {
                List<?> messages = (List<?>) messagesObj;
                if (!messages.isEmpty()) {
                    Object last = messages.get(messages.size() - 1);
                    Map<String, Object> lastMsg = GsonUtils.getInstance().convertToMap(GsonUtils.getInstance().toJson(last));
                    input = lastMsg.get("content");
                }
            }
        }
        if (Objects.nonNull(input)) {
            target.put("input", input);
        }
        // If original request explicitly used 'query', keep it for compatibility
        if (src.containsKey("query")) {
            target.put("query", src.get("query"));
        }

        // history: only pass through if provided to keep minimal body for common Q&A
        if (src.containsKey("history")) {
            target.put("history", src.get("history"));
        }

        // stream: prefer provided else config/handle
        Object stream = src.getOrDefault(Constants.STREAM, config.getStream());
        target.put("stream", stream);

        // conversation/session passthrough if exists
        if (src.containsKey("conversation_id")) {
            target.put("conversation_id", src.get("conversation_id"));
        }
        if (src.containsKey("session_id")) {
            target.put("session_id", src.get("session_id"));
        } else if (src.containsKey("conversation_id")) {
            // Fallback: copy conversation_id into session_id if session_id not provided
            target.put("session_id", src.get("conversation_id"));
        }

        // kn_params, plugin_list, api_schema, api_auth, functioncallslist, etc. passthrough
        passthrough(src, target, "kn_params");
        passthrough(src, target, "plugin_list");
        passthrough(src, target, "api_schema");
        passthrough(src, target, "api_auth");
        passthrough(src, target, "funtioncallslist");
        passthrough(src, target, "use_search");

        // If not provided in request body, try to inject from selector handle defaults
        if (handle != null) {
            if (!target.containsKey("kn_params") && handle.getKnParams() != null) {
                target.put("kn_params", handle.getKnParams());
            }
            if (!target.containsKey("plugin_list") && handle.getPluginList() != null) {
                target.put("plugin_list", handle.getPluginList());
            }
            if (!target.containsKey("api_schema") && handle.getApiSchema() != null) {
                target.put("api_schema", handle.getApiSchema());
            }
            if (!target.containsKey("api_auth") && handle.getApiAuth() != null) {
                target.put("api_auth", handle.getApiAuth());
            }
            // support both spellings if provided via handle
            if (!target.containsKey("funtioncallslist") && handle.getFuntioncallslist() != null) {
                target.put("funtioncallslist", handle.getFuntioncallslist());
            }
            if (!target.containsKey("functioncallslist") && handle.getFunctioncallslist() != null) {
                target.put("functioncallslist", handle.getFunctioncallslist());
            }
        }

        // temperature/max_tokens/top_p etc. passthrough when present
        passthrough(src, target, "temperature");
        passthrough(src, target, "max_tokens");
        passthrough(src, target, "top_p");
        passthrough(src, target, "presence_penalty");
        passthrough(src, target, "frequency_penalty");

        // supplement defaults from config when absent
        if (!target.containsKey("temperature") && config.getTemperature() != null) {
            target.put("temperature", config.getTemperature());
        }
        if (!target.containsKey("max_tokens") && config.getMaxTokens() != null) {
            target.put("max_tokens", config.getMaxTokens());
        }

        return GsonUtils.getInstance().toJson(target);
    }

    private static void passthrough(final Map<String, Object> src, final Map<String, Object> target, final String key) {
        if (src.containsKey(key)) {
            target.put(key, src.get(key));
        }
    }
}


