/**
 * 实验模型
 */
export interface Experiment {
  id: string
  name: string
  trafficKey: string
  status: 'DRAFT' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'TERMINATED'
  groups: ExperimentGroup[]
  startDate: string
  endDate?: string
  terminationCondition?: string
  description?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}

export interface ExperimentGroup {
  name: string
  ratio: number
  strategyId: string
  isControl: boolean
}

/**
 * 沙箱回测
 */
export interface SandboxRequest {
  targetId: string
  targetType: 'RULE' | 'SCORECARD' | 'TABLE' | 'TREE' | 'FLOW'
  version?: number
  inputData: Record<string, any>
}

export interface SandboxResult {
  traceId: string
  result: string
  score?: number
  durationMs: number
  trace: TraceEntry[]
}

export interface TraceEntry {
  nodeId: string
  nodeType: string
  nodeName: string
  startTime: number
  endTime: number
  durationMs: number
  input: Record<string, any>
  output: Record<string, any>
  details?: Record<string, any>
  children?: TraceEntry[]
}

/**
 * 发布 & 审批
 */
export interface PublishRecord {
  id: string
  targetId: string
  targetType: string
  targetName: string
  fromVersion: number
  toVersion: number
  status: string
  operator: string
  operatedAt: string
  reason?: string
}

export interface GrayscaleConfig {
  configId: string
  targetId: string
  targetType: string
  targetVersion: number
  percentage: number
  previousPercentage: number
  operator: string
  startedAt: string
  updatedAt: string
  grayscaleStatus: 'NOT_STARTED' | 'IN_PROGRESS' | 'GRAYSCALE' | 'FULL' | 'RELEASED' | 'PAUSED' | 'ROLLED_BACK'
}

export interface ApprovalRecord {
  recordId: string
  targetId: string
  targetType: string
  targetVersion: number
  action: 'SUBMIT' | 'APPROVE' | 'REJECT' | 'WITHDRAW'
  operator: string
  operatedAt: string
  comment?: string
}
