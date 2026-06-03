import { get, post, put, del } from './request'
import type { PageParams, PageResult } from '@/types'

// --- 规则 ---
export function listRules(params?: PageParams & { type?: string; status?: string; keyword?: string }) {
  return get<PageResult<any>>('/rules', params)
}

export function getRule(id: string) {
  return get<any>(`/rules/${id}`)
}

export function createRule(data: any) {
  return post<any>('/rules', data)
}

export function updateRule(id: string, data: any) {
  return put<any>(`/rules/${id}`, data)
}

export function deleteRule(id: string) {
  return del(`/rules/${id}`)
}

// --- 评分卡 ---
export function listScorecards(params?: PageParams & { keyword?: string }) {
  return get<PageResult<any>>('/scorecards', params)
}

export function getScorecard(id: string) {
  return get<any>(`/scorecards/${id}`)
}

export function createScorecard(data: any) {
  return post<any>('/scorecards', data)
}

export function updateScorecard(id: string, data: any) {
  return put<any>(`/scorecards/${id}`, data)
}

export function deleteScorecard(id: string) {
  return del(`/scorecards/${id}`)
}

// --- 决策表 ---
export function listTables(params?: PageParams & { keyword?: string }) {
  return get<PageResult<any>>('/tables', params)
}

export function getTable(id: string) {
  return get<any>(`/tables/${id}`)
}

export function createTable(data: any) {
  return post<any>('/tables', data)
}

export function updateTable(id: string, data: any) {
  return put<any>(`/tables/${id}`, data)
}

export function deleteTable(id: string) {
  return del(`/tables/${id}`)
}

// --- 决策树 ---
export function listTrees(params?: PageParams & { keyword?: string }) {
  return get<PageResult<any>>('/trees', params)
}

export function getTree(id: string) {
  return get<any>(`/trees/${id}`)
}

export function createTree(data: any) {
  return post<any>('/trees', data)
}

export function updateTree(id: string, data: any) {
  return put<any>(`/trees/${id}`, data)
}

export function deleteTree(id: string) {
  return del(`/trees/${id}`)
}

// --- 决策流 ---
export function listFlows(params?: PageParams & { keyword?: string }) {
  return get<PageResult<any>>('/flows', params)
}

export function getFlow(id: string) {
  return get<any>(`/flows/${id}`)
}

export function createFlow(data: any) {
  return post<any>('/flows', data)
}

export function updateFlow(id: string, data: any) {
  return put<any>(`/flows/${id}`, data)
}

export function deleteFlow(id: string) {
  return del(`/flows/${id}`)
}

// --- 变量 ---
export function listVariables(params?: PageParams & { category?: string; layer?: string; keyword?: string }) {
  return get<PageResult<any>>('/variables', params)
}

export function getVariable(id: string) {
  return get<any>(`/variables/${id}`)
}

export function createVariable(data: any) {
  return post<any>('/variables', data)
}

export function updateVariable(id: string, data: any) {
  return put<any>(`/variables/${id}`, data)
}

export function deleteVariable(id: string) {
  return del(`/variables/${id}`)
}

// --- 实验 ---
export function listExperiments(params?: PageParams & { status?: string }) {
  return get<PageResult<any>>('/experiments', params)
}

export function getExperiment(id: string) {
  return get<any>(`/experiments/${id}`)
}

export function createExperiment(data: any) {
  return post<any>('/experiments', data)
}

export function updateExperiment(id: string, data: any) {
  return put<any>(`/experiments/${id}`, data)
}

export function deleteExperiment(id: string) {
  return del(`/experiments/${id}`)
}
