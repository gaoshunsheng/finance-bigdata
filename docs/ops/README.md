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

---

## 大数据集群运维

### Hadoop HDFS 运维

#### NameNode 安全模式

```bash
# 查看安全模式状态
hdfs dfsadmin -safemode get

# 离开安全模式 (启动后自动退出，如未退出则手动执行)
hdfs dfsadmin -safemode leave

# 强制进入安全模式 (维护时使用)
hdfs dfsadmin -safemode enter
```

安全模式通常在启动后自动退出。如持续处于安全模式，通常是因为可用 DataNode 数量未达到阈值或 Block 副本不足。

#### 磁盘使用率

```bash
# 查看整体磁盘使用
hdfs dfs -df -h /

# 查看各目录大小
hdfs du -h /data/ods
hdfs du -h /data/dwd
hdfs du -h /data/dws
hdfs du -h /data/ads

# 查看 DataNode 存储报告
hdfs dfsadmin -report
```

磁盘使用率超过 85% 时需清理数据或扩容。

#### 数据均衡

```bash
# 执行数据均衡 (阈值 10%)
hdfs balancer -threshold 10

# 带宽限制 (避免影响在线业务)
hdfs dfsadmin -setBalancerBandwidth 104857600  # 100MB/s

# 查看均衡进度
hdfs balancer -Ddfs.balancer.max-size-to-move=53687091200
```

建议在业务低峰期执行均衡操作。

---

### Kafka 运维

#### Topic 管理

```bash
# 创建 Topic
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic <topic_name> --partitions 3 --replication-factor 1

# 查看 Topic 列表
kafka-topics --bootstrap-server localhost:9092 --list

# 查看 Topic 详情
kafka-topics --bootstrap-server localhost:9092 --describe --topic <topic_name>

# 修改分区数 (只能增加，不能减少)
kafka-topics --bootstrap-server localhost:9092 \
  --alter --topic <topic_name> --partitions 6

# 删除 Topic (需确保 delete.topic.enable=true)
kafka-topics --bootstrap-server localhost:9092 --delete --topic <topic_name>
```

#### 消费者组管理

```bash
# 查看所有消费者组
kafka-consumer-groups --bootstrap-server localhost:9092 --list

# 查看消费者组详情 (含 Lag)
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group <group_id>

# 重置消费者 Offset (慎用)
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group <group_id> --topic <topic_name> --reset-offsets --to-earliest --execute
```

#### 性能监控

| 监控项 | 命令/方法 | 告警阈值 |
|--------|----------|---------|
| Consumer Lag | `kafka-consumer-groups --describe` | Lag > 10000 |
| 生产吞吐量 | Kafka JMX 指标 | < 预期 QPS 的 50% |
| 消费吞吐量 | Kafka JMX 指标 | < 生产速率 |
| 磁盘使用率 | `du -sh /kafka-logs` | > 80% |
| ISR 缩减 | `kafka-topics --describe` | ISR < 副本数 |

**Lag 监控脚本：**

```bash
#!/bin/bash
GROUP=$1
LAG=$(kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group ${GROUP} 2>/dev/null | \
  awk 'NR>1 {sum+=$5} END {print sum+0}')
echo "Consumer Group: ${GROUP}, Total Lag: ${LAG}"
```

---

### Flink 运维

#### 作业管理

```bash
# 查看运行中的作业
flink list -r

# 查看所有作业 (含已完成)
flink list -a

# 取消作业
flink cancel <job_id>

# 带 Savepoint 取消
flink stop --savepointPath /savepoints <job_id>

# 从 Savepoint 恢复启动
flink run -s /savepoints/savepoint-<id> -d <jar_path>
```

#### Checkpoint 监控

通过 Flink Web UI (`http://localhost:8081`) 查看：

- Checkpoint 间隔和耗时
- Checkpoint 大小趋势
- 失败率

```bash
# 通过 API 获取 Checkpoint 统计
curl -sf http://localhost:8081/jobs/<job_id>/checkpoints | python3 -m json.tool
```

**Checkpoint 异常排查：**

| 现象 | 可能原因 | 处理方式 |
|------|---------|---------|
| Checkpoint 超时 | Barrier 无法对齐 | 检查是否有反压 |
| Checkpoint 失败率高 | HDFS/HBase 写入慢 | 检查存储 IO |
| Checkpoint 过大 | 状态膨胀 | 检查窗口状态、设置 TTL |

#### Backpressure 排查

1. 打开 Flink Web UI -> 选择作业 -> Overview
2. 查看各算子的 Backpressure 状态 (OK / LOW / HIGH)
3. HIGH 表示该算子处理速度跟不上上游

**常见原因及处理：**

