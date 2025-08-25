# 内容安全检测系统

## 概述

本系统采用策略模式实现，支持多种厂商的内容安全检测服务，包括：

- **ZKRJ** - 原有的内容安全检测服务
- **数美** - 新增的内容安全检测服务

## 架构设计

### 核心组件

1. **ContentSecurityChecker** - 检测器接口
2. **ContentSecurityResult** - 统一的检测结果格式
3. **ContentSecurityCheckerFactory** - 检测器工厂
4. **ContentSecurityService** - 统一的服务接口

### 策略模式

```
ContentSecurityChecker (接口)
├── ContentSecurityCheckerZkrj (ZKRJ实现)
└── ContentSecurityCheckerSm (数美实现)
```

## 使用方法

### 1. 基础配置

在 `ContentSecurityHandle` 中设置 `vendor` 字段来选择检测器：

```java
ContentSecurityHandle handle = new ContentSecurityHandle();
handle.setVendor("zkrj");        // 使用ZKRJ检测器
handle.setVendor("shumei");      // 使用数美检测器
```

**注意**: 如果不设置 `vendor` 字段，系统默认使用 `zkrj` 检测器，保持向后兼容。

### 2. 使用统一服务

```java
@Autowired
private ContentSecurityService contentSecurityService;

// 自动选择检测器
Mono<ContentSecurityResult> result = contentSecurityService.checkText(text, handle);

// 指定厂商检测
Mono<ContentSecurityResult> zkrjResult = contentSecurityService.checkTextWithZkrj(text, handle);
Mono<ContentSecurityResult> shumeiResult = contentSecurityService.checkTextWithShumei(text, handle);
```

### 3. 直接使用检测器

```java
@Autowired
private ContentSecurityCheckerFactory checkerFactory;

ContentSecurityChecker checker = checkerFactory.getChecker(handle);
Mono<ContentSecurityResult> result = checker.checkText(request, handle);
```

## 配置示例

### ZKRJ配置

```json
{
  "accessKey": "your_zkrj_access_key",
  "accessToken": "your_zkrj_access_token",
  "appId": "your_zkrj_app_id",
  "url": "http://your-zkrj-api-endpoint",
  "vendor": "zkrj"
}
```

### 数美配置

```json
{
  "accessKey": "your_shumei_access_key",
  "appId": "your_shumei_app_id",
  "eventId": "text",
  "type": "TEXTRISK",
  "url": "http://api-text-bj.fengkongcloud.com/text/v4",
  "vendor": "shumei"
}
```

## 检测结果

### 统一结果格式

```java
public class ContentSecurityResult {
    private boolean passed;           // 是否通过检测
    private String riskLevel;         // 风险等级
    private String riskDescription;   // 风险描述
    private String vendor;            // 厂商标识
    private Object vendorResult;      // 厂商原始结果
    private String errorMessage;      // 错误信息
    private boolean success;          // 检测是否成功
    private String code;              // 检测响应码
    private String message;           // 检测响应消息
}
```

### 结果处理

```java
result.subscribe(resp -> {
    if (resp.isSuccess()) {
        if (resp.isPassed()) {
            LOG.info("内容检测通过，厂商: {}", resp.getVendor());
        } else {
            LOG.warn("检测到风险内容: {}, 风险等级: {}, 厂商: {}", 
                resp.getRiskDescription(), resp.getRiskLevel(), resp.getVendor());
        }
    } else {
        LOG.error("检测失败: {}, 厂商: {}", resp.getErrorMessage(), resp.getVendor());
    }
});
```

## 扩展新厂商

### 1. 实现接口

```java
@Component
public class NewVendorChecker implements ContentSecurityChecker {
    
    @Override
    public Mono<ContentSecurityResult> checkText(Object request, ContentSecurityHandle handle) {
        // 实现检测逻辑
    }
    
    @Override
    public String getVendor() {
        return "newvendor";
    }
    
    @Override
    public boolean supports(String vendor) {
        return "newvendor".equalsIgnoreCase(vendor);
    }
}
```

### 2. 添加配置支持

在 `ContentSecurityService` 中添加新的case：

```java
case "newvendor":
    return checkTextWithNewVendor(text, handle);
```

## 特性

### 高可用性
- Hystrix熔断器保护
- 超时控制
- 错误降级处理

### 异步处理
- 基于Reactor的响应式编程
- 非阻塞I/O
- 高并发支持

### 可配置性
- 支持多种风险类型检测
- 灵活的配置参数
- 多集群支持

### 扩展性
- 支持多厂商集成
- 模块化设计
- 易于维护和扩展

## 注意事项

1. **厂商选择**: 通过 `vendor` 字段选择检测器，默认为 `zkrj`
2. **配置兼容**: 保持与现有ZKRJ配置的完全兼容
3. **错误处理**: 统一的错误处理和降级机制
4. **性能优化**: 基于Hystrix的线程池管理和熔断器保护

## 测试

运行测试用例：

```bash
mvn test -Dtest=ContentSecurityCheckerSmTest
mvn test -Dtest=ContentSecurityCheckerZkrjTest
```

## 总结

本系统成功实现了：

1. **策略模式**: 支持多种厂商检测器
2. **统一接口**: 提供一致的检测结果格式
3. **向后兼容**: 不影响现有ZKRJ检测器使用
4. **易于扩展**: 新增厂商只需实现接口即可
5. **高可用性**: 集成熔断器和错误处理机制
6. **插件集成**: 已集成到ContentSecurityPlugin和ContentSecurityResponseDecorator中

## 插件集成说明

### ContentSecurityPlugin
- 支持多厂商的请求内容检测
- 根据配置自动选择对应的检测器
- 保持原有的检测逻辑和错误处理

### ContentSecurityResponseDecorator
- 支持多厂商的响应内容检测
- 保持现有的累积式检测机制
- 根据配置自动选择对应的检测器
- 支持流式响应的分块检测

## 配置类说明

### ContentSecurityPluginConfiguration
所有相关的Bean都在`ContentSecurityPluginConfiguration`中配置，包括：

- `ContentSecurityPlugin` - 主插件Bean
- `ContentSecurityService` - 统一检测服务Bean
- `ContentSecurityCheckerFactory` - 检测器工厂Bean
- `ContentSecurityPluginDataHandler` - 插件数据处理器Bean

### 自动注入
检测器实现类（如`ContentSecurityCheckerZkrj`、`ContentSecurityCheckerSm`）会自动被Spring发现并注入到`ContentSecurityCheckerFactory`中，无需手动配置。 