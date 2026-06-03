import { get, post } from './request'
import type { PublishRecord, GrayscaleConfig, ApprovalRecord } from '@/types'

// --- 发布流程 ---
export function promoteToTesting(targetId: string, targetType: string, version: number) {
  return post(`/publish/promote?targetId=${targetId}&targetType=${targetType}&version=${version}`)
}

export function submitForApproval(targetId: string, targetType: string, version: number, comment?: string) {
  return post('/publish/submit-approval', { targetId, targetType, version, comment })
}

export function approve(targetId: string, targetType: string, comment?: string) {
  return post('/publish/approve', { targetId, targetType, comment })
}

export function reject(targetId: string, targetType: string, reason: string) {
  return post('/publish/reject', { targetId, targetType, reason })
}

export function withdraw(targetId: string, targetType: string) {
  return post('/publish/withdraw', { targetId, targetType })
}

export function getApprovalHistory(targetId: string, targetType: string): Promise<ApprovalRecord[]> {
  return get(`/publish/approval-history/${targetType}/${targetId}`)
}

export function getPendingApprovals(): Promise<ApprovalRecord[]> {
  return get('/publish/pending-approvals')
}

// --- 灰度发布 ---
export function startGrayscale(targetId: string, targetType: string, percentage?: number) {
  return post('/publish/grayscale/start', { targetId, targetType, percentage })
}

export function rampUpGrayscale(targetId: string, targetType: string) {
  return post(`/publish/grayscale/ramp-up?targetId=${targetId}&targetType=${targetType}`)
}

export function adjustGrayscale(targetId: string, targetType: string, percentage: number) {
  return post('/publish/grayscale/adjust', { targetId, targetType, percentage })
}

export function pauseGrayscale(targetId: string, targetType: string) {
  return post(`/publish/grayscale/pause?targetId=${targetId}&targetType=${targetType}`)
}

export function resumeGrayscale(targetId: string, targetType: string) {
  return post(`/publish/grayscale/resume?targetId=${targetId}&targetType=${targetType}`)
}

export function rollback(targetId: string, targetType: string, reason?: string) {
  return post('/publish/rollback', { targetId, targetType, reason })
}

export function getGrayscaleConfig(targetId: string, targetType: string): Promise<GrayscaleConfig> {
  return get(`/publish/grayscale/config/${targetType}/${targetId}`)
}

// --- 版本对比 ---
export function versionDiff(targetId: string, targetType: string, fromVersion: number, toVersion: number) {
  return get(`/publish/diff/${targetType}/${targetId}`, { fromVersion, toVersion })
}

export function versionDiffLatest(targetId: string, targetType: string) {
  return get(`/publish/diff-latest/${targetType}/${targetId}`)
}

// --- 发布历史 ---
export function getPublishHistory(targetId: string, targetType: string): Promise<PublishRecord[]> {
  return get(`/publish/history/${targetType}/${targetId}`)
}
