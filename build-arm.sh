#!/bin/bash

# 在Windows/Mac上构建ARM架构的Docker镜像

set -e

echo "构建ARM架构的Docker镜像..."

# 清除旧镜像
echo "清除旧镜像..."
docker rmi alpine:latest 2>/dev/null || true
docker rmi amazoncorretto:17-alpine 2>/dev/null || true  # ⭐ 修改版本
docker rmi shenyu-admin:latest-arm 2>/dev/null || true
docker rmi shenyu-bootstrap:latest-arm 2>/dev/null || true

# 拉取 ARM64 基础镜像
echo ""
echo "拉取 ARM64 架构的基础镜像..."
docker pull --platform linux/arm64 alpine:latest
docker pull --platform linux/arm64 amazoncorretto:17-alpine  # ⭐ 修改版本

# 验证架构
echo ""
echo "验证基础镜像架构:"
ALPINE_ARCH=$(docker image inspect alpine:latest --format='{{.Architecture}}')
JDK_ARCH=$(docker image inspect amazoncorretto:17-alpine --format='{{.Architecture}}')  # ⭐ 修改版本

echo "alpine: $ALPINE_ARCH"
echo "amazoncorretto:17-alpine: $JDK_ARCH"

if [ "$ALPINE_ARCH" != "arm64" ] || [ "$JDK_ARCH" != "arm64" ]; then
    echo ""
    echo "❌ 错误: 基础镜像不是 ARM64 架构！"
    exit 1
fi

echo "✅ 基础镜像架构正确"

# 构建应用镜像
echo ""
echo "构建 shenyu-admin ARM 镜像..."
docker build \
  --platform linux/arm64 \
  --tag shenyu-admin:latest-arm \
  --file shenyu-dist/shenyu-admin-dist/docker/Dockerfile \
  --build-arg APP_NAME=apache-shenyu-2.7.0.2-SNAPSHOT-admin-bin \
  shenyu-dist/shenyu-admin-dist

echo ""
echo "构建 shenyu-bootstrap ARM 镜像..."
docker build \
  --platform linux/arm64 \
  --tag shenyu-bootstrap:latest-arm \
  --file shenyu-dist/shenyu-bootstrap-dist/docker/Dockerfile \
  --build-arg APP_NAME=apache-shenyu-2.7.0.2-SNAPSHOT-bootstrap-bin \
  shenyu-dist/shenyu-bootstrap-dist

# 验证应用镜像架构
echo ""
echo "验证应用镜像架构:"
ADMIN_ARCH=$(docker image inspect shenyu-admin:latest-arm --format='{{.Architecture}}')
BOOTSTRAP_ARCH=$(docker image inspect shenyu-bootstrap:latest-arm --format='{{.Architecture}}')

echo "shenyu-admin: $ADMIN_ARCH"
echo "shenyu-bootstrap: $BOOTSTRAP_ARCH"

if [ "$ADMIN_ARCH" != "arm64" ] || [ "$BOOTSTRAP_ARCH" != "arm64" ]; then
    echo ""
    echo "❌ 错误: 应用镜像不是 ARM64 架构！"
    exit 1
fi

echo ""
echo "================================================"
echo "✅ ARM64 架构镜像构建完成！"
echo "================================================"
echo ""
echo "镜像标签:"
echo "  shenyu-admin:latest-arm (arm64)"
echo "  shenyu-bootstrap:latest-arm (arm64)"
echo ""
echo "下一步: 运行 ./save-images.sh arm 保存镜像"
