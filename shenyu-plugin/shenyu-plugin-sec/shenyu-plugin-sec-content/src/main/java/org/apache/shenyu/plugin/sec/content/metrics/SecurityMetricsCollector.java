package org.apache.shenyu.plugin.sec.content.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Metrics;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 安全插件监控指标收集器
 */
public class SecurityMetricsCollector {
    
    private static final Logger LOG = LoggerFactory.getLogger(SecurityMetricsCollector.class);
    
    private final MeterRegistry meterRegistry;
    
    // Hystrix 线程池相关指标
    private final ConcurrentHashMap<String, AtomicLong> threadPoolActiveThreads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> threadPoolQueueSize = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> threadPoolRejectedTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> threadPoolCompletedTasks = new ConcurrentHashMap<>();
    
    // 连接池相关指标  
    private final ConcurrentHashMap<String, AtomicLong> connectionPoolActiveConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> connectionPoolPendingAcquire = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DoubleAdder> connectionPoolAcquireTime = new ConcurrentHashMap<>();
    
    // API 调用相关指标
    private final Counter apiCallTotal;
    private final Counter apiCallSuccess;
    private final Counter apiCallFailure;
    private final Timer apiCallDuration;
    private final DistributionSummary responseSize;
    
    public SecurityMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化计数器
        this.apiCallTotal = Counter.builder("security.api.calls.total")
                .description("Total number of security API calls")
                .register(meterRegistry);
                
        this.apiCallSuccess = Counter.builder("security.api.calls.success")
                .description("Number of successful security API calls")
                .register(meterRegistry);
                
        this.apiCallFailure = Counter.builder("security.api.calls.failure")
                .description("Number of failed security API calls")
                .register(meterRegistry);
                
        // 统一下划线风格，Prometheus中会导出为 security_api_calls_duration_seconds_*
        this.apiCallDuration = Timer.builder("security_api_calls_duration")
                .description("Duration of security API calls")
                .register(meterRegistry);
                
