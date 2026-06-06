#!/bin/bash
# DolphinScheduler 工作流导入脚本
# 使用 DS API 导入每日 ETL 工作流定义
# 用法: ./import_workflow.sh <dolphinscheduler-url> <token>

DS_URL="${1:-http://localhost:12345}"
DS_TOKEN="${2:-}"

if [ -z "$DS_TOKEN" ]; then
    echo "Usage: $0 <dolphinscheduler-url> <api-token>"
    exit 1
fi

echo "导入每日 ETL 工作流到 DolphinScheduler..."

# 创建项目
curl -s -X POST "${DS_URL}/dolphinscheduler/projects" \
  -H "token: ${DS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"projectName":"finance-etl","description":"金融大数据 ETL 管道"}'

echo ""

# 导入工作流
curl -s -X POST "${DS_URL}/dolphinscheduler/projects/finance-etl/process/import" \
  -H "token: ${DS_TOKEN}" \
  -F "file=@daily_etl_workflow.json"

echo ""
echo "导入完成。请登录 http://localhost:12345 查看和上线工作流。"
