#!/bin/bash
# ============================================================================
# DataX每日数据同步脚本
# 描述: 按顺序执行MySQL到HDFS的数据同步任务
# 用法: ./run_daily_sync.sh [yyyy-MM-dd]
# 示例: ./run_daily_sync.sh 2026-06-05
# 创建时间: 2026-06-06
# ============================================================================

# ==================== 颜色定义 ====================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # 无颜色（恢复默认）

# ==================== 变量定义 ====================
# 脚本所在目录
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
# DataX安装目录（请根据实际部署路径修改）
DATAX_HOME="${DATAX_HOME:-/opt/datax}"
# 日志目录
LOG_DIR="${SCRIPT_DIR}/logs"
# 日志文件
LOG_FILE="${LOG_DIR}/sync_$(date +%Y%m%d_%H%M%S).log"

# 确保日志目录存在
mkdir -p "${LOG_DIR}"

# ==================== 日期参数处理 ====================
if [ -n "$1" ]; then
    DT="$1"
else
    DT=$(TZ='Asia/Shanghai' date -v-1d '+%Y-%m-%d')
fi

# 验证日期格式
if ! echo "${DT}" | grep -qE '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'; then
    echo -e "${RED}[错误] 日期格式不正确: ${DT}，正确格式为 yyyy-MM-dd${NC}"
    exit 1
fi

# ==================== 同步任务列表 ====================
# 定义需要同步的表和对应的DataX配置文件
SYNC_JOBS=(
    "customer_info:${SCRIPT_DIR}/jobs/mysql_to_hdfs_customer_info.json"
    "loan_application:${SCRIPT_DIR}/jobs/mysql_to_hdfs_loan_application.json"
    "repayment_record:${SCRIPT_DIR}/jobs/mysql_to_hdfs_repayment_record.json"
)

# ==================== 工具函数 ====================
log() {
    local level=$1
    shift
    local message="$*"
    local timestamp=$(TZ='Asia/Shanghai' date '+%Y-%m-%d %H:%M:%S')
    case "${level}" in
        INFO)  echo -e "${GREEN}[INFO]  ${timestamp} - ${message}${NC}" ;;
        WARN)  echo -e "${YELLOW}[WARN]  ${timestamp} - ${message}${NC}" ;;
        ERROR) echo -e "${RED}[ERROR] ${timestamp} - ${message}${NC}" ;;
        STEP)  echo -e "${BLUE}[STEP]  ${timestamp} - ${message}${NC}" ;;
    esac
    echo "[${level}] ${timestamp} - ${message}" >> "${LOG_FILE}"
}

# 执行单个DataX同步任务
run_sync() {
    local table_name=$1
    local job_file=$2

    # 将配置文件中的${dt}替换为实际日期
    local temp_job_file="${SCRIPT_DIR}/jobs/.tmp_${table_name}_${DT}.json"
    sed "s/\${dt}/${DT}/g" "${job_file}" > "${temp_job_file}"

    log STEP "=========================================="
    log STEP "开始同步: ${table_name}"
    log STEP "配置文件: ${job_file}"
    log STEP "目标分区: dt=${DT}"
    log STEP "=========================================="

    local start_time=$(date +%s)

    # 执行DataX同步
    python "${DATAX_HOME}/bin/datax.py" "${temp_job_file}" 2>&1 | tee -a "${LOG_FILE}"
    local exit_code=${PIPESTATUS[0]}

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    # 清理临时配置文件
    rm -f "${temp_job_file}"

    if [ ${exit_code} -eq 0 ]; then
        log INFO "${table_name} 同步成功！耗时: ${duration} 秒"
        return 0
    else
        log ERROR "${table_name} 同步失败！退出码: ${exit_code}，耗时: ${duration} 秒"
        return ${exit_code}
    fi
}

# ==================== 主流程 ====================
log INFO "=========================================="
log INFO "DataX每日数据同步任务开始"
log INFO "同步日期: ${DT}"
log INFO "DataX目录: ${DATAX_HOME}"
log INFO "日志文件: ${LOG_FILE}"
log INFO "同步表数: ${#SYNC_JOBS[@]}"
log INFO "=========================================="

TOTAL_START=$(date +%s)
SUCCESS_COUNT=0
FAIL_COUNT=0

# 按顺序执行每个同步任务
for job in "${SYNC_JOBS[@]}"; do
    table_name="${job%%:*}"
    job_file="${job#*:}"

    # 检查配置文件是否存在
    if [ ! -f "${job_file}" ]; then
        log ERROR "配置文件不存在: ${job_file}，跳过 ${table_name}"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        continue
    fi

    if run_sync "${table_name}" "${job_file}"; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        log ERROR "${table_name} 同步失败，终止后续任务"
        break
    fi
done

# ==================== 执行结果汇总 ====================
TOTAL_END=$(date +%s)
TOTAL_DURATION=$((TOTAL_END - TOTAL_START))

log INFO "=========================================="
log INFO "DataX每日数据同步任务结束"
log INFO "同步日期: ${DT}"
log INFO "成功: ${SUCCESS_COUNT} 个表"
log INFO "失败: ${FAIL_COUNT} 个表"
log INFO "总耗时: ${TOTAL_DURATION} 秒 ($((TOTAL_DURATION / 60)) 分钟)"
log INFO "日志文件: ${LOG_FILE}"
log INFO "=========================================="

# 如果有失败的任务，以非0退出码返回
if [ ${FAIL_COUNT} -gt 0 ]; then
    exit 1
fi

exit 0
