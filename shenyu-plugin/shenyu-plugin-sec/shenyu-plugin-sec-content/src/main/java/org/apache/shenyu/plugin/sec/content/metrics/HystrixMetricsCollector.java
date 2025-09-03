package org.apache.shenyu.plugin.sec.content.metrics;

import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolKey;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Hystrix指标收集器
 */
public class HystrixMetricsCollector {
    
    private static final Logger LOG = LoggerFactory.getLogger(HystrixMetricsCollector.class);
    
    private final SecurityMetricsCollector metricsCollector;
    
    public HystrixMetricsCollector(SecurityMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        LOG.info("Hystrix metrics collector initialized");
    }
    
    /**
     * 定期收集Hystrix线程池指标
     */
    @Scheduled(fixedDelay = 5000) // 每5秒收集一次
    public void collectHystrixMetrics() {
        try {
            Collection<HystrixThreadPoolMetrics> threadPoolMetrics = HystrixThreadPoolMetrics.getInstances();
            
            for (HystrixThreadPoolMetrics metrics : threadPoolMetrics) {
                String poolName = metrics.getThreadPoolKey().name();
                
                // 收集线程池指标
                metricsCollector.updateThreadPoolActiveThreads(poolName, 
                    metrics.getCurrentActiveCount().intValue());
                    
                metricsCollector.updateThreadPoolQueueSize(poolName, 
                    metrics.getCurrentQueueSize().intValue());
                    
                // 累计完成任务数
                long completedTasks = metrics.getCurrentCompletedTaskCount().longValue();
                metricsCollector.updateThreadPoolCompletedTasks(poolName, completedTasks);
                
                LOG.debug("Collected Hystrix metrics for pool: {} - active: {}, queue: {}, completed: {}", 
                    poolName, 
                    metrics.getCurrentActiveCount().intValue(),
                    metrics.getCurrentQueueSize().intValue(), 
                    completedTasks);
            }
        } catch (Exception e) {
            LOG.warn("Failed to collect Hystrix metrics: {}", e.getMessage());
        }
    }
}
