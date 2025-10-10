#!/bin/bash

# 在Windows/Mac上构建x86架构的Docker镜像
# 使用 --platform linux/amd64 指定目标架构

set -e

echo "构建 x86_64 架构的Docker镜像..."

# 清除旧镜像
echo "清除旧镜像..."
docker rmi alpine:latest 2>/dev/null || true
docker rmi amazoncorretto:17.0.11-alpine3.19 2>/dev/null || true
docker rmi shenyu-admin:latest-x86 2>/dev/null || true
docker rmi shenyu-bootstrap:latest-x86 2>/dev/null || true

# 拉取 x86_64 基础镜像
echo ""
echo "拉取 x86_64 架构的基础镜像..."
docker pull --platform linux/amd64 alpine:latest
docker pull --platform linux/amd64 amazoncorretto:17.0.11-alpine3.19

# 验证架构
echo ""
echo "验证基础镜像架构:"
ALPINE_ARCH=$(docker image inspect alpine:latest --format='{{.Architecture}}')
JDK_ARCH=$(docker image inspect amazoncorretto:17.0.11-alpine3.19 --format='{{.Architecture}}')

echo "alpine: $ALPINE_ARCH"
echo "amazoncorretto:17.0.11-alpine3.19: $JDK_ARCH"

if [ "$ALPINE_ARCH" != "amd64" ] || [ "$JDK_ARCH" != "amd64" ]; then
    echo ""
    echo "❌ 错误: 基础镜像不是 amd64 架构！"
    exit 1
fi

echo "✅ 基础镜像架构正确"

# 构建应用镜像
echo ""
echo "构建 shenyu-admin x86 镜像..."
docker build \
  --platform linux/amd64 \
  --tag shenyu-admin:latest-x86 \
  --file shenyu-dist/shenyu-admin-dist/docker/Dockerfile \
  --build-arg APP_NAME=apache-shenyu-2.7.0.2-SNAPSHOT-admin-bin \
  shenyu-dist/shenyu-admin-dist

echo ""
echo "构建 shenyu-bootstrap x86 镜像..."
docker build \
  --platform linux/amd64 \
  --tag shenyu-bootstrap:latest-x86 \
  --file shenyu-dist/shenyu-bootstrap-dist/docker/Dockerfile \
  --build-arg APP_NAME=apache-shenyu-2.7.0.2-SNAPSHOT-bootstrap-bin \
  shenyu-dist/shenyu-bootstrap-dist

# 验证应用镜像架构
echo ""
echo "验证应用镜像架构:"
ADMIN_ARCH=$(docker image inspect shenyu-admin:latest-x86 --format='{{.Architecture}}')
BOOTSTRAP_ARCH=$(docker image inspect shenyu-bootstrap:latest-x86 --format='{{.Architecture}}')

echo "shenyu-admin: $ADMIN_ARCH"
echo "shenyu-bootstrap: $BOOTSTRAP_ARCH"

if [ "$ADMIN_ARCH" != "amd64" ] || [ "$BOOTSTRAP_ARCH" != "amd64" ]; then
    echo ""
    echo "❌ 错误: 应用镜像不是 amd64 架构！"
    exit 1
fi

echo ""
echo "================================================"
echo "✅ x86_64 架构镜像构建完成！"
echo "================================================"
echo ""
echo "镜像标签:"
echo "  shenyu-admin:latest-x86 (amd64)"
echo "  shenyu-bootstrap:latest-x86 (amd64)"
echo ""
echo "下一步: 运行 ./save-images.sh x86 保存镜像"
