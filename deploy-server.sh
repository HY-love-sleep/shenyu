#!/bin/bash

# 服务器部署脚本
# 在目标服务器上运行

set -e

echo "开始部署ShenYu服务到服务器..."

# 创建部署目录
DEPLOY_DIR="/opt/shenyu"
mkdir -p $DEPLOY_DIR/{admin,bootstrap,configs,logs}

echo "创建部署目录: $DEPLOY_DIR"

# 加载Docker镜像
echo "加载Docker镜像..."
docker load -i offline-images/shenyu-admin.tar
docker load -i offline-images/shenyu-bootstrap.tar

# 重新标记镜像（将x86标签改为latest标签）
echo "重新标记镜像..."
docker tag shenyu-admin:latest-x86 shenyu-admin:latest
docker tag shenyu-bootstrap:latest-x86 shenyu-bootstrap:latest

# 创建配置文件目录
mkdir -p $DEPLOY_DIR/configs/admin
mkdir -p $DEPLOY_DIR/configs/bootstrap

# 复制配置文件模板
cat > $DEPLOY_DIR/configs/admin/application-mysql.yml << 'EOF'
shenyu:
  database:
    dialect: mysql
    init_enable: true

spring:
  datasource:
    url: jdbc:mysql://YOUR_MYSQL_HOST:3306/shenyu?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true
    username: YOUR_MYSQL_USER
    password: YOUR_MYSQL_PASSWORD
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      connection-timeout: 30000
      minimum-idle: 5
      maximum-pool-size: 20
      auto-commit: true
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-test-query: SELECT 1
      connection-init-sql: SET NAMES utf8mb4
      validation-timeout: 800
EOF

cat > $DEPLOY_DIR/configs/admin/application.yml << 'EOF'
server:
  port: 9095
  address: 0.0.0.0

spring:
  application:
    name: shenyu-admin
  profiles:
    active: mysql

shenyu:
  sync:
    websocket:
      enabled: true
      messageMaxSize: 10240
      allowOrigins: ws://YOUR_SERVER_IP:9095;ws://YOUR_SERVER_IP:9195;
EOF

cat > $DEPLOY_DIR/configs/bootstrap/application.yml << 'EOF'
server:
  port: 9195
  address: 0.0.0.0

spring:
  application:
    name: shenyu-bootstrap
  profiles:
    active: local

shenyu:
  sync:
    websocket:
      urls: ws://YOUR_SERVER_IP:9095/websocket
EOF

# 创建启动脚本
cat > $DEPLOY_DIR/start-admin.sh << 'EOF'
#!/bin/bash
docker run -d \
  --name shenyu-admin \
  --restart unless-stopped \
  -p 9095:9095 \
  -v /opt/shenyu/configs/admin:/opt/shenyu-admin/conf \
  -v /opt/shenyu/logs/admin:/opt/shenyu-admin/logs \
  -e SPRING_PROFILES_ACTIVE=mysql \
  shenyu-admin:latest
EOF

cat > $DEPLOY_DIR/start-bootstrap.sh << 'EOF'
#!/bin/bash
docker run -d \
  --name shenyu-bootstrap \
  --restart unless-stopped \
  -p 9195:9195 \
  -v /opt/shenyu/configs/bootstrap:/opt/shenyu-bootstrap/conf \
  -v /opt/shenyu/logs/bootstrap:/opt/shenyu-bootstrap/logs \
  shenyu-bootstrap:latest
EOF

cat > $DEPLOY_DIR/stop-all.sh << 'EOF'
#!/bin/bash
docker stop shenyu-admin shenyu-bootstrap || true
docker rm shenyu-admin shenyu-bootstrap || true
EOF

cat > $DEPLOY_DIR/restart-all.sh << 'EOF'
#!/bin/bash
./stop-all.sh
sleep 2
./start-admin.sh
sleep 5
./start-bootstrap.sh
EOF

# 设置执行权限
chmod +x $DEPLOY_DIR/*.sh

echo "部署完成！"
echo ""
echo "请修改配置文件中的以下内容："
echo "1. 在 $DEPLOY_DIR/configs/admin/application-mysql.yml 中设置MySQL连接信息"
echo "2. 在 $DEPLOY_DIR/configs/admin/application.yml 中设置服务器IP"
echo "3. 在 $DEPLOY_DIR/configs/bootstrap/application.yml 中设置服务器IP"
echo ""
echo "启动命令："
echo "  cd $DEPLOY_DIR"
echo "  ./start-admin.sh    # 启动admin服务"
echo "  ./start-bootstrap.sh # 启动bootstrap服务"
echo "  ./restart-all.sh    # 重启所有服务"
echo "  ./stop-all.sh       # 停止所有服务"
