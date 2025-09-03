#!/bin/bash

echo "🛑 停止 ShenYu 监控环境"
echo "================================"

# 停止监控服务
docker-compose -f docker-compose-monitoring.yml down

echo "✅ 监控服务已停止"
echo ""
echo "💡 提示: ShenYu 服务仍在本地运行，如需停止请手动操作"
