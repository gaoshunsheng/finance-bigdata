/**
 * 变量模型
 */
export interface Variable {
  id: string
  name: string
  category: string
  layer: 'INPUT' | 'EXTERNAL_API' | 'FEATURE_STORE' | 'COMPUTED'
  source?: string
  dataType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'LIST' | 'MAP'
  expression?: string
  dependencies?: string[]
  version: number
  status: string
  description?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}
