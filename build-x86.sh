#!/bin/bash

# 在Mac上构建x86架构的Docker镜像
# 使用 --platform linux/amd64 指定目标架构

set -e

echo "在Mac上构建x86架构的Docker镜像..."

# 检查网络连接
echo "检查网络连接..."
if ! curl -s --connect-timeout 5 https://registry-1.docker.io/v2/ > /dev/null; then
    echo "警告: 无法连接到 Docker Hub，请检查网络连接"
    echo "如果网络有问题，请先手动拉取基础镜像："
    echo "  docker pull --platform linux/amd64 alpine:latest"
    echo "  docker pull --platform linux/amd64 amazoncorretto:17.0.11-alpine3.19"
    echo ""
    read -p "是否继续构建？(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# 先拉取x86架构的基础镜像
echo "拉取x86架构的基础镜像..."
docker pull --platform linux/amd64 alpine:latest
docker pull --platform linux/amd64 amazoncorretto:17.0.11-alpine3.19

# 构建 shenyu-admin x86镜像
echo "构建 shenyu-admin x86镜像..."
docker build \
  --platform linux/amd64 \
  --tag shenyu-admin:latest-x86 \
  --file shenyu-dist/shenyu-admin-dist/docker/Dockerfile \
  --build-arg APP_NAME=apache-shenyu-2.7.0.2-SNAPSHOT-admin-bin \
  shenyu-dist/shenyu-admin-dist

# 构建 shenyu-bootstrap x86镜像
echo "构建 shenyu-bootstrap x86镜像..."
docker build \
  --platform linux/amd64 \
  --tag shenyu-bootstrap:latest-x86 \
  --file shenyu-dist/shenyu-bootstrap-dist/docker/Dockerfile \
  --build-arg APP_NAME=apache-shenyu-2.7.0.2-SNAPSHOT-bootstrap-bin \
  shenyu-dist/shenyu-bootstrap-dist

echo "x86架构镜像构建完成！"
echo "镜像标签:"
echo "  shenyu-admin:latest-x86 (x86架构)"
echo "  shenyu-bootstrap:latest-x86 (x86架构)"
echo ""
echo "这些镜像可以在x86 Linux服务器上运行"
echo "注意：在Mac上构建x86镜像会使用模拟，构建速度较慢"