| 原因 | 处理 |
|------|------|
| 外部系统慢 (HBase/Redis) | 增加超时、批量写入、增加并行度 |
| 数据倾斜 | 重新设置 KeyBy 分区键、预聚合 |
| GC 停顿 | 调优 JVM 参数、减少对象创建 |
| 算子逻辑复杂 | 拆分算子、增加并行度 |

---

### HBase 运维

#### Region 管理

```bash
# 查看 Region 分布
hbase balancer status

# 手动 Split Region
hbase shell <<EOF
split 'customer_feature,<rowkey_prefix>,<encoded_region_name>'
EOF

# 手动 Merge Region
hbase shell <<EOF
merge_region '<encoded_region_name_1>', '<encoded_region_name_2>'
EOF

# 触发均衡
hbase shell <<EOF
balance_switch true
balancer
EOF
```

#### Compaction

```bash
# 触发 Major Compaction (会阻塞写入，低峰执行)
hbase shell <<EOF
major_compact 'customer_feature'
EOF

# 触发 Minor Compaction
hbase shell <<EOF
compact 'customer_feature'
EOF

# 查看Compaction队列
hbase shell <<EOF
status 'detailed'
EOF
```

| 类型 | 说明 | 触发方式 |
|------|------|---------|
| Minor Compaction | 合并小文件，不删除过期数据 | 自动 (MemStore Flush) |
| Major Compaction | 合并所有文件，清理过期/删除数据 | 手动或定期 (默认 7 天) |

#### RowKey 设计原则

| 原则 | 说明 | 示例 |
|------|------|------|
| 散列前缀 | 避免顺序写入热点 | `reversed(customerId)` |
| 预分区 | 建表时按前缀预分配 Region | `SPLITS => ['1','2',...,'F']` |
| 长度适中 | RowKey 过长影响索引性能 | 建议不超过 100 字节 |
| 唯一性 | RowKey 全局唯一 | `customerId_featureType_timestamp` |

---

### Hive 运维

#### 分区管理

```bash
# 添加分区
ALTER TABLE ods_loan_application ADD IF NOT EXISTS PARTITION (dt='2026-06-06');

# 删除过期分区
ALTER TABLE ods_loan_application DROP IF EXISTS PARTITION (dt='2026-05-06');

# 修复分区 (HDFS 已有目录但 Metastore 未注册)
MSCK REPAIR TABLE ods_loan_application;

# 查看分区列表
SHOW PARTITIONS ods_loan_application;
```

#### Metastore 备份

```bash
# 备份 Metastore 数据库 (MySQL)
mysqldump -h <metastore_mysql_host> -u hive -p hive_metastore \
  > metastore_backup_$(date +%Y%m%d).sql

# 恢复
mysql -h <metastore_mysql_host> -u hive -p hive_metastore \
  < metastore_backup_20260606.sql
```

建议每日自动备份，保留最近 7 天。

#### 查询性能调优

| 参数 | 推荐值 | 说明 |
|------|-------|------|
| `hive.exec.parallel` | true | 开启作业并行执行 |
| `hive.exec.parallel.thread.number` | 8 | 并行线程数 |
| `mapred.reduce.tasks` | -1 | 自动推断 Reduce 数 |
| `hive.groupby.skewindata` | true | 处理 Group By 数据倾斜 |
| `hive.merge.mapfiles` | true | 合并小文件 |
| `hive.exec.dynamic.partition` | true | 开启动态分区 |

```sql
-- 查询前设置调优参数
SET hive.exec.parallel=true;
SET hive.exec.parallel.thread.number=8;
SET hive.groupby.skewindata=true;
```

---

### 日常巡检清单

| 序号 | 检查项 | 命令/方法 | 频率 | 正常标准 |
|------|--------|----------|------|---------|
| 1 | HDFS 健康状态 | `hdfs dfsadmin -report` | 每日 | 所有 DataNode 在线 |
| 2 | HDFS 磁盘使用率 | `hdfs dfs -df -h /` | 每日 | < 80% |
| 3 | HDFS 块健康 | `hdfs fsck / -list-corruptfileblocks` | 每日 | 0 个损坏块 |
| 4 | Kafka Lag | `kafka-consumer-groups --describe` | 每日 | Lag < 10000 |
| 5 | Kafka 磁盘使用 | `du -sh /kafka-logs` | 每日 | < 80% |
| 6 | Flink 作业状态 | Flink Web UI / API | 每日 | 所有作业 RUNNING |
| 7 | Flink Checkpoint | Flink Web UI | 每日 | 成功率 > 99% |
| 8 | HBase Region 均衡 | `hbase balancer status` | 每日 | 无未均衡 Region |
| 9 | HBase Compaction 队列 | HBase Master UI | 每日 | 队列深度 < 10 |
| 10 | Hive Metastore 可用 | `beeline -e "SHOW DATABASES;"` | 每日 | 返回正常 |
| 11 | ES 集群健康 | `curl localhost:9200/_cluster/health` | 每日 | status=green |
| 12 | MySQL 慢查询 | 慢查询日志 | 每日 | 无异常增长 |
| 13 | data-service 健康 | `curl localhost:8083/health` | 每 5 分钟 | status=UP |
| 14 | DolphinScheduler 调度 | DS Web UI | 每日 | 任务无失败 |
| 15 | 容器资源 | `docker stats` | 每日 | CPU < 70%, MEM < 80% |

