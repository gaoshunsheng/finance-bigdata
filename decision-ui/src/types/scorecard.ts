/**
 * 评分卡模型
 */
export interface Scorecard {
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

export interface ScorecardModel {
  initialScore: number
  characteristics: Characteristic[]
  cutoff?: number
}

export interface Characteristic {
  name: string
  field: string
  bins: Bin[]
}

export interface Bin {
  from?: number
  to?: number
  fromInclusive?: boolean
  toInclusive?: boolean
  values?: string[]
  score: number
  label?: string
}
