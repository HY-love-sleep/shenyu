package org.apache.shenyu.plugin.sec.content;

import org.apache.shenyu.common.dto.convert.rule.ContentSecurityHandle;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityChecker;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityCheckerFactory;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityCheckerSm;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityCheckerZkrj.SafetyCheckRequest;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityCheckerSm.SmTextCheckRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 内容安全检测服务
 * 提供统一的内容安全检测接口，自动选择对应的检测器
 * 
 * @author yHong
 * @version 1.0
 * @since 2025/8/21
 */
public class ContentSecurityService {
    
    private static final Logger LOG = LoggerFactory.getLogger(ContentSecurityService.class);
    
    // 数美检测器默认值常量
    private static final String DEFAULT_TOKEN_ID_PREFIX = "default_token_";
    private static final String DEFAULT_IP = "118.89.214.89";
    private static final String DEFAULT_DEVICE_ID = "default_device_id";
    private static final String DEFAULT_NICKNAME = "yuanjing-mass";
    private static final String DEFAULT_TOPIC = "default_topic";
    private static final String DEFAULT_AT_ID = "default_user";
    private static final String DEFAULT_ROOM = "default_room";
    private static final String DEFAULT_RECEIVER_TOKEN_ID = "default_receiver";
    
    private final ContentSecurityCheckerFactory checkerFactory;
    
    public ContentSecurityService(ContentSecurityCheckerFactory checkerFactory) {
        this.checkerFactory = checkerFactory;
    }
    
    /**
     * 检测文本内容安全性（ZKRJ厂商）
     * 
     * @param text 需要检测的文本
     * @param handle 配置参数
     * @return 检测结果
     */
    public Mono<ContentSecurityResult> checkTextWithZkrj(String text, ContentSecurityHandle handle) {
        try {
            SafetyCheckRequest request = new SafetyCheckRequest(
                handle.getAccessKey(),
                handle.getAccessToken(),
                handle.getAppId(),
                text
            );
            
            ContentSecurityChecker checker = checkerFactory.getChecker(handle);
            return checker.checkText(request, handle);
        } catch (Exception e) {
            LOG.error("Failed to check text with ZKRJ", e);
            return Mono.just(ContentSecurityResult.error("zkrj", 
                e.getMessage(), "1500", "检测服务异常"));
        }
    }
    
    /**
     * 检测文本内容安全性（数美厂商）
     * 
     * @param text 需要检测的文本
     * @param handle 配置参数
     * @return 检测结果
     */
    public Mono<ContentSecurityResult> checkTextWithShumei(String text, ContentSecurityHandle handle) {
        try {
            // 创建SmTextCheckRequest，使用无参构造函数
            SmTextCheckRequest request = new SmTextCheckRequest();
            request.setAccessKey(handle.getAccessKey());
            request.setAppId(handle.getAppId());
            request.setEventId(handle.getEventId());
            request.setType(handle.getType());
            
            // 创建SmTextCheckData，设置默认值, 不需要前端配置
            ContentSecurityCheckerSm.SmTextCheckData data = new ContentSecurityCheckerSm.SmTextCheckData();
            LOG.info("数美前置送审内容：{}", text);
            data.setText(text);
            // 不设置relateText，让它保持未初始化状态，避免null值问题
            data.setTokenId(DEFAULT_TOKEN_ID_PREFIX + System.currentTimeMillis());
            data.setIp(DEFAULT_IP);
            data.setDeviceId(DEFAULT_DEVICE_ID);
            data.setNickname(DEFAULT_NICKNAME);

            ContentSecurityCheckerSm.SmTextCheckExtra extra = new ContentSecurityCheckerSm.SmTextCheckExtra();
            extra.setTopic(DEFAULT_TOPIC);
            extra.setAtId(DEFAULT_AT_ID);
            extra.setRoom(DEFAULT_ROOM);
            extra.setReceiveTokenId(DEFAULT_RECEIVER_TOKEN_ID);
            
            data.setExtra(extra);
            request.setData(data);
            
            ContentSecurityChecker checker = checkerFactory.getChecker(handle);
            return checker.checkText(request, handle);
        } catch (Exception e) {
            LOG.error("Failed to check text with Shumei", e);
            return Mono.just(ContentSecurityResult.error("shumei", 
                e.getMessage(), "1500", "检测服务异常"));
        }
    }
    
    /**
     * 通用文本检测接口
     * 根据配置自动选择对应的检测器
     * 
     * @param text 需要检测的文本
     * @param handle 配置参数
     * @return 检测结果
     */
    public Mono<ContentSecurityResult> checkText(String text, ContentSecurityHandle handle) {
        if (handle == null) {
            return Mono.just(ContentSecurityResult.error("unknown", 
                "配置参数为空", "1500", "配置参数为空"));
        }
        
        String vendor = handle.getVendor();
        if (vendor == null || vendor.trim().isEmpty()) {
            // 默认使用zkrj检测器
            vendor = "zkrj";
        }
        
        try {
            return switch (vendor.toLowerCase()) {
                case "zkrj" -> checkTextWithZkrj(text, handle);
                case "shumei" -> checkTextWithShumei(text, handle);
                default -> Mono.just(ContentSecurityResult.error(vendor,
                        "不支持的厂商类型: " + vendor, "1500", "不支持的厂商类型"));
            };
        } catch (Exception e) {
            LOG.error("Failed to check text with vendor: {}", vendor, e);
            return Mono.just(ContentSecurityResult.error(vendor, 
                e.getMessage(), "1500", "检测服务异常"));
        }
    }
    
    /**
     * 检查是否支持指定的厂商类型
     * 
     * @param vendor 厂商标识
     * @return 是否支持
     */
    public boolean supportsVendor(String vendor) {
        return checkerFactory.supportsVendor(vendor);
    }
    
    /**
     * 获取所有支持的厂商类型
     * 
     * @return 支持的厂商类型列表
     */
    public java.util.List<String> getSupportedVendors() {
        return checkerFactory.getSupportedVendors();
    }
}
