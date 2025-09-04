#!/bin/bash

# 创建完整的部署包
# 包含所有必要的镜像、脚本和配置文件

set -e

echo "开始创建ShenYu部署包..."

# 创建部署包目录
PACKAGE_DIR="shenyu-deployment-package-$(date +%Y%m%d)"
mkdir -p $PACKAGE_DIR

echo "创建部署包目录: $PACKAGE_DIR"

# 复制脚本文件
cp build-x86.sh $PACKAGE_DIR/
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
if [ -d "offline-images" ]; then
    echo "复制离线镜像目录..."
    cp -r offline-images $PACKAGE_DIR/
else
    echo "警告: offline-images 目录不存在，请先运行 ./save-images.sh"
    exit 1
fi

# 创建README文件
cat > $PACKAGE_DIR/README.md << 'EOF'
# ShenYu 离线部署包

## 文件说明
- `build-x86.sh` - x86架构构建脚本（在本地运行）
- `save-images.sh` - 镜像保存脚本（在本地运行）
- `deploy-server.sh` - 服务器部署脚本（在目标服务器运行）
- `offline-images/` - 离线Docker镜像文件
- `docker-compose-monitoring.yml` - Prometheus & Grafana 编排文件（可选）
- `start-monitoring.sh` / `stop-monitoring.sh` - 一键启动/停止监控（可选）
- `monitoring/` - Grafana 面板与 Prometheus 配置（可选）

## 部署步骤

### 1. 本地构建镜像（可选）
```bash
# 构建x86架构镜像（推荐用于Linux服务器）
./build-x86.sh
```

### 2. 保存镜像到离线包
```bash
./save-images.sh
```

### 3. 将整个目录传输到目标服务器

### 4. 在目标服务器上运行部署脚本
```bash
cd shenyu-deployment-package-YYYYMMDD
./deploy-server.sh
```

### 5. 修改配置文件

### 6. 启动服务
```bash
cd /opt/shenyu
./start-admin.sh
./start-bootstrap.sh

# （可选）启动监控
cd /opt/shenyu
./start-monitoring.sh
```

## 注意事项

- 确保目标服务器已安装Docker（以及 docker compose 插件）
- 确保目标服务器上的MySQL服务正常运行
- 配置文件中的占位符需要替换为实际值
- 服务启动后可以通过配置文件挂载进行配置修改
- 本包包含x86架构的镜像，适用于Linux服务器
EOF

# 创建部署包压缩文件
tar -czf "${PACKAGE_DIR}.tar.gz" $PACKAGE_DIR

echo "部署包创建完成！"
echo "部署包文件: ${PACKAGE_DIR}.tar.gz"
echo "部署包大小: $(du -h "${PACKAGE_DIR}.tar.gz" | cut -f1)"
echo ""
echo "下一步操作："
echo "1. 将 ${PACKAGE_DIR}.tar.gz 传输到目标服务器"
echo "2. 在目标服务器上解压并运行部署脚本"
