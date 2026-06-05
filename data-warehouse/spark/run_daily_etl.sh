#!/bin/bash
# ============================================================================
# 每日ETL调度脚本
# 描述: 按顺序执行 ODS->DWD->DWS->ADS 三层ETL脚本
# 用法: ./run_daily_etl.sh [yyyy-MM-dd]
# 示例: ./run_daily_etl.sh 2026-06-05
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
# 日志目录
LOG_DIR="${SCRIPT_DIR}/logs"
# 日志文件
LOG_FILE="${LOG_DIR}/etl_$(date +%Y%m%d_%H%M%S).log"

# 确保日志目录存在
mkdir -p "${LOG_DIR}"

# ==================== 日期参数处理 ====================
# 如果传入日期参数则使用传入值，否则默认使用昨天（北京时间）
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

# ==================== 工具函数 ====================
# 打印日志（同时输出到终端和日志文件）
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

# 执行单个ETL脚本
run_etl() {
    local step_name=$1
    local sql_file=$2

    log STEP "=========================================="
    log STEP "开始执行: ${step_name}"
    log STEP "脚本文件: ${sql_file}"
    log STEP "分区日期: ${DT}"
    log STEP "=========================================="

    local start_time=$(date +%s)

    # 执行spark-sql，传入分区日期变量
    spark-sql \
        --master yarn \
        --deploy-mode client \
        --name "${step_name} dt=${DT}" \
        --executor-memory 4g \
        --executor-cores 2 \
        --num-executors 10 \
        -f "${sql_file}" \
        --hivevar dt="${DT}" \
        2>&1 | tee -a "${LOG_FILE}"

    local exit_code=${PIPESTATUS[0]}
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    if [ ${exit_code} -eq 0 ]; then
        log INFO "${step_name} 执行成功！耗时: ${duration} 秒"
        return 0
    else
        log ERROR "${step_name} 执行失败！退出码: ${exit_code}，耗时: ${duration} 秒"
        return ${exit_code}
    fi
}

# ==================== 主流程 ====================
log INFO "=========================================="
log INFO "每日ETL任务开始"
log INFO "执行日期: ${DT}"
log INFO "日志文件: ${LOG_FILE}"
log INFO "=========================================="

TOTAL_START=$(date +%s)

# ---------- 第1步: ODS -> DWD ----------
if ! run_etl "ODS到DWD清洗转换" "${SCRIPT_DIR}/etl_ods_to_dwd.sql"; then
    log ERROR "ODS->DWD 步骤失败，终止后续执行"
    exit 1
fi

# ---------- 第2步: DWD -> DWS ----------
if ! run_etl "DWD到DWS聚合汇总" "${SCRIPT_DIR}/etl_dwd_to_dws.sql"; then
    log ERROR "DWD->DWS 步骤失败，终止后续执行"
    exit 1
fi

# ---------- 第3步: DWS -> ADS ----------
if ! run_etl "DWS到ADS应用层" "${SCRIPT_DIR}/etl_dws_to_ads.sql"; then
    log ERROR "DWS->ADS 步骤失败，终止后续执行"
    exit 1
fi

# ==================== 执行完成 ====================
TOTAL_END=$(date +%s)
TOTAL_DURATION=$((TOTAL_END - TOTAL_START))

log INFO "=========================================="
log INFO "每日ETL任务全部完成"
log INFO "执行日期: ${DT}"
log INFO "总耗时: ${TOTAL_DURATION} 秒 ($((TOTAL_DURATION / 60)) 分钟)"
log INFO "日志文件: ${LOG_FILE}"
log INFO "=========================================="

exit 0
