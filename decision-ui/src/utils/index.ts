import dayjs from 'dayjs'

/** 格式化时间 (北京时间) */
export function formatTime(value: string | Date, format = 'YYYY-MM-DD HH:mm:ss'): string {
  return dayjs(value).format(format)
}

/** 状态标签颜色 */
export function statusColor(status: string): string {
  const map: Record<string, string> = {
    DRAFT: 'default',
    TESTING: 'processing',
    PENDING_REVIEW: 'warning',
    APPROVED: 'success',
    GRAYSCALE: 'cyan',
    RELEASED: 'green',
    REJECTED: 'error',
    RUNNING: 'processing',
    PAUSED: 'warning',
    COMPLETED: 'success',
    TERMINATED: 'default',
  }
  return map[status] || 'default'
}

/** 状态中文标签 */
export function statusLabel(status: string): string {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    TESTING: '测试中',
    PENDING_REVIEW: '待审批',
    APPROVED: '已审批',
    GRAYSCALE: '灰度中',
    RELEASED: '已发布',
    REJECTED: '已驳回',
    RUNNING: '运行中',
    PAUSED: '已暂停',
    COMPLETED: '已完成',
    TERMINATED: '已终止',
  }
  return map[status] || status
}

/** 角色 中文 */
export function roleLabel(role: string): string {
  const map: Record<string, string> = {
    VIEWER: '观察者',
    EDITOR: '编辑者',
    APPROVER: '审批者',
    ADMIN: '管理员',
  }
  return map[role] || role
}

/** 节点类型中文 */
export function nodeTypeLabel(type: string): string {
  const map: Record<string, string> = {
    START: '开始',
    END: '结束',
    DATA_PREP: '数据准备',
    RULE_SET: '规则集',
    SCORECARD: '评分卡',
    MODEL: '模型',
    DECISION: '决策',
    SUB_FLOW: '子流程',
    AB_SPLIT: 'AB分流',
    ACTION: '动作',
    SCRIPT: '脚本',
  }
  return map[type] || type
}
