#!/bin/bash

# 离线镜像保存脚本（支持多架构）
# 用法: ./save-images.sh [x86|arm]

set -e

ARCH=${1:-x86}  # 默认 x86，可传入 arm

echo "保存 $ARCH 架构的 Docker 镜像到 tar 文件..."

# 统一的输出目录结构
OUTPUT_DIR="offline-images/${ARCH}"
mkdir -p $OUTPUT_DIR

echo "输出目录: $OUTPUT_DIR"

# 根据架构验证和保存镜像
if [ "$ARCH" = "arm" ]; then
    echo ""
    echo "验证 ARM64 镜像是否存在..."
    
    # 检查镜像是否存在
    if ! docker images alpine:latest | grep -q "alpine"; then
        echo "❌ 错误: alpine:latest 镜像不存在"
        echo "请先运行: docker pull --platform linux/arm64 alpine:latest"
        exit 1
    fi
    
    if ! docker images amazoncorretto:17-alpine | grep -q "17-alpine"; then
        echo "❌ 错误: amazoncorretto:17-alpine 镜像不存在"
        echo "请先运行: docker pull --platform linux/arm64 amazoncorretto:17-alpine"
        exit 1
    fi
    
    if ! docker images shenyu-admin:latest-arm | grep -q "latest-arm"; then
        echo "❌ 错误: shenyu-admin:latest-arm 镜像不存在"
        echo "请先运行: ./build-arm.sh"
        exit 1
    fi
    
    if ! docker images shenyu-bootstrap:latest-arm | grep -q "latest-arm"; then
        echo "❌ 错误: shenyu-bootstrap:latest-arm 镜像不存在"
        echo "请先运行: ./build-arm.sh"
        exit 1
    fi
    
    # 保存基础镜像（使用 container 方式避免多架构 manifest bug）
    echo ""
    echo "保存 ARM64 基础镜像..."
    
    # 方法：创建临时容器，commit 为新镜像，然后保存
    # 这样可以避免 Docker Desktop 的多架构 manifest bug
    
    echo "处理 alpine..."
    ALPINE_CONTAINER=$(docker create alpine:latest)
    docker commit $ALPINE_CONTAINER alpine:arm-save
    docker rm $ALPINE_CONTAINER >/dev/null 2>&1
    docker save -o $OUTPUT_DIR/alpine.tar alpine:arm-save
    docker rmi alpine:arm-save >/dev/null 2>&1
    echo "✓ 保存 alpine (arm64)"
    
    echo "处理 amazoncorretto..."
    JDK_CONTAINER=$(docker create amazoncorretto:17-alpine)
    docker commit $JDK_CONTAINER amazoncorretto:arm-save
    docker rm $JDK_CONTAINER >/dev/null 2>&1
    docker save -o $OUTPUT_DIR/amazoncorretto-17-alpine.tar amazoncorretto:arm-save
    docker rmi amazoncorretto:arm-save >/dev/null 2>&1
    echo "✓ 保存 amazoncorretto:17-alpine (arm64)"
    
    # 保存应用镜像
    echo ""
    echo "保存 ARM64 应用镜像..."
    docker save -o $OUTPUT_DIR/shenyu-admin-arm.tar shenyu-admin:latest-arm
    echo "✓ 保存 shenyu-admin:latest-arm"
    
    docker save -o $OUTPUT_DIR/shenyu-bootstrap-arm.tar shenyu-bootstrap:latest-arm
    echo "✓ 保存 shenyu-bootstrap:latest-arm"
    
