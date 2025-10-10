#!/bin/bash

# 创建完整的部署包
# 支持 x86 和 ARM 架构
# 用法: ./create-deployment-package.sh [x86|arm|all]

set -e

ARCH=${1:-all}  # 默认打包所有架构

echo "开始创建 ShenYu 部署包（架构: $ARCH）..."

# 创建部署包目录
TIMESTAMP=$(date +%Y%m%d)
if [ "$ARCH" = "all" ]; then
    PACKAGE_DIR="shenyu-deployment-package-${TIMESTAMP}"
else
    PACKAGE_DIR="shenyu-deployment-package-${ARCH}-${TIMESTAMP}"
fi

mkdir -p $PACKAGE_DIR

echo "创建部署包目录: $PACKAGE_DIR"

# 复制构建和保存脚本
echo "复制构建脚本..."
cp build-x86.sh $PACKAGE_DIR/ 2>/dev/null || true
cp build-arm.sh $PACKAGE_DIR/ 2>/dev/null || true
cp save-images.sh $PACKAGE_DIR/
cp deploy-server.sh $PACKAGE_DIR/

# 复制监控相关（Prometheus & Grafana）
if [ -f "docker-compose-monitoring.yml" ]; then
    cp docker-compose-monitoring.yml $PACKAGE_DIR/
fi
if [ -f "start-monitoring.sh" ]; then
    cp start-monitoring.sh $PACKAGE_DIR/
fi
if [ -f "stop-monitoring.sh" ]; then
    cp stop-monitoring.sh $PACKAGE_DIR/
fi
if [ -d "monitoring" ]; then
    cp -r monitoring $PACKAGE_DIR/
fi

# 复制离线镜像目录
echo "复制离线镜像..."

if [ "$ARCH" = "all" ]; then
    # 打包所有架构
    if [ -d "offline-images/x86" ] || [ -d "offline-images/arm" ]; then
        mkdir -p $PACKAGE_DIR/offline-images
        
        if [ -d "offline-images/x86" ]; then
            echo "  - 复制 x86 镜像..."
            cp -r offline-images/x86 $PACKAGE_DIR/offline-images/
        else
            echo "  ⚠ x86 镜像不存在，跳过"
        fi
        
        if [ -d "offline-images/arm" ]; then
            echo "  - 复制 ARM 镜像..."
            cp -r offline-images/arm $PACKAGE_DIR/offline-images/
        else
            echo "  ⚠ ARM 镜像不存在，跳过"
        fi
    else
        echo "❌ 错误: offline-images/ 目录下没有 x86 或 arm 子目录"
        echo "请先运行: ./save-images.sh x86 或 ./save-images.sh arm"
        exit 1
    fi
elif [ "$ARCH" = "x86" ]; then
    # 只打包 x86
    if [ -d "offline-images/x86" ]; then
        mkdir -p $PACKAGE_DIR/offline-images
        echo "  - 复制 x86 镜像..."
        cp -r offline-images/x86 $PACKAGE_DIR/offline-images/
    else
        echo "❌ 错误: offline-images/x86 目录不存在"
        echo "请先运行: ./save-images.sh x86"
        exit 1
    fi
elif [ "$ARCH" = "arm" ]; then
    # 只打包 ARM
    if [ -d "offline-images/arm" ]; then
        mkdir -p $PACKAGE_DIR/offline-images
        echo "  - 复制 ARM 镜像..."
        cp -r offline-images/arm $PACKAGE_DIR/offline-images/
    else
        echo "❌ 错误: offline-images/arm 目录不存在"
        echo "请先运行: ./save-images.sh arm"
        exit 1
    fi
else
    echo "❌ 错误: 不支持的架构 '$ARCH'"
    echo "用法: ./create-deployment-package.sh [x86|arm|all]"
    exit 1
fi

# 创建 README 文件
ARCH_INFO=""
if [ "$ARCH" = "all" ]; then
    ARCH_INFO="x86 和 ARM 架构"
elif [ "$ARCH" = "x86" ]; then
    ARCH_INFO="x86_64 (amd64) 架构"
elif [ "$ARCH" = "arm" ]; then
    ARCH_INFO="ARM64 (aarch64) 架构"
fi

cat > $PACKAGE_DIR/README.md << EOF
# ShenYu 离线部署包

## 包含架构
本部署包包含 **${ARCH_INFO}** 的镜像。

