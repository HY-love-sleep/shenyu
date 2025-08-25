package org.apache.shenyu.plugin.sec.content;

/**
 * 内容安全检测结果
 * 统一的检测结果格式，供网关使用
 * 
 * @author yHong
 * @version 1.0
 * @since 2025/8/21
 */
public class ContentSecurityResult {
    
    /**
     * 是否通过检测
     */
    private boolean passed;
    
    /**
     * 风险等级
     */
    private String riskLevel;
    
    /**
     * 风险描述
     */
    private String riskDescription;
    
    /**
     * 厂商标识
     */
    private String vendor;
    
    /**
     * 厂商原始结果（保留完整信息）
     */
    private Object vendorResult;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 检测是否成功
     */
    private boolean success;
    
    /**
     * 检测响应码
     */
    private String code;
    
    /**
     * 检测响应消息
     */
    private String message;
    
    public ContentSecurityResult() {
    }
    
    /**
     * 创建成功通过的结果
     */
    public static ContentSecurityResult passed(String vendor, Object vendorResult) {
        ContentSecurityResult result = new ContentSecurityResult();
        result.setPassed(true);
        result.setRiskLevel("PASS");
        result.setRiskDescription("内容检测通过");
        result.setVendor(vendor);
        result.setVendorResult(vendorResult);
        result.setSuccess(true);
        result.setCode("1100");
        result.setMessage("成功");
        return result;
    }
    
    /**
     * 创建检测失败的结果
     */
    public static ContentSecurityResult failed(String vendor, String riskLevel, String riskDescription, Object vendorResult) {
        ContentSecurityResult result = new ContentSecurityResult();
        result.setPassed(false);
        result.setRiskLevel(riskLevel);
        result.setRiskDescription(riskDescription);
        result.setVendor(vendor);
        result.setVendorResult(vendorResult);
        result.setSuccess(true);
        result.setCode("1100");
        result.setMessage("成功");
        return result;
    }
    
    /**
     * 创建检测异常的结果
     */
    public static ContentSecurityResult error(String vendor, String errorMessage, String code, String message) {
        ContentSecurityResult result = new ContentSecurityResult();
        result.setPassed(false);
        result.setRiskLevel("ERROR");
        result.setRiskDescription("检测异常");
        result.setVendor(vendor);
        result.setErrorMessage(errorMessage);
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
    
    // Getters and Setters
    
    public boolean isPassed() {
        return passed;
    }
    
    public void setPassed(boolean passed) {
        this.passed = passed;
    }
    
    public String getRiskLevel() {
        return riskLevel;
    }
    
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
    
    public String getRiskDescription() {
        return riskDescription;
    }
    
    public void setRiskDescription(String riskDescription) {
        this.riskDescription = riskDescription;
    }
    
    public String getVendor() {
        return vendor;
    }
    
    public void setVendor(String vendor) {
        this.vendor = vendor;
    }
    
    public Object getVendorResult() {
        return vendorResult;
    }
    
    public void setVendorResult(Object vendorResult) {
        this.vendorResult = vendorResult;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    @Override
    public String toString() {
        return "ContentSecurityResult{" +
                "passed=" + passed +
                ", riskLevel='" + riskLevel + '\'' +
                ", riskDescription='" + riskDescription + '\'' +
                ", vendor='" + vendor + '\'' +
                ", success=" + success +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
