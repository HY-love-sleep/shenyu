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

package org.apache.shenyu.plugin.ai.maas.proxy.handler;

import org.apache.shenyu.common.constant.Constants;
import org.apache.shenyu.common.dto.PluginData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.dto.convert.rule.MaasProxyHandle;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.common.utils.GsonUtils;
import org.apache.shenyu.common.utils.Singleton;
import org.apache.shenyu.plugin.ai.common.config.AiCommonConfig;
import org.apache.shenyu.plugin.base.cache.CommonHandleCache;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.base.utils.BeanHolder;
import org.apache.shenyu.plugin.base.utils.CacheKeyUtils;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * MaasProxyPluginHandler.
 */
public class MaasProxyPluginHandler implements PluginDataHandler {

    public static final Supplier<CommonHandleCache<String, MaasProxyHandle>> SELECTOR_CACHED_HANDLE = new BeanHolder<>(CommonHandleCache::new);

    @Override
    public void handlerPlugin(final PluginData pluginData) {
        if (Objects.nonNull(pluginData) && pluginData.getEnabled()) {
            AiCommonConfig aiCommonConfig = GsonUtils.getInstance().fromJson(pluginData.getConfig(), AiCommonConfig.class);
            if (Objects.isNull(aiCommonConfig)) {
                return;
            }
            Singleton.INST.single(AiCommonConfig.class, aiCommonConfig);
        }
    }

    @Override
    public void handlerSelector(final SelectorData selectorData) {
        if (Objects.isNull(selectorData.getHandle())) {
            return;
        }
        MaasProxyHandle handle = GsonUtils.getInstance().fromJson(selectorData.getHandle(), MaasProxyHandle.class);
        SELECTOR_CACHED_HANDLE.get().cachedHandle(CacheKeyUtils.INST.getKey(selectorData.getId(), Constants.DEFAULT_RULE), handle);
    }

    @Override
    public void removeSelector(final SelectorData selectorData) {
        SELECTOR_CACHED_HANDLE.get().removeHandle(CacheKeyUtils.INST.getKey(selectorData.getId(), Constants.DEFAULT_RULE));
    }

    @Override
    public String pluginNamed() {
        return PluginEnum.AI_MAAS_PROXY.getName();
    }
}


