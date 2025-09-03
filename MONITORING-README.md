# ShenYu 监控环境设置指南

## 🎯 架构说明

```
本地 ShenYu Admin (9095) ────┐
                              ├──> Docker Prometheus (9090) ──> Docker Grafana (3000)
本地 ShenYu Bootstrap (9195) ──┘
```

## 🚀 快速启动

### 1. 确保 ShenYu 服务运行
```bash
# 启动 ShenYu Admin (9095端口)
# 启动 ShenYu Bootstrap (9195端口)
```

### 2. 启动监控环境
```bash
./start-monitoring.sh
```

### 3. 访问监控界面
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 
  - 用户名: `admin`
  - 密码: `admin123`

## 📊 监控指标

### 自定义安全插件指标
- `security_api_calls_total` - API调用总数
- `security_api_calls_duration_seconds` - API响应时间
- `security_api_response_size` - API响应大小

### Hystrix 线程池指标  
- `hystrix_threadpool_active_threads` - 活跃线程数
- `hystrix_threadpool_queue_size` - 队列大小
- `hystrix_threadpool_completed_tasks` - 完成任务数

### 连接池指标
- `hikaricp_connections_active` - 活跃连接数
- `hikaricp_connections_pending` - 等待连接数
- `hikaricp_connections_max` - 最大连接数

## 🎨 Grafana 仪表盘

预配置的 `ShenYu 监控仪表盘` 包含：
1. **安全API调用速率** - 监控各厂商API调用频率
2. **Hystrix线程池监控** - 线程池状态和队列情况  
3. **连接池监控** - 数据库连接池使用情况
4. **API响应时间** - 各API的响应时间分析

## 🔧 管理命令

```bash
# 启动监控环境
./start-monitoring.sh

# 停止监控环境  
./stop-monitoring.sh

# 查看容器状态
docker-compose -f docker-compose-monitoring.yml ps

# 查看日志
docker-compose -f docker-compose-monitoring.yml logs
```

## 🐛 故障排除

### 问题1: Prometheus 无法抓取指标
- 检查 ShenYu 服务是否运行
- 验证端点可访问: `curl http://localhost:9095/actuator/prometheus`

### 问题2: Grafana 仪表盘无数据
- 确认 Prometheus 数据源配置正确
- 检查指标名称是否匹配

### 问题3: 容器启动失败
- 检查端口占用: `lsof -i :9090,3000`
- 查看容器日志排查问题

## 📈 扩展配置

### 添加新的监控目标
编辑 `monitoring/prometheus.yml`，添加新的 `scrape_configs`

### 自定义仪表盘
1. 在 Grafana 中创建新仪表盘
2. 导出 JSON 配置
3. 保存到 `monitoring/grafana/dashboards/` 目录

### 告警配置
可以配置 Grafana 告警规则，当指标异常时发送通知。
