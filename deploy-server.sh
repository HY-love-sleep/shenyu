#!/bin/bash

# 服务器部署脚本
# 在目标服务器上运行

set -e

echo "开始部署ShenYu服务到服务器..."

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

# 创建部署目录
DEPLOY_DIR="/opt/shenyu"
mkdir -p $DEPLOY_DIR/{admin,bootstrap,configs,logs}

echo "创建部署目录: $DEPLOY_DIR"

# 加载Docker镜像
echo "加载Docker镜像..."
docker load -i "$SCRIPT_DIR/offline-images/shenyu-admin-x86.tar"
docker load -i "$SCRIPT_DIR/offline-images/shenyu-bootstrap-x86.tar"

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
    url: jdbc:mysql://192.168.130.168:3306/shenyu?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true
    username: remote
    password: zUTk%@uvs5EFdDY@jSDd
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
  mail:
    host: smtp.qq.com
    username: shenyu@apache.com
    password: your-password
    port: 587
    properties:
      mail:
        smtp:
          socketFactoryClass: javax.net.ssl.SSLSocketFactory
          ssl:
            enable: true

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
  thymeleaf:
    cache: true
    encoding: utf-8
    enabled: true
    prefix: classpath:/static/
    suffix: .html
  mvc:
    pathmatch:
      matching-strategy: ant_path_matcher
  jackson:
    time-zone: GMT+8
  messages:
    basename: message/i18n

management:
  health:
    mail:
      enabled: off
  endpoints:
    web:
      exposure:
        include:
          - 'health'
          - 'prometheus'
          - 'metrics'
    enabled-by-default: true
  endpoint:
    metrics:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        "[security.api.calls.duration]": true
        "[watermark.api.calls.duration]": true
      percentiles:
        "[security.api.calls.duration]": 0.5,0.75,0.9,0.95,0.99
        "[watermark.api.calls.duration]": 0.5,0.75,0.9,0.95,0.99

