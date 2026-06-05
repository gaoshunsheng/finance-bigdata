#!/bin/bash
# JMeter 性能测试运行脚本
# 用法: ./run-benchmark.sh [decision-server|model-platform|all]
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"
JMETER_BIN="${JMETER_HOME:-/opt/jmeter}/bin/jmeter"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 检查 JMeter 安装
check_jmeter() {
    if ! command -v "${JMETER_BIN}" &> /dev/null && ! command -v jmeter &> /dev/null; then
        log_error "JMeter 未安装。请设置 JMETER_HOME 环境变量或安装 JMeter。"
        log_error "下载地址: https://jmeter.apache.org/download_jmeter.cgi"
        exit 1
    fi
    # 使用 PATH 中的 jmeter 或指定路径
    if command -v jmeter &> /dev/null; then
        JMETER_CMD="jmeter"
    else
        JMETER_CMD="${JMETER_BIN}"
    fi
    log_info "JMeter: ${JMETER_CMD}"
}

# 创建结果目录
setup_results_dir() {
    mkdir -p "${RESULTS_DIR}"
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    log_info "结果输出目录: ${RESULTS_DIR}"
}

# 运行 decision-server 基准测试
run_decision_server_benchmark() {
    local test_plan="${SCRIPT_DIR}/decision-server-benchmark.jmx"
    if [[ ! -f "${test_plan}" ]]; then
        log_error "测试计划不存在: ${test_plan}"
        return 1
    fi

    log_info "========================================"
    log_info "启动决策引擎性能基准测试"
    log_info "目标: QPS≥500, P99<3s"
    log_info "========================================"

    # 非GUI模式运行
    "${JMETER_CMD}" -n -t "${test_plan}" \
        -l "${RESULTS_DIR}/decision-server-${TIMESTAMP}.jtl" \
        -e -o "${RESULTS_DIR}/decision-server-report-${TIMESTAMP}" \
        -JHOST="${DECISION_HOST:-localhost}" \
        -JPORT="${DECISION_PORT:-8080}" \
        -JTHREADS="${THREADS:-50}" \
        -JRAMP_UP="${RAMP_UP:-10}" \
        -JDURATION="${DURATION:-120}"

    log_info "决策引擎测试完成。报告: ${RESULTS_DIR}/decision-server-report-${TIMESTAMP}"
}

# 运行 model-platform 基准测试
run_model_platform_benchmark() {
    local test_plan="${SCRIPT_DIR}/model-platform-benchmark.jmx"
    if [[ ! -f "${test_plan}" ]]; then
        log_error "测试计划不存在: ${test_plan}"
        return 1
    fi

    log_info "========================================"
    log_info "启动模型平台性能基准测试"
    log_info "========================================"

    "${JMETER_CMD}" -n -t "${test_plan}" \
        -l "${RESULTS_DIR}/model-platform-${TIMESTAMP}.jtl" \
        -e -o "${RESULTS_DIR}/model-platform-report-${TIMESTAMP}" \
        -JHOST="${MODEL_HOST:-localhost}" \
        -JPORT="${MODEL_PORT:-8082}" \
        -JTHREADS="${THREADS:-20}" \
        -JRAMP_UP="${RAMP_UP:-5}" \
        -JLOOPS="${LOOPS:-100}"

    log_info "模型平台测试完成。报告: ${RESULTS_DIR}/model-platform-report-${TIMESTAMP}"
}

# 检查性能指标
check_performance() {
    log_info "========================================"
    log_info "性能指标检查"
    log_info "========================================"

    local latest_jtl=$(ls -t "${RESULTS_DIR}"/decision-server-*.jtl 2>/dev/null | head -1)
    if [[ -n "${latest_jtl}" ]]; then
        log_info "分析决策引擎测试结果: ${latest_jtl}"

        # 提取关键指标
        local total=$(tail -n +2 "${latest_jtl}" | wc -l | tr -d ' ')
        local errors=$(tail -n +2 "${latest_jtl}" | awk -F',' '{if($8=="false") print}' | wc -l | tr -d ' ')
        local error_rate=$(echo "scale=2; ${errors}*100/${total}" | bc 2>/dev/null || echo "0")
        local p99=$(tail -n +2 "${latest_jtl}" | awk -F',' '{print $2}' | sort -n | awk 'BEGIN{c=0} {a[c++]=$1} END{print a[int(c*0.99)]}')

        log_info "总请求数: ${total}"
        log_info "错误数: ${errors} (${error_rate}%)"
        log_info "P99 延迟: ${p99}ms"

        if [[ ${p99:-0} -lt 3000 ]] && [[ ${error_rate%.*} -lt 1 ]]; then
            log_info "✅ P99延迟和错误率达标"
        else
            log_warn "⚠️  性能指标未达标 (目标: P99<3000ms, 错误率<1%)"
        fi
    fi
}

# 主入口
main() {
    local target="${1:-all}"

    check_jmeter
    setup_results_dir

    case "${target}" in
        decision-server)
            run_decision_server_benchmark
            ;;
        model-platform)
            run_model_platform_benchmark
            ;;
        all)
            run_decision_server_benchmark
            run_model_platform_benchmark
            ;;
        *)
            echo "用法: $0 [decision-server|model-platform|all]"
            echo ""
            echo "环境变量:"
            echo "  JMETER_HOME        JMeter 安装目录 (默认: /opt/jmeter)"
            echo "  DECISION_HOST      决策引擎地址 (默认: localhost)"
            echo "  DECISION_PORT      决策引擎端口 (默认: 8080)"
            echo "  MODEL_HOST         模型平台地址 (默认: localhost)"
            echo "  MODEL_PORT         模型平台端口 (默认: 8082)"
            echo "  THREADS            并发线程数 (默认: 50)"
            echo "  RAMP_UP            启动时间/秒 (默认: 10)"
            echo "  DURATION           持续时间/秒 (默认: 120)"
            exit 1
            ;;
    esac

    check_performance
    log_info "所有性能测试完成!"
}

main "$@"
