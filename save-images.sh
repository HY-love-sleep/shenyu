#!/bin/bash

# 离线镜像保存脚本
# 保存所有需要的镜像到tar文件

set -e

echo "开始保存Docker镜像到tar文件..."

# 创建输出目录
mkdir -p offline-images

# 保存基础镜像
echo "保存基础镜像..."
docker save -o offline-images/amazoncorretto-17-alpine.tar amazoncorretto:17.0.11-alpine3.19
docker save -o offline-images/alpine.tar alpine:latest

# 保存应用镜像（包括x86架构）
echo "保存应用镜像..."

# 检查并保存x86架构镜像
if docker images shenyu-admin:latest-x86 | grep -q "latest-x86"; then
    docker save -o offline-images/shenyu-admin-x86.tar shenyu-admin:latest-x86
    echo "✓ 保存 shenyu-admin:latest-x86"
else
    echo "⚠ 未找到 shenyu-admin:latest-x86 镜像"
fi

if docker images shenyu-bootstrap:latest-x86 | grep -q "latest-x86"; then
    docker save -o offline-images/shenyu-bootstrap-x86.tar shenyu-bootstrap:latest-x86
    echo "✓ 保存 shenyu-bootstrap:latest-x86"
else
    echo "⚠ 未找到 shenyu-bootstrap:latest-x86 镜像"
fi

# 也保存当前架构的镜像（如果存在）
if docker images shenyu-admin:latest | grep -q "latest"; then
    docker save -o offline-images/shenyu-admin.tar shenyu-admin:latest
    echo "✓ 保存 shenyu-admin:latest"
else
    echo "⚠ 未找到 shenyu-admin:latest 镜像"
fi

if docker images shenyu-bootstrap:latest | grep -q "latest"; then
    docker save -o offline-images/shenyu-bootstrap.tar shenyu-bootstrap:latest
    echo "✓ 保存 shenyu-bootstrap:latest"
else
    echo "⚠ 未找到 shenyu-bootstrap:latest 镜像"
fi

# 创建镜像列表文件
cat > offline-images/images.txt << EOF
# Docker镜像列表
# 使用以下命令加载镜像:
# docker load -i <镜像文件名>

amazoncorretto:17.0.11-alpine3.19
alpine:latest

# 应用镜像（x86架构，适用于Linux服务器）
shenyu-admin:latest-x86
shenyu-bootstrap:latest-x86

# 应用镜像（当前架构）
shenyu-admin:latest
shenyu-bootstrap:latest

# 加载命令:
# docker load -i amazoncorretto-17-alpine.tar
# docker load -i alpine.tar
# docker load -i shenyu-admin-x86.tar
# docker load -i shenyu-bootstrap-x86.tar
# docker load -i shenyu-admin.tar
# docker load -i shenyu-bootstrap.tar
EOF

echo "镜像保存完成！"
echo "输出目录: offline-images/"
echo "镜像文件:"
ls -la offline-images/