        this.responseSize = DistributionSummary.builder("security_api_response_size")
                .description("Size of security API responses")
                .register(meterRegistry);
    }
    
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
    
    /**
     * 初始化线程池监控指标
     */
    public void initThreadPoolMetrics(String poolName) {
        threadPoolActiveThreads.putIfAbsent(poolName, new AtomicLong(0));
        threadPoolQueueSize.putIfAbsent(poolName, new AtomicLong(0));
        threadPoolRejectedTasks.putIfAbsent(poolName, new AtomicLong(0));
        threadPoolCompletedTasks.putIfAbsent(poolName, new AtomicLong(0));
        
        // 注册Gauge指标
        Gauge.builder("hystrix.threadpool.active.threads", threadPoolActiveThreads.get(poolName), AtomicLong::get)
                .description("Number of active threads in Hystrix thread pool")
                .tags(Tags.of("pool", poolName))
                .register(meterRegistry);
                
        Gauge.builder("hystrix.threadpool.queue.size", threadPoolQueueSize.get(poolName), AtomicLong::get)
                .description("Size of Hystrix thread pool queue")
                .tags(Tags.of("pool", poolName))
                .register(meterRegistry);
                
        Gauge.builder("hystrix.threadpool.rejected.tasks", threadPoolRejectedTasks.get(poolName), AtomicLong::get)
                .description("Number of rejected tasks in Hystrix thread pool")
                .tags(Tags.of("pool", poolName))
                .register(meterRegistry);
                
        Gauge.builder("hystrix.threadpool.completed.tasks", threadPoolCompletedTasks.get(poolName), AtomicLong::get)
                .description("Number of completed tasks in Hystrix thread pool")
                .tags(Tags.of("pool", poolName))
                .register(meterRegistry);
    }
    
    /**
     * 初始化连接池监控指标
     */
    public void initConnectionPoolMetrics(String poolName) {
        connectionPoolActiveConnections.putIfAbsent(poolName, new AtomicLong(0));
        connectionPoolPendingAcquire.putIfAbsent(poolName, new AtomicLong(0));
        connectionPoolAcquireTime.putIfAbsent(poolName, new DoubleAdder());
        
        // 注册Gauge指标
        Gauge.builder("connection.pool.active.connections", connectionPoolActiveConnections.get(poolName), AtomicLong::get)
                .description("Number of active connections in pool")
                .tags(Tags.of("pool", poolName))
                .register(meterRegistry);
                
        Gauge.builder("connection.pool.pending.acquire", connectionPoolPendingAcquire.get(poolName), AtomicLong::get)
                .description("Number of pending acquire requests in connection pool")
                .tags(Tags.of("pool", poolName))
                .register(meterRegistry);
                
        Gauge.builder("connection.pool.acquire.time", connectionPoolAcquireTime.get(poolName), DoubleAdder::sum)
                .description("Average connection acquire time")
                .tags(Tags.of("pool", poolName))
                .register(meterRegistry);
    }
    
    // 更新指标的方法
    public void updateThreadPoolActiveThreads(String poolName, long value) {
        AtomicLong counter = threadPoolActiveThreads.get(poolName);
        if (counter != null) {
            counter.set(value);
        }
    }
    
    public void updateThreadPoolQueueSize(String poolName, long value) {
        AtomicLong counter = threadPoolQueueSize.get(poolName);
        if (counter != null) {
            counter.set(value);
        }
    }
    
    public void incrementThreadPoolRejectedTasks(String poolName) {
        AtomicLong counter = threadPoolRejectedTasks.get(poolName);
        if (counter != null) {
            counter.incrementAndGet();
        }
    }
    
    public void incrementThreadPoolCompletedTasks(String poolName) {
        AtomicLong counter = threadPoolCompletedTasks.get(poolName);
        if (counter != null) {
            counter.incrementAndGet();
        }
    }
    
    public void updateThreadPoolCompletedTasks(String poolName, long value) {
        AtomicLong counter = threadPoolCompletedTasks.get(poolName);
        if (counter != null) {
            counter.set(value);
        }
    }
    
    public void updateConnectionPoolActiveConnections(String poolName, long value) {
        AtomicLong counter = connectionPoolActiveConnections.get(poolName);
        if (counter != null) {
            counter.set(value);
        }
    }
    
    public void updateConnectionPoolPendingAcquire(String poolName, long value) {
        AtomicLong counter = connectionPoolPendingAcquire.get(poolName);
        if (counter != null) {
            counter.set(value);
        }
    }
    
    public void recordConnectionAcquireTime(String poolName, double timeMs) {
        DoubleAdder adder = connectionPoolAcquireTime.get(poolName);
        if (adder != null) {
            adder.add(timeMs);
        }
    }
    
    public void recordApiCall(String vendor, String operation) {
        try {
            Counter counter = meterRegistry.counter("security_api_calls_total", "vendor", vendor, "operation", operation);
            counter.increment();
            // 静默记录，避免日志污染
        } catch (Exception e) {
            LOG.error("METRICS ERROR in recordApiCall: {}", e.getMessage(), e);
        }
    }
    
    public void recordApiSuccess(String vendor, String operation) {
        Counter.builder("security_api_calls_success")
                .tags("vendor", vendor, "operation", operation)
                .register(meterRegistry)
                .increment();
    }
    
    public void recordApiFailure(String vendor, String operation, String errorType) {
        Counter.builder("security_api_calls_failure")
                .tags("vendor", vendor, "operation", operation, "error", errorType)
                .register(meterRegistry)
                .increment();
    }
    
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void stopTimer(Timer.Sample sample, String vendor, String operation) {
        Timer timer = meterRegistry.timer("security_api_calls_duration", "vendor", vendor, "operation", operation);
        sample.stop(timer);
        // 静默记录，避免日志污染
    }
    
    public void recordResponseSize(String vendor, long sizeBytes) {
        DistributionSummary.builder("security_api_response_size")
                .tags("vendor", vendor)
                .register(meterRegistry)
                .record(sizeBytes);
    }
}
