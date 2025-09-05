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

package org.apache.shenyu.common.dto.convert.rule;

import java.util.Map;

/**
 * Maas Proxy rule handle. Define Yuanjing Agent specific fields.
 */
public class MaasProxyHandle {

    private String baseUrl;

    private String apiKey;

    private String appId;

    private Double temperature;

    private Integer maxTokens;

    private Boolean stream;

    private Map<String, Object> knParams;

    private Object pluginList;

    private Map<String, Object> apiSchema;

    private Map<String, Object> apiAuth;

    /**
     * Notice: keep original doc naming for compatibility.
     */
    private Object funtioncallslist;

    private Object functioncallslist;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(final String appId) {
        this.appId = appId;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(final Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(final Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(final Boolean stream) {
        this.stream = stream;
    }

    public Map<String, Object> getKnParams() {
        return knParams;
    }

    public void setKnParams(final Map<String, Object> knParams) {
        this.knParams = knParams;
    }

    public Object getPluginList() {
        return pluginList;
    }

    public void setPluginList(final Object pluginList) {
        this.pluginList = pluginList;
    }

    public Map<String, Object> getApiSchema() {
        return apiSchema;
    }

    public void setApiSchema(final Map<String, Object> apiSchema) {
        this.apiSchema = apiSchema;
    }

    public Map<String, Object> getApiAuth() {
        return apiAuth;
    }

    public void setApiAuth(final Map<String, Object> apiAuth) {
        this.apiAuth = apiAuth;
    }

    public Object getFuntioncallslist() {
        return funtioncallslist;
    }

    public void setFuntioncallslist(final Object funtioncallslist) {
        this.funtioncallslist = funtioncallslist;
    }

    public Object getFunctioncallslist() {
        return functioncallslist;
    }

    public void setFunctioncallslist(final Object functioncallslist) {
        this.functioncallslist = functioncallslist;
    }
}


