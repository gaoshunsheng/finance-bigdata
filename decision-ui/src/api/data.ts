import request from './request'

// 安全修复: 移除双重 /api/v1 前缀
// request.ts 的 baseURL 已配置为 '/api/v1'，
// 这里只需写相对路径即可

export function getCustomerFeatures(customerId: string) {
  return request.get(`/features/${customerId}`)
}

export function getEnterpriseProfile(enterpriseId: string) {
  return request.get(`/profile/${enterpriseId}`)
}

export function getReport(type: string) {
  return request.get(`/reports/${type}`)
}