elif [ "$ARCH" = "x86" ]; then
    echo ""
    echo "验证 x86_64 镜像是否存在..."
    
    # 检查镜像是否存在
    if ! docker images alpine:latest | grep -q "alpine"; then
        echo "❌ 错误: alpine:latest 镜像不存在"
        echo "请先运行: docker pull --platform linux/amd64 alpine:latest"
        exit 1
    fi
    
    if ! docker images amazoncorretto:17.0.11-alpine3.19 | grep -q "17.0.11"; then
        echo "❌ 错误: amazoncorretto:17.0.11-alpine3.19 镜像不存在"
        echo "请先运行: docker pull --platform linux/amd64 amazoncorretto:17.0.11-alpine3.19"
        exit 1
    fi
    
    if ! docker images shenyu-admin:latest-x86 | grep -q "latest-x86"; then
        echo "❌ 错误: shenyu-admin:latest-x86 镜像不存在"
        echo "请先运行: ./build-x86.sh"
        exit 1
    fi
    
    if ! docker images shenyu-bootstrap:latest-x86 | grep -q "latest-x86"; then
        echo "❌ 错误: shenyu-bootstrap:latest-x86 镜像不存在"
        echo "请先运行: ./build-x86.sh"
        exit 1
    fi
    
    # 保存基础镜像（使用 container 方式避免多架构 manifest bug）
    echo ""
    echo "保存 x86_64 基础镜像..."
    
    # 方法：创建临时容器，commit 为新镜像，然后保存
    # 这样可以避免 Docker Desktop 的多架构 manifest bug
    
    echo "处理 alpine..."
    ALPINE_CONTAINER=$(docker create alpine:latest)
    docker commit $ALPINE_CONTAINER alpine:x86-save
    docker rm $ALPINE_CONTAINER >/dev/null 2>&1
    docker save -o $OUTPUT_DIR/alpine.tar alpine:x86-save
    docker rmi alpine:x86-save >/dev/null 2>&1
    echo "✓ 保存 alpine (amd64)"
    
    echo "处理 amazoncorretto..."
    JDK_CONTAINER=$(docker create amazoncorretto:17.0.11-alpine3.19)
    docker commit $JDK_CONTAINER amazoncorretto:x86-save
    docker rm $JDK_CONTAINER >/dev/null 2>&1
    docker save -o $OUTPUT_DIR/amazoncorretto-17-alpine.tar amazoncorretto:x86-save
    docker rmi amazoncorretto:x86-save >/dev/null 2>&1
    echo "✓ 保存 amazoncorretto:17.0.11-alpine3.19 (amd64)"
    
    # 保存应用镜像
    echo ""
    echo "保存 x86_64 应用镜像..."
    docker save -o $OUTPUT_DIR/shenyu-admin-x86.tar shenyu-admin:latest-x86
    echo "✓ 保存 shenyu-admin:latest-x86"
    
    docker save -o $OUTPUT_DIR/shenyu-bootstrap-x86.tar shenyu-bootstrap:latest-x86
    echo "✓ 保存 shenyu-bootstrap:latest-x86"
    
else
    echo "❌ 错误: 不支持的架构 '$ARCH'"
    echo "用法: ./save-images.sh [x86|arm]"
    exit 1
fi