---

### 故障排查

#### NameNode 宕机

**现象：** HDFS 不可用，所有读写操作失败。

**排查步骤：**

1. 检查 NameNode 进程：`jps | grep NameNode`
2. 查看 NameNode 日志：`tail -200f $HADOOP_LOG_DIR/hadoop-hdfs-namenode-*.log`
3. 常见原因：
   - 磁盘满：`df -h`，清理 EditLog 或 FsImage
   - JVM OOM：调大 `-Xmx`
   - 元数据损坏：从 SecondaryNameNode / CheckpointNode 恢复

**恢复方案：**

```bash
# 如果有 HA，自动切换 Standby
# 单节点需手动重启
hdfs --daemon start namenode

# 元数据损坏时从 Checkpoint 恢复
# 1. 停止 NameNode
# 2. 拷贝 SecondaryNameNode 的 fsimage
# 3. hdfs namenode -importCheckpoint
# 4. 重启 NameNode
```

#### Kafka 消息积压

**现象：** Consumer Lag 持续增长，数据处理延迟增大。

**排查步骤：**

1. 确认 Lag 大小：`kafka-consumer-groups --describe --group <group>`
2. 确认生产和消费速率：Kafka JMX 指标
3. 常见原因及处理：

| 原因 | 排查 | 处理 |
|------|------|------|
| 消费者处理慢 | 查看 GC、CPU | 优化消费逻辑、增加消费者数 |
| 数据倾斜 | 查看各 Partition Lag | 调整分区策略、增加分区数 |
| 消费者频繁 Rebalance | 查看消费者日志 | 增大 `max.poll.interval.ms` |
| 外部系统瓶颈 | 查看 HBase/Redis 延迟 | 优化 Sink、增加并行度 |

**临时处理：**

```bash
# 扩容分区
kafka-topics --alter --topic <topic> --partitions 6 --bootstrap-server localhost:9092

# 增加 Flink 并行度 (需重启作业)
flink run -p 6 -d <jar>
```

#### Flink Checkpoint 失败

**现象：** Checkpoint 持续失败，最终导致作业失败。

**排查步骤：**

1. Flink Web UI -> Checkpoints 页面查看失败原因
2. 常见原因及处理：

| 原因 | 排查 | 处理 |
|------|------|------|
| Checkpoint 超时 | 查看 Barrier 对齐时间 | 增大 `execution.checkpointing.timeout` |
| 存储写入失败 | 检查 HDFS 可用性和空间 | 修复 HDFS、清理空间 |
| 状态过大 | 查看 Checkpoint Size 趋势 | 设置 State TTL、优化状态 |
| 对齐Barrier延迟 | 查看 Backpressure 指标 | 排查反压、增加并行度 |

**调整参数：**

```bash
# 增大超时时间
execution.checkpointing.timeout: 600000  # 10 min

# 增大间隔
execution.checkpointing.interval: 60000  # 60 s

# 开启增量 Checkpoint (RocksDB)
state.backend: rocksdb
state.backend.incremental: true
```

#### HBase Hotspot (热点)

**现象：** 单个 Region 负载过高，写入/读取延迟增大。

**排查步骤：**

1. HBase Master UI -> Region Server 页面查看请求分布
2. 确认热点 Region：`hbase hbck` 或 UI 查看
3. 常见原因：
   - RowKey 顺序递增 (时间戳)
   - 未预分区
   - 数据倾斜

**处理方案：**

```bash
# 1. 紧急处理：Split 热点 Region
split '<hot_region_encoded_name>'

# 2. 根本解决：重新设计 RowKey
#    - 添加散列前缀: hash(prefix) + originalKey
#    - 反转 RowKey: reversed(key)
#    - 加盐: randomPrefix + key

# 3. 重新建表 (需停写)
disable 'customer_feature'
drop 'customer_feature'
create 'customer_feature', {NAME => 'cf', ...}, {SPLITS => ['1','2',...,'F']}
```
