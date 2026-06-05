# 运维手册 — 信贷风控决策引擎平台

## 1. 日常巡检清单

| 检查项 | 命令 | 频率 |
|--------|------|------|
| 服务健康 | `curl http://localhost:8080/health` | 每5分钟 |
| JVM内存 | `kubectl top pods -n finance-platform` | 每10分钟 |
| 决策QPS | 检查 Grafana 仪表盘 | 实时 |
| 规则缓存命中率 | `GET /actuator/metrics/cache.hits` | 每小时 |
| 模型推理延迟 | 检查 P99 指标 | 实时 |

## 2. 服务启停

```bash
# Docker Compose
docker-compose up -d          # 启动全部
docker-compose restart decision-server  # 重启单个服务
docker-compose down           # 停止全部

# Kubernetes
kubectl rollout restart deployment/decision-server -n finance-platform
kubectl scale deployment/decision-server --replicas=3 -n finance-platform
```

## 3. 故障排查

### 3.1 决策超时 (>3s)
- 检查线程池: `decision.engine.thread-pool.dag-pool-size` (默认8)
- 检查外部API超时: `decision.engine.external-api.timeout-ms` (默认3000)
- 检查Redis连接: `redis-cli ping`

### 3.2 规则缓存不一致
- 触发热加载: 发布新版本后自动推送
- 手动清除: `curl -X POST /api/v1/admin/cache/reload`

### 3.3 模型推理失败
- 检查模型平台健康: `curl http://localhost:8082/health`
- 检查模型文件: `ls storage/models/`
- 查看监控: `GET /api/v1/monitoring/dashboard/{model_id}`

## 4. 备份恢复

| 组件 | 备份策略 | 恢复命令 |
|------|---------|---------|
| MySQL | `mysqldump --all-databases > backup.sql` | `mysql < backup.sql` |
| Redis | RDB快照 (自动) | `redis-cli shutdown` (重启自动加载) |
| ES | 快照API | `_snapshot/backup/_restore` |
| 配置 | Git版本控制 | `git checkout` |

## 5. 扩缩容

### JVM 参数
```yaml
# decision-server (高吞吐)
JAVA_OPTS: "-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# model-platform (ML推理)
resources:
  limits: { memory: 4Gi, cpu: "2" }
```

### K8s HPA
```bash
kubectl autoscale deployment decision-server --min=2 --max=10 --cpu-percent=70 -n finance-platform
```

## 6. 监控告警

| 指标 | 阈值 | 级别 |
|------|------|------|
| 决策P99延迟 | >3s | WARNING |
| 决策QPS | <100 | WARNING |
| 服务不可用 | 连续3次健康检查失败 | CRITICAL |
| 模型PSI | >0.25 | CRITICAL |
| JVM堆使用率 | >85% | WARNING |
