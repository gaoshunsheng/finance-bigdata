import request from './request'

export function getCustomerFeatures(customerId: string) {
  return request.get(`/api/v1/features/${customerId}`)
}

export function getEnterpriseProfile(enterpriseId: string) {
  return request.get(`/api/v1/profile/${enterpriseId}`)
}

export function getReport(type: string) {
  return request.get(`/api/v1/reports/${type}`)
}
