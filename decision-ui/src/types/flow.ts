/**
 * 决策表模型
 */
export interface DecisionTable {
  id: string
  name: string
  content: string
  version: number
  status: string
  description?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}

export interface TableModel {
  columns: TableColumn[]
  rows: TableRow[]
  hitPolicy: 'FIRST_MATCH' | 'ALL_MATCH' | 'PRIORITY'
}

export interface TableColumn {
  field: string
  label: string
  type: 'INPUT' | 'OUTPUT'
  dataType?: 'STRING' | 'NUMBER' | 'BOOLEAN'
}

export interface TableRow {
  id: string
  cells: Record<string, any>
  priority?: number
  output?: Record<string, any>
}

/**
 * 决策树模型
 */
export interface DecisionTree {
  id: string
  name: string
  content: string
  version: number
  status: string
  description?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}

/**
 * DAG 决策流模型
 */
export interface Flow {
  id: string
  name: string
  content: string
  version: number
  status: string
  description?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}

/** DAG 节点类型 */
export type NodeType =
  | 'DATA_PREP'
  | 'RULE_SET'
  | 'SCORECARD'
  | 'MODEL'
  | 'DECISION'
  | 'SUB_FLOW'
  | 'AB_SPLIT'
  | 'ACTION'
  | 'SCRIPT'
  | 'START'
  | 'END'

/** DAG 节点 */
export interface FlowNode {
  id: string
  type: NodeType
  name: string
  position: { x: number; y: number }
  config: Record<string, any>
}

/** DAG 边 */
export interface FlowEdge {
  id: string
  source: string
  target: string
  condition?: string
  label?: string
}

/** DAG 模型 */
export interface FlowModel {
  nodes: FlowNode[]
  edges: FlowEdge[]
}
