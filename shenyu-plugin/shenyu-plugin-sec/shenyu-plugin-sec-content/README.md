# Content Security Response Decorator

## 概述

`ContentSecurityResponseDecorator` 是一个用于 Apache ShenYu 网关的内容安全检测装饰器，专门处理 LLM/流式响应的内容安全检测。该装饰器采用累积式检测机制，确保流式响应内容符合安全要求。

## 核心特性

### 1. 累积式检测机制
- **累积内容**：每次检测时包含之前的所有内容
- **阈值控制**：防止内容无限增长，超过阈值时丢弃前面的内容
- **实时检测**：在流式响应过程中实时进行内容安全检测

### 2. 配置参数
```java
// 累积式检测配置
private static final int BATCH_SIZE = 20; // 每批处理的token数量
private static final int WINDOW_SIZE = 200; // 累积内容阈值
```

### 3. 工作流程

#### 初始化阶段
1. 为每个请求创建唯一的状态管理器
2. 初始化累积内容缓冲区
3. 设置请求ID作为状态键

#### 处理阶段
1. **内容提取**：从SSE响应中提取有效内容
2. **内容累积**：将新内容追加到累积缓冲区
3. **阈值检查**：检查累积内容是否达到检测阈值
4. **安全检测**：调用第三方内容安全API进行检测
5. **结果处理**：根据检测结果决定是否拦截响应

#### 清理阶段
- 响应完成后自动清理请求状态
- 释放内存资源

## 技术实现

### 状态管理
```java
private static final ConcurrentHashMap<String, DecoratorState> STATE_MAP = new ConcurrentHashMap<>();
```

使用 `ConcurrentHashMap` 管理每个请求的状态，确保线程安全。

### 累积内容缓冲区
```java
private static class AccumulativeContentBuffer {
    private final int maxSize;
    private final StringBuilder contentBuffer;
    
    public void addContent(String content) {
        contentBuffer.append(content);
        if (contentBuffer.length() > maxSize) {
            // 丢弃前面的内容，保持阈值
            int charsToRemove = contentBuffer.length() - maxSize;
            contentBuffer.delete(0, charsToRemove);
        }
    }
}
```

### SSE内容处理
- 解析 `data:` 开头的SSE行
- 提取JSON格式的内容
- 跳过 `[DONE]` 结束标记
- 累积有效内容

### 内容安全检测
```java
ContentSecurityChecker.checkText(
    ContentSecurityChecker.SafetyCheckRequest.forContent(
        handle.getAccessKey(), 
        handle.getAccessToken(), 
        handle.getAppId(), 
        accumulatedContent
    ),
    handle
)
```

## 使用示例

### 配置参数
```yaml
shenyu:
  plugin:
    sec:
      content:
        batch-size: 20      # 每批处理token数量
        window-size: 200    # 累积内容阈值
```

### 检测流程示例

```
第1批: "Hello, how are you today?" (25字符)
累积: 25字符 < 200阈值 → 不检测，直接输出

第2批: "I'm doing well, thank you for asking. The weather is nice today." (65字符)
累积: 25 + 65 = 90字符 < 200阈值 → 不检测，直接输出

第3批: "Let me tell you about a very long story that goes on and on..." (120字符)
累积: 90 + 120 = 210字符 > 200阈值
丢弃: 前10字符 → 保留200字符
检测: 调用内容安全API
结果: 通过 → 输出响应
```

## 日志输出

### 关键日志
- **送审内容**: `LOG.info("送审内容: {}", accumulatedContent);`
- **错误状态**: `LOG.error("Decorator state not found for request: {}", stateKey);`

### 日志示例
```
INFO - 送审内容: Hello, how are you today? I'm doing well, thank you for asking. The weather is nice today. Let me tell you about a very long story that goes on and on...
```

## 错误处理

### 检测失败
当内容安全检测失败时：
- 设置 `SEC_ERROR` 属性
- 返回错误响应：`{"code":1401,"msg":"返回内容违规","detail":"检测结果：违规"}`
- 后续所有响应将被拦截

### 状态管理错误
- 自动清理无效状态
- 线程安全的状态管理
- 防止内存泄漏

## 性能优化

### 1. 累积机制
- 减少API调用频率
- 提高检测效率
- 降低延迟

### 2. 阈值控制
- 防止内容无限增长
- 控制内存使用
- 保持响应性能

### 3. 状态清理
- 自动清理完成的状态
- 防止内存泄漏
- 提高系统稳定性

## 兼容性

### Hystrix熔断器
- 完全兼容现有的 `ContentSecurityChecker`
- 支持熔断器配置
- 保持故障容错能力

### 流式响应
- 支持SSE格式
- 支持流式JSON响应
- 保持响应流连续性

## 配置建议

### 阈值设置
- **小阈值 (50-100字符)**: 快速检测，适合敏感内容
- **中等阈值 (200-500字符)**: 平衡性能和准确性
- **大阈值 (1000+字符)**: 减少API调用，适合大批量内容

### 批次大小
- **小批次 (10-20)**: 快速响应，适合实时交互
- **大批次 (50-100)**: 减少处理开销，适合批量处理

## 注意事项

1. **内存使用**: 累积缓冲区会占用内存，建议根据实际需求调整阈值
2. **API限制**: 注意第三方内容安全API的调用频率限制
3. **延迟影响**: 检测会增加响应延迟，建议在可接受的范围内
4. **错误处理**: 确保正确处理检测失败的情况

## 扩展性

该实现具有良好的扩展性：
- 支持不同的内容安全API
- 可配置的检测策略
- 灵活的阈值控制
- 可扩展的状态管理机制 