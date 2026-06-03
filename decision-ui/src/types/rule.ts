/**
 * 规则模型
 */
export interface Rule {
  id: string
  name: string
  type: 'CONDITION' | 'RULE_SET'
  target: 'RULE' | 'SCORECARD' | 'TABLE' | 'TREE' | 'FLOW'
  content: string
  version: number
  status: 'DRAFT' | 'TESTING' | 'PENDING_REVIEW' | 'APPROVED' | 'GRAYSCALE' | 'RELEASED'
  description?: string
  tags?: string[]
  createdBy?: string
  createdAt: string
  updatedAt: string
}

/**
 * 条件结构
 */
export interface Condition {
  field: string
  operator: 'GT' | 'LT' | 'GTE' | 'LTE' | 'EQ' | 'NEQ' | 'BETWEEN' | 'IN' | 'NOT_IN' | 'CONTAINS' | 'STARTS_WITH' | 'IS_NULL' | 'IS_NOT_NULL'
  value: any
  valueType?: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATE'
}

/**
 * 逻辑节点
 */
export interface LogicNode {
  type: 'AND' | 'OR' | 'NOT'
  children?: LogicNode[]
  condition?: Condition
}

/**
 * 动作
 */
export interface Action {
  type: 'OUTPUT' | 'ASSIGN' | 'REJECT' | 'APPROVE' | 'MANUAL'
  result?: string
  score?: number
  reason?: string
  properties?: Record<string, any>
}
