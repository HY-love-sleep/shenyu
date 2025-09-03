# 安全插件监控指标说明

本文档说明如何使用和配置安全插件的监控指标功能。

## 功能概述

安全插件监控系统提供以下监控能力：

1. **Hystrix线程池监控**
   - 活跃线程数
   - 队列长度
   - 拒绝任务数
   - 完成任务数

2. **连接池监控**
   - 活跃连接数
   - 等待获取连接数
   - 连接获取时间

3. **API调用监控**
   - 调用总数
   - 成功率
   - 失败率
   - 调用时长
   - 响应大小

## 配置方式

### 1. 启用监控功能

在 `shenyu-admin/src/main/resources/application.yml` 中添加以下配置（监控配置已集成）：

```yaml
management:
  endpoints:
    web:
      exposure:
        include:
          - 'health'
          - 'prometheus'
          - 'metrics'
  endpoint:
    metrics:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        "[security.api.calls.duration]": true
        "[watermark.api.calls.duration]": true
      percentiles:
        "[security.api.calls.duration]": 0.5,0.75,0.9,0.95,0.99
        "[watermark.api.calls.duration]": 0.5,0.75,0.9,0.95,0.99

# 插件启用配置
shenyu:
  plugins:
    content-security:
      enabled: true  # 启用内容安全插件
    content-mark:
      enabled: true  # 启用水印插件
```

**注意**：监控功能通过Spring Boot Starter自动配置启用，当MeterRegistry Bean存在时自动激活。

### 2. 监控指标端点

启用后，可以通过以下端点访问监控指标：

- **Prometheus格式**: `http://localhost:8080/actuator/prometheus`
- **JSON格式**: `http://localhost:8080/actuator/metrics`

## 监控指标详解

### Hystrix线程池指标

| 指标名称 | 描述 | 标签 |
|---------|------|------|
| `hystrix.threadpool.active.threads` | 活跃线程数 | `pool`: 线程池名称 |
| `hystrix.threadpool.queue.size` | 队列长度 | `pool`: 线程池名称 |
| `hystrix.threadpool.rejected.tasks` | 拒绝任务数 | `pool`: 线程池名称 |
| `hystrix.threadpool.completed.tasks` | 完成任务数 | `pool`: 线程池名称 |

### 连接池指标

| 指标名称 | 描述 | 标签 |
|---------|------|------|
| `connection.pool.active.connections` | 活跃连接数 | `pool`: 连接池名称 |
| `connection.pool.pending.acquire` | 等待获取连接数 | `pool`: 连接池名称 |
| `connection.pool.acquire.time` | 连接获取时间 | `pool`: 连接池名称 |

### API调用指标

| 指标名称 | 描述 | 标签 |
|---------|------|------|
| `security.api.calls.total` | API调用总数 | `vendor`, `operation` |
| `security.api.calls.success` | 成功调用数 | `vendor`, `operation` |
| `security.api.calls.failure` | 失败调用数 | `vendor`, `operation`, `error` |
| `security.api.calls.duration` | 调用时长 | `vendor`, `operation` |
| `security.api.response.size` | 响应大小 | `vendor` |
| `watermark.api.calls.total` | 水印API调用总数 | `vendor`, `operation` |
| `watermark.api.calls.success` | 水印成功调用数 | `vendor`, `operation` |
| `watermark.api.calls.failure` | 水印失败调用数 | `vendor`, `operation`, `error` |
| `watermark.api.calls.duration` | 水印调用时长 | `vendor`, `operation` |
| `watermark.api.response.size` | 水印响应大小 | `vendor` |

### 标签说明

- **vendor**: 厂商名称 (`shumei`, `zkrj`, `watermark`)
- **operation**: 操作类型 (`checkText`, `addMark`)
- **error**: 错误类型 (`timeout`, `hystrix_fallback`, `circuit_breaker_open`, 等)
- **pool**: 池名称 (`shumei-pool`, `zkrj-pool`, `watermark-pool`, `ContentSecurityPool`, `WaterMarkPool`)

## Grafana仪表盘

### 1. 推荐的Grafana查询

**API成功率**:
```promql
rate(security_api_calls_success_total[5m]) / rate(security_api_calls_total[5m]) * 100
```

**API平均响应时间**:
```promql
rate(security_api_calls_duration_seconds_sum[5m]) / rate(security_api_calls_duration_seconds_count[5m]) * 1000
```

**线程池使用率**:
```promql
hystrix_threadpool_active_threads / on(pool) group_left() (hystrix_threadpool_active_threads + hystrix_threadpool_queue_size) * 100
```

**连接池使用率**:
```promql
connection_pool_active_connections / on(pool) group_left() connection_pool_max_connections * 100
```

### 2. 告警规则示例

```yaml
groups:
  - name: shenyu-security
    rules:
      - alert: SecurityAPIHighFailureRate
        expr: rate(security_api_calls_failure_total[5m]) / rate(security_api_calls_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "安全API失败率过高"
          description: "{{ $labels.vendor }} API失败率超过10%"
      
      - alert: HystrixThreadPoolExhausted
        expr: hystrix_threadpool_active_threads / (hystrix_threadpool_active_threads + hystrix_threadpool_queue_size) > 0.9
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Hystrix线程池接近耗尽"
          description: "{{ $labels.pool }} 线程池使用率超过90%"
```

## 性能优化建议

### 1. 线程池调优

基于监控指标调整Hystrix线程池配置：

```yaml
shenyu:
  plugin:
    security:
      hystrix:
        thread-pool:
          core-size: 1000        # 根据 active.threads 指标调整
          max-size: 1200         # 根据峰值负载调整
          queue-capacity: 50     # 根据 queue.size 指标调整
```

### 2. 连接池调优

根据连接池监控指标调整连接数：

```java
// 在代码中调整连接池配置
private static final ConnectionProvider PROVIDER = ConnectionProvider.builder("pool-name")
    .maxConnections(1200)      // 根据 active.connections 指标调整
    .pendingAcquireMaxCount(15000)  // 根据 pending.acquire 指标调整
    .build();
```

### 3. 监控数据采集频率

```yaml
shenyu:
  plugin:
    security:
      metrics:
        collection-interval: 5000  # 根据性能需求调整采集间隔
```

## 故障排查

### 1. 常见问题

**指标不显示**:
- 检查 `management.endpoints.web.exposure.include` 是否包含 `prometheus`
- 确认 `shenyu.plugin.security.metrics.enabled=true`
- 查看日志中是否有配置错误

**性能影响**:
- 监控组件采用异步方式，对性能影响minimal
- 可通过调整 `collection-interval` 降低采集频率

### 2. 日志配置

启用详细日志以排查问题：

```yaml
logging:
  level:
    org.apache.shenyu.plugin.sec.content.metrics: DEBUG
    org.apache.shenyu.plugin.sec.mark.metrics: DEBUG
```

## 注意事项

1. **生产环境使用**: 监控功能对性能影响很小，可以安全在生产环境启用
2. **数据存储**: Prometheus指标默认存储在内存中，重启后清零
3. **网络开销**: 指标暴露会增加少量网络流量
4. **安全考虑**: 建议在内网环境访问监控端点，或配置适当的访问控制
