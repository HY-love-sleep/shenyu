#!/bin/bash

echo "🚀 启动 ShenYu 监控环境"
echo "================================"

# 检查 ShenYu 服务是否运行
echo "📊 检查 ShenYu 服务状态..."

# 检查 Admin
if curl -s http://localhost:9095/actuator/health > /dev/null; then
    echo "✅ ShenYu Admin (9095) - 运行中"
else
    echo "❌ ShenYu Admin (9095) - 未运行"
    echo "请先启动 ShenYu Admin"
fi

# 检查 Bootstrap  
if curl -s http://localhost:9195/actuator/health > /dev/null; then
    echo "✅ ShenYu Bootstrap (9195) - 运行中"
else
    echo "❌ ShenYu Bootstrap (9195) - 未运行"
    echo "请先启动 ShenYu Bootstrap"
fi

echo ""
echo "🐳 启动 Prometheus 和 Grafana..."

# 启动监控服务
docker-compose -f docker-compose-monitoring.yml up -d

echo ""
echo "⏳ 等待服务启动..."
sleep 10

# 检查服务状态
echo ""
echo "📋 监控服务状态:"
echo "================================"

if curl -s http://localhost:9090/-/healthy > /dev/null; then
    echo "✅ Prometheus (9090) - 运行中"
    echo "   访问地址: http://localhost:9090"
else
    echo "❌ Prometheus (9090) - 启动失败"
fi

if curl -s http://localhost:3000/api/health > /dev/null; then
    echo "✅ Grafana (3000) - 运行中"  
    echo "   访问地址: http://localhost:3000"
    echo "   用户名: admin"
    echo "   密码: admin123"
else
    echo "❌ Grafana (3000) - 启动失败"
fi

echo ""
echo "🎯 监控环境已就绪!"
echo "================================"
echo "• Prometheus: http://localhost:9090"
echo "• Grafana: http://localhost:3000 (admin/admin123)"
echo "• ShenYu Admin 指标: http://localhost:9095/actuator/prometheus"
echo "• ShenYu Bootstrap 指标: http://localhost:9195/actuator/prometheus"
echo ""
echo "💡 提示: Grafana 仪表盘会自动导入，请在 'ShenYu' 文件夹中查找"
