package org.apache.shenyu.plugin.sec.content.checker;

import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内容安全检测器工厂
 * 根据配置自动选择对应的检测器实现
 * 
 * @author yHong
 * @version 1.0
 * @since 2025/8/21
 */
public class ContentSecurityCheckerFactory {
    
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityCheckerFactory.class);
    
    private final List<ContentSecurityChecker> checkers;
    private final Map<String, ContentSecurityChecker> checkerMap = new ConcurrentHashMap<>();
    
    public ContentSecurityCheckerFactory(List<ContentSecurityChecker> checkers) {
        this.checkers = checkers;
        // 初始化检测器映射
        for (ContentSecurityChecker checker : checkers) {
            String vendor = checker.getVendor();
            checkerMap.put(vendor.toLowerCase(), checker);
            LOG.info("Registered content security checker: {} -> {}", vendor, checker.getClass().getSimpleName());
        }
        
        LOG.info("Content security checker factory initialized with {} checkers: {}", 
                checkers.size(), 
                checkers.stream().map(ContentSecurityChecker::getVendor).collect(Collectors.joining(", ")));
    }
    
    /**
     * 根据配置获取对应的检测器
     * 
     * @param handle 配置参数
     * @return 对应的检测器
     * @throws IllegalArgumentException 如果找不到对应的检测器
     */
    public ContentSecurityChecker getChecker(ContentSecurityHandle handle) {
        if (handle == null) {
            throw new IllegalArgumentException("ContentSecurityHandle cannot be null");
        }
        
        String vendor = handle.getVendor();
        if (vendor == null || vendor.trim().isEmpty()) {
            // 默认使用zkrj检测器
            vendor = "zkrj";
            LOG.warn("No vendor specified, using default: {}", vendor);
        }
        
        ContentSecurityChecker checker = checkerMap.get(vendor.toLowerCase());
        if (checker == null) {
            String availableVendors = String.join(", ", checkerMap.keySet());
            throw new IllegalArgumentException(
                String.format("No content security checker found for vendor: %s. Available vendors: %s", 
                    vendor, availableVendors));
        }
        
        LOG.debug("Selected content security checker: {} for vendor: {}", 
                checker.getClass().getSimpleName(), vendor);
        return checker;
    }
    
    /**
     * 检查是否支持指定的厂商类型
     * 
     * @param vendor 厂商标识
     * @return 是否支持
     */
    public boolean supportsVendor(String vendor) {
        if (vendor == null || vendor.trim().isEmpty()) {
            return false;
        }
        return checkerMap.containsKey(vendor.toLowerCase());
    }
    
    /**
     * 获取所有支持的厂商类型
     * 
     * @return 支持的厂商类型列表
     */
    public List<String> getSupportedVendors() {
        return checkerMap.keySet().stream().collect(Collectors.toList());
    }
    
    /**
     * 获取检测器实例
     * 
     * @param vendor 厂商标识
     * @return 检测器实例，如果不存在则返回null
     */
    public ContentSecurityChecker getCheckerByVendor(String vendor) {
        if (vendor == null || vendor.trim().isEmpty()) {
            return null;
        }
        return checkerMap.get(vendor.toLowerCase());
    }
}