# 验证保存的镜像
echo ""
echo "验证保存的镜像..."
echo "文件列表:"
ls -lh $OUTPUT_DIR/*.tar

# 创建加载脚本
if [ "$ARCH" = "arm" ]; then
    cat > $OUTPUT_DIR/load-images.sh << 'EOF'
#!/bin/bash
# 在 ARM 架构的 Linux 服务器上加载镜像

set -e

echo "加载 ARM 架构的 Docker 镜像..."

docker load -i alpine.tar
docker load -i amazoncorretto-17-alpine.tar
docker load -i shenyu-admin-arm.tar
docker load -i shenyu-bootstrap-arm.tar

echo "重新标记镜像..."

# 应用镜像
docker tag shenyu-admin:latest-arm shenyu-admin:latest
docker tag shenyu-bootstrap:latest-arm shenyu-bootstrap:latest

# 基础镜像（从临时 tag 恢复到标准 tag）
docker tag alpine:arm-save alpine:latest
docker tag amazoncorretto:arm-save amazoncorretto:17-alpine

# 兼容性别名
docker tag amazoncorretto:17-alpine amazoncorretto:17.0.11-alpine3.19 2>/dev/null || true

echo ""
echo "✅ 镜像加载完成！"
echo ""
echo "验证镜像架构:"
docker image inspect shenyu-admin:latest --format='shenyu-admin: {{.Architecture}}'
docker image inspect shenyu-bootstrap:latest --format='shenyu-bootstrap: {{.Architecture}}'
docker image inspect alpine:latest --format='alpine: {{.Architecture}}'
docker image inspect amazoncorretto:17-alpine --format='amazoncorretto: {{.Architecture}}'

echo ""
echo "镜像列表:"
docker images | grep -E "shenyu|alpine|amazoncorretto"
EOF

elif [ "$ARCH" = "x86" ]; then
    cat > $OUTPUT_DIR/load-images.sh << 'EOF'
#!/bin/bash
# 在 x86 架构的 Linux 服务器上加载镜像

set -e

echo "加载 x86 架构的 Docker 镜像..."

docker load -i alpine.tar
docker load -i amazoncorretto-17-alpine.tar
docker load -i shenyu-admin-x86.tar
docker load -i shenyu-bootstrap-x86.tar

echo "重新标记镜像..."

# 应用镜像
docker tag shenyu-admin:latest-x86 shenyu-admin:latest
docker tag shenyu-bootstrap:latest-x86 shenyu-bootstrap:latest

# 基础镜像（从临时 tag 恢复到标准 tag）
docker tag alpine:x86-save alpine:latest
docker tag amazoncorretto:x86-save amazoncorretto:17.0.11-alpine3.19

# 兼容性别名
docker tag amazoncorretto:17.0.11-alpine3.19 amazoncorretto:17-alpine 2>/dev/null || true

echo ""
echo "✅ 镜像加载完成！"
echo ""
echo "验证镜像架构:"
docker image inspect shenyu-admin:latest --format='shenyu-admin: {{.Architecture}}'
docker image inspect shenyu-bootstrap:latest --format='shenyu-bootstrap: {{.Architecture}}'
docker image inspect alpine:latest --format='alpine: {{.Architecture}}'
docker image inspect amazoncorretto:17.0.11-alpine3.19 --format='amazoncorretto: {{.Architecture}}'

echo ""
echo "镜像列表:"
docker images | grep -E "shenyu|alpine|amazoncorretto"
EOF

else
    echo "❌ 错误: 不支持的架构 '$ARCH'"
    exit 1
fi

chmod +x $OUTPUT_DIR/load-images.sh

# 创建 README
JDK_VERSION=$([ "$ARCH" = "arm" ] && echo "17-alpine" || echo "17.0.11-alpine3.19")

cat > $OUTPUT_DIR/README.md << EOF
# Docker 镜像离线包 ($ARCH 架构)

## 镜像列表
- alpine:latest
- amazoncorretto:${JDK_VERSION}
- shenyu-admin:latest-${ARCH}
- shenyu-bootstrap:latest-${ARCH}

## 快速加载
\`\`\`bash
./load-images.sh
\`\`\`

## 手动加载
\`\`\`bash
docker load -i alpine.tar
docker load -i amazoncorretto-17-alpine.tar
docker load -i shenyu-admin-${ARCH}.tar
docker load -i shenyu-bootstrap-${ARCH}.tar

# 重新标记
docker tag shenyu-admin:latest-${ARCH} shenyu-admin:latest
docker tag shenyu-bootstrap:latest-${ARCH} shenyu-bootstrap:latest
\`\`\`

## 架构信息
- 目标架构: $ARCH
- 构建时间: $(date '+%Y-%m-%d %H:%M:%S')
- 适用系统: $([ "$ARCH" = "arm" ] && echo "ARM64 (aarch64) Linux" || echo "x86_64 (amd64) Linux")
- JDK 版本: Amazon Corretto ${JDK_VERSION}

## 注意事项
$([ "$ARCH" = "arm" ] && echo "- ⚠️ 本包仅适用于 ARM64 架构服务器" || echo "- ⚠️ 本包仅适用于 x86_64 架构服务器")
- 所有基础镜像均已包含，无需联网
- 完全离线部署
EOF

echo ""
echo "✅ 镜像保存完成！"
echo "输出目录: $OUTPUT_DIR/"
echo ""
echo "文件大小统计:"
du -sh $OUTPUT_DIR

echo ""
echo "================================================"
echo "✅ 部署包准备完成！"
echo "================================================"
echo ""
echo "镜像文件位置: $OUTPUT_DIR/"
ls -lh $OUTPUT_DIR/*.tar
echo ""
echo "下一步:"
echo "1. 可选：打包整个目录传输到服务器"
echo "   tar -czf offline-images-${ARCH}-$(date +%Y%m%d).tar.gz offline-images/${ARCH}"
echo ""
echo "2. 在目标服务器上加载镜像:"
echo "   cd $OUTPUT_DIR"
echo "   ./load-images.sh"
