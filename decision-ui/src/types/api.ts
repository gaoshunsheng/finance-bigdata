/**
 * 通用 API 响应结构
 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/**
 * 分页请求参数
 */
export interface PageParams {
  page?: number
  size?: number
  sort_by?: string
  sort_order?: 'asc' | 'desc'
}

/**
 * 分页响应
 */
export interface PageResult<T> {
  content: T[]
  total: number
  page: number
  size: number
  total_pages: number
}
