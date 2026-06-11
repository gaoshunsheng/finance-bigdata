import { get, post } from './request'
import type { PublishRecord, GrayscaleConfig, ApprovalRecord } from '@/types'

// --- 发布流程 ---
export function promoteToTesting(targetId: string, targetType: string) {
  return post(`/publish/${targetType}/${targetId}/promote-to-testing`, { operator: '' })
}

export function submitForApproval(targetId: string, targetType: string, comment?: string) {
  return post(`/publish/${targetType}/${targetId}/submit-approval`, { operator: '', comment })
}

export function approve(targetId: string, targetType: string, comment?: string) {
  return post(`/publish/${targetType}/${targetId}/approve`, { operator: '', comment })
}

export function reject(targetId: string, targetType: string, reason: string) {
  return post(`/publish/${targetType}/${targetId}/reject`, { operator: '', reason })
}

export function withdraw(targetId: string, targetType: string) {
  return post(`/publish/${targetType}/${targetId}/withdraw`, { operator: '', comment: '' })
}

export function getApprovalHistory(targetId: string, targetType: string): Promise<ApprovalRecord[]> {
  return get(`/publish/${targetType}/${targetId}/approval-history`)
}

export function getPendingApprovals(): Promise<ApprovalRecord[]> {
  return get('/publish/pending-approvals')
}

// --- 灰度发布 ---
export function startGrayscale(targetId: string, targetType: string, percentage?: number) {
  return post(`/publish/${targetType}/${targetId}/grayscale/start`, { operator: '', percentage: percentage ?? 10 })
}

export function rampUpGrayscale(targetId: string, targetType: string) {
  return post(`/publish/${targetType}/${targetId}/grayscale/ramp-up`, { operator: '' })
}

export function adjustGrayscale(targetId: string, targetType: string, percentage: number) {
  return post(`/publish/${targetType}/${targetId}/grayscale/adjust`, { operator: '', percentage })
}

export function pauseGrayscale(targetId: string, targetType: string) {
  return post(`/publish/${targetType}/${targetId}/grayscale/pause`, { operator: '' })
}

export function resumeGrayscale(targetId: string, targetType: string) {
  return post(`/publish/${targetType}/${targetId}/grayscale/resume`, { operator: '' })
}

export function rollback(targetId: string, targetType: string, version: number) {
  return post(`/publish/${targetType}/${targetId}/rollback/${version}`, { operator: '' })
}

export function getGrayscaleConfig(targetId: string, targetType: string): Promise<GrayscaleConfig> {
  return get(`/publish/${targetType}/${targetId}/grayscale`)
}

// --- 版本对比 ---
export function versionDiff(targetId: string, targetType: string, fromVersion: number, toVersion: number) {
  return get(`/publish/${targetType}/${targetId}/diff`, { from: fromVersion, to: toVersion })
}

export function versionDiffLatest(targetId: string, targetType: string) {
  return get(`/publish/${targetType}/${targetId}/diff-latest`)
}

// --- 发布历史（单个实体） ---
export function getPublishHistory(targetId: string, targetType: string): Promise<PublishRecord[]> {
  return get(`/publish/${targetType}/${targetId}/history`)
}

// --- 全局聚合查询（发布中心用） ---
export function getAllGrayscaleConfigs(): Promise<GrayscaleConfig[]> {
  return get('/publish/grayscale/list')
}

export function getAllApprovalRecords(): Promise<ApprovalRecord[]> {
  return get('/publish/history')
}
