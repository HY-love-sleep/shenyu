package org.apache.shenyu.plugin.sec.content.checker;

import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.sec.content.ContentSecurityResult;
import reactor.core.publisher.Mono;

/**
 * 内容安全检测器接口
 * 定义内容安全检测的通用契约，支持多种厂商实现
 * 
 * @author yHong
 * @version 1.0
 * @since 2025/8/21
 */
public interface ContentSecurityChecker {
    
    /**
     * 检测文本内容安全性
     * 
     * @param request 检测请求（厂商特定的请求类型）
     * @param handle 配置参数
     * @return 统一的检测结果
     */
    Mono<ContentSecurityResult> checkText(Object request, ContentSecurityHandle handle);
    
    /**
     * 获取厂商标识
     * 
     * @return 厂商标识字符串
     */
    String getVendor();
    
    /**
     * 检查是否支持指定的厂商类型
     * 
     * @param vendor 厂商标识
     * @return 是否支持
     */
    boolean supports(String vendor);
}