## 文件说明
- \`build-x86.sh\` - x86 架构构建脚本（在本地运行）
- \`build-arm.sh\` - ARM 架构构建脚本（在本地运行）
- \`save-images.sh\` - 镜像保存脚本（在本地运行）
- \`deploy-server.sh\` - 服务器部署脚本（自动检测架构）
- \`offline-images/\` - 离线 Docker 镜像文件
  - \`offline-images/x86/\` - x86_64 架构镜像
  - \`offline-images/arm/\` - ARM64 架构镜像
- \`docker-compose-monitoring.yml\` - Prometheus & Grafana 编排文件（可选）
- \`start-monitoring.sh\` / \`stop-monitoring.sh\` - 一键启动/停止监控（可选）
- \`monitoring/\` - Grafana 面板与 Prometheus 配置（可选）

## 部署步骤

### 1. 传输到目标服务器
\`\`\`bash
scp ${PACKAGE_DIR}.tar.gz user@server:/tmp/
\`\`\`

### 2. 在目标服务器上解压
\`\`\`bash
cd /tmp
tar -xzf ${PACKAGE_DIR}.tar.gz
cd ${PACKAGE_DIR}
\`\`\`

### 3. 运行部署脚本（自动检测服务器架构）
\`\`\`bash
./deploy-server.sh
\`\`\`

脚本会自动：
- 检测服务器架构（x86_64 或 aarch64）
- 加载对应架构的镜像
- 创建配置文件模板
- 生成启动脚本

### 4. 修改配置文件
\`\`\`bash
vi /opt/shenyu/configs/admin/application-mysql.yml   # 修改 MySQL 连接
vi /opt/shenyu/configs/admin/application.yml         # 修改 Admin 配置
vi /opt/shenyu/configs/bootstrap/application.yml     # 修改 Bootstrap 配置
\`\`\`

### 5. 启动服务
\`\`\`bash
cd /opt/shenyu
./start-admin.sh        # 启动 Admin
./start-bootstrap.sh    # 启动 Bootstrap

# （可选）启动监控
./start-monitoring.sh
\`\`\`

### 6. 验证部署
\`\`\`bash
# 检查容器状态
docker ps | grep shenyu

# 检查日志
docker logs shenyu-admin
docker logs shenyu-bootstrap

# 访问测试
curl http://localhost:9095/actuator/health
curl http://localhost:9195/actuator/health
\`\`\`

## 架构说明

### x86_64 (amd64)
- 适用于: Intel/AMD x86_64 处理器
- 服务器: 大多数云服务器、物理服务器
- 验证: \`uname -m\` 输出 \`x86_64\`

### ARM64 (aarch64)
- 适用于: ARM64 处理器
- 服务器: AWS Graviton、华为鲲鹏、树莓派 4/5
- 验证: \`uname -m\` 输出 \`aarch64\`

## 注意事项

- ✅ 部署脚本会自动检测服务器架构并加载对应镜像
- ⚠️ 确保目标服务器已安装 Docker（20.10+）
- ⚠️ 确保 MySQL 服务正常运行
- ⚠️ x86 镜像无法在 ARM 服务器运行，反之亦然
- ⚠️ 配置文件中的占位符需要替换为实际值

## 故障排除

### 检查服务器架构
\`\`\`bash
uname -m
# x86_64 = 使用 x86 镜像
# aarch64 = 使用 ARM 镜像
\`\`\`

### 检查镜像架构
\`\`\`bash
docker inspect shenyu-admin:latest --format='{{.Architecture}}'
# 应该与服务器架构匹配
\`\`\`

### 手动加载镜像
\`\`\`bash
# 如果自动部署失败，可以手动加载
cd offline-images/x86    # 或 offline-images/arm
./load-images.sh
\`\`\`

## 支持

- 文档: DOCKER-DEPLOYMENT-GUIDE.md
- 创建时间: ${TIMESTAMP}
- 包含架构: ${ARCH_INFO}
EOF

# 创建部署包压缩文件
echo ""
echo "创建压缩包..."
tar -czf "${PACKAGE_DIR}.tar.gz" $PACKAGE_DIR

PACKAGE_SIZE=$(du -h "${PACKAGE_DIR}.tar.gz" | cut -f1)

echo ""
echo "================================================"
echo "✅ 部署包创建完成！"
echo "================================================"
echo ""
echo "部署包文件: ${PACKAGE_DIR}.tar.gz"
echo "部署包大小: ${PACKAGE_SIZE}"
echo "包含架构: ${ARCH_INFO}"
echo ""
echo "包含的镜像目录:"
if [ -d "$PACKAGE_DIR/offline-images/x86" ]; then
    echo "  ✓ offline-images/x86/ (x86_64 架构)"
fi
if [ -d "$PACKAGE_DIR/offline-images/arm" ]; then
    echo "  ✓ offline-images/arm/ (ARM64 架构)"
fi
echo ""
echo "下一步操作："
echo "1. 传输到目标服务器:"
echo "   scp ${PACKAGE_DIR}.tar.gz user@server:/tmp/"
echo ""
echo "2. 在服务器上部署:"
echo "   tar -xzf ${PACKAGE_DIR}.tar.gz"
echo "   cd ${PACKAGE_DIR}"
echo "   ./deploy-server.sh"