mybatis:
  config-location: classpath:/mybatis/mybatis-config.xml
  mapper-locations: classpath:/mappers/*.xml
  type-handlers-package: org.apache.shenyu.admin.mybatis.handler

shenyu:
  register:
    registerType: http
    serverLists:
    props:
      sessionTimeout: 5000
      connectionTimeout: 2000
      checked: true
      zombieCheckThreads: 10
      zombieCheckTimes: 5
      scheduledTime: 10
      nacosNameSpace: ShenyuRegisterCenter
  sync:
    websocket:
      enabled: true
      messageMaxSize: 10240
      allowOrigins: ws://localhost:9195;ws://192.168.130.233:9195;
  ldap:
    enabled: false
  jwt:
    expired-seconds: 86400000
  cluster:
    enabled: false
  shiro:
    white-list:
      - /
      - /favicon.*
      - /static/**
      - /index**
      - /platform/login
      - /platform/secretInfo
      - /websocket
      - /error
      - /actuator/health
      - /actuator/health/**
      - /actuator/prometheus
      - /swagger-ui.html
      - /swagger-ui/**
      - /webjars/**
      - /v3/api-docs/**
      - /csrf
      - /alert/report
  dashboard:
    core:
      onlySuperAdminPermission:
        - system:manager:add
        - system:manager:edit
        - system:manager:delete
        - system:role:add
        - system:role:edit
        - system:role:delete
        - system:resource:addButton
        - system:resource:addMenu
        - system:resource:editButton
        - system:resource:editMenu
        - system:resource:deleteButton
        - system:resource:deleteMenu

logging:
  level:
    root: info
    org.springframework.boot: info
    org.apache.ibatis: info
    org.apache.shenyu: info

EOF

cat > $DEPLOY_DIR/configs/bootstrap/application.yml << 'EOF'
server:
  port: 9195
  address: 0.0.0.0
  compression:
    enabled: true
    minResponseSize: 1MB # If the response data is greater than 1MB, enable compression.

spring:
  main:
    allow-bean-definition-overriding: true
  application:
    name: shenyu-bootstrap
  codec:
    max-in-memory-size: 2MB
  cloud:
    discovery:
      enabled: false
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848 # Spring Cloud Alibaba Dubbo use this.
        enabled: false
        namespace: ShenyuRegisterCenter


eureka:
  client:
    enabled: false
    serviceUrl:
      defaultZone: http://192.168.130.233:8761/eureka/
  instance:
    prefer-ip-address: true

management:
  health:
    redis:
      enabled: false
    elasticsearch:
      enabled: false
  endpoint:
    health:
      enabled: true
      show-details: always
    metrics:
      enabled: true
    prometheus:
      enabled: true
  endpoints:
    web:
      exposure:
        include:
          - 'health'
          - 'info'
          - 'prometheus'
          - 'metrics'
  metrics:
    distribution:
      percentiles-histogram:
        "[security.api.calls.duration]": true
        "[watermark.api.calls.duration]": true
      percentiles:
        "[security.api.calls.duration]": 0.5,0.75,0.9,0.95,0.99
        "[watermark.api.calls.duration]": 0.5,0.75,0.9,0.95,0.99

shenyu:
  namespace: 649330b6-c2d7-4edc-be8e-8a54df9eb385
  selectorMatchCache:
    cache:
      enabled: false
      initialCapacity: 10000 # initial capacity in cache
      maximumSize: 10000 # max size in cache
    trie:
      enabled: false
      cacheSize: 128 # the number of plug-ins
      matchMode: antPathMatch
  ruleMatchCache:
    cache:
      enabled: false
      initialCapacity: 10000 # initial capacity in cache
      maximumSize: 65536 # max size in cache
    trie:
      enabled: false
      cacheSize: 1024 # the number of selectors
      matchMode: antPathMatch
  netty:
    http:
      webServerFactoryEnabled: true
      selectCount: 1
      workerCount: 8
      accessLog: false
      serverSocketChannel:
        soBackLog: 128
        soReuseAddr: true
        connectTimeoutMillis: 10000
        writeBufferHighWaterMark: 65536
        writeBufferLowWaterMark: 32768
        writeSpinCount: 16
        autoRead: false
        allocType: "unpooled"
        messageSizeEstimator: 8
        singleEventExecutorPerGroup: true
      socketChannel:
        soKeepAlive: false
        soReuseAddr: true
        soLinger: -1
        tcpNoDelay: true
        ipTos: 0
        allowHalfClosure: false
        connectTimeoutMillis: 10000
        writeBufferHighWaterMark: 65536
        writeBufferLowWaterMark: 32768
        writeSpinCount: 16
        autoRead: false
        allocType: "unpooled"
        messageSizeEstimator: 8
        singleEventExecutorPerGroup: true
      sni:
        enabled: false
        mod: k8s #manul
        defaultK8sSecretNamespace: shenyu-ingress
        defaultK8sSecretName: default-cert

  register:
    enabled: false
    registerType: zookeeper #etcd #consul
    serverLists: 192.168.130.233:2181 #http://localhost:2379 #localhost:8848
    props:
  cross:
    enabled: true
    allowedHeaders:
    allowedMethods: "*"
    allowedAnyOrigin: true # the same of Access-Control-Allow-Origin: "*"

    allowedExpose: ""
    maxAge: "18000"
    allowCredentials: true

  switchConfig:
    local: true
    collapseSlashes: false
  file:
    enabled: true
    maxSize : 10
  sync:
    websocket:
      urls: ws://localhost:9095/websocket
      allowOrigin: ws://192.168.130.233:9195

  exclude:
    enabled: false
    paths:
      - /favicon.ico
  fallback:
    enabled: false
    paths:
      - /fallback/hystrix
      - /fallback/resilience4j
      - /fallback/sentinel
  health:
    enabled: true
    paths:
      - /actuator
      - /health_check
  alert:
    enabled: false
    admins: 192.168.130.233:9095
  extPlugin:
    path:
    enabled: true
    threads: 1
    scheduleTime: 300
    scheduleDelay: 30
  scheduler:
    enabled: false
    type: fixed
    threads: 16
  upstreamCheck:
    enabled: false
    poolSize: 10
    timeout: 3000
    healthyThreshold: 1
    unhealthyThreshold: 1
    interval: 5000
    printEnabled: true
    printInterval: 60000
  springCloudCache:
    enabled: false
  ribbon:
    serverListRefreshInterval: 10000
  metrics:
    enabled: false
    name : prometheus
    host: 127.0.0.1
    port: 8090
    jmxConfig:
    props:
      jvm_enabled: true

  local:
    enabled: false
    sha512Key: "BA3253876AED6BC22D4A6FF53D8406C6AD864195ED144AB5C87621B6C233B548BAEAE6956DF346EC8C17F5EA10F35EE3CBC514797ED7DDD3145464E2A0BAB413"
  websocket:
    enableProxyPing: false


logging:
  level:
    root: info
    org.springframework.boot: info
    org.apache.ibatis: info
    org.apache.shenyu.bonuspoint: info
    org.apache.shenyu.lottery: info
    org.apache.shenyu: info
    org.springframework.http.server.reactive: info
    org.springframework.web.reactive: info
    reactor.ipc.netty: info
    reactor.netty: info
    org.apache.shenyu.plugin.api.ShenyuPlugin: info

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

# 复制监控编排与面板（可选）
if [ -f "$SCRIPT_DIR/docker-compose-monitoring.yml" ]; then
  cp "$SCRIPT_DIR/docker-compose-monitoring.yml" $DEPLOY_DIR/
fi
if [ -f "$SCRIPT_DIR/start-monitoring.sh" ]; then
  cp "$SCRIPT_DIR/start-monitoring.sh" $DEPLOY_DIR/
fi
if [ -f "$SCRIPT_DIR/stop-monitoring.sh" ]; then
  cp "$SCRIPT_DIR/stop-monitoring.sh" $DEPLOY_DIR/
fi
if [ -d "$SCRIPT_DIR/monitoring" ]; then
  cp -r "$SCRIPT_DIR/monitoring" $DEPLOY_DIR/
fi

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
