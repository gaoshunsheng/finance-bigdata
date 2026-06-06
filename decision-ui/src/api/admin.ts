import { get, post, put, del } from './request'
import type { PageParams, PageResult } from '@/types'
import type { Rule } from '@/types/rule'
import type { Scorecard } from '@/types/scorecard'
import type { DecisionTable, DecisionTree, Flow } from '@/types/flow'
import type { Variable } from '@/types/variable'
import type { Experiment } from '@/types/experiment'

// --- 规则 ---
export function listRules(params?: PageParams & { type?: string; status?: string; keyword?: string }) {
  return get<PageResult<Rule>>('/rules', params)
}

export function getRule(id: string) {
  return get<Rule>(`/rules/${id}`)
}

export function createRule(data: Partial<Rule>) {
  return post<Rule>('/rules', data)
}

export function updateRule(id: string, data: Partial<Rule>) {
  return put<Rule>(`/rules/${id}`, data)
}

export function deleteRule(id: string) {
  return del(`/rules/${id}`)
}

// --- 评分卡 ---
export function listScorecards(params?: PageParams & { keyword?: string }) {
  return get<PageResult<Scorecard>>('/scorecards', params)
}

export function getScorecard(id: string) {
  return get<Scorecard>(`/scorecards/${id}`)
}

export function createScorecard(data: Partial<Scorecard>) {
  return post<Scorecard>('/scorecards', data)
}

export function updateScorecard(id: string, data: Partial<Scorecard>) {
  return put<Scorecard>(`/scorecards/${id}`, data)
}

export function deleteScorecard(id: string) {
  return del(`/scorecards/${id}`)
}

// --- 决策表 ---
export function listTables(params?: PageParams & { keyword?: string }) {
  return get<PageResult<DecisionTable>>('/tables', params)
}

export function getTable(id: string) {
  return get<DecisionTable>(`/tables/${id}`)
}

export function createTable(data: Partial<DecisionTable>) {
  return post<DecisionTable>('/tables', data)
}

export function updateTable(id: string, data: Partial<DecisionTable>) {
  return put<DecisionTable>(`/tables/${id}`, data)
}

export function deleteTable(id: string) {
  return del(`/tables/${id}`)
}

// --- 决策树 ---
export function listTrees(params?: PageParams & { keyword?: string }) {
  return get<PageResult<DecisionTree>>('/trees', params)
}

export function getTree(id: string) {
  return get<DecisionTree>(`/trees/${id}`)
}

export function createTree(data: Partial<DecisionTree>) {
  return post<DecisionTree>('/trees', data)
}

export function updateTree(id: string, data: Partial<DecisionTree>) {
  return put<DecisionTree>(`/trees/${id}`, data)
}

export function deleteTree(id: string) {
  return del(`/trees/${id}`)
}

// --- 决策流 ---
export function listFlows(params?: PageParams & { keyword?: string }) {
  return get<PageResult<Flow>>('/flows', params)
}

export function getFlow(id: string) {
  return get<Flow>(`/flows/${id}`)
}

export function createFlow(data: Partial<Flow>) {
  return post<Flow>('/flows', data)
}

export function updateFlow(id: string, data: Partial<Flow>) {
  return put<Flow>(`/flows/${id}`, data)
}

export function deleteFlow(id: string) {
  return del(`/flows/${id}`)
}

// --- 变量 ---
export function listVariables(params?: PageParams & { category?: string; layer?: string; keyword?: string }) {
  return get<PageResult<Variable>>('/variables', params)
}

export function getVariable(id: string) {
  return get<Variable>(`/variables/${id}`)
}

export function createVariable(data: Partial<Variable>) {
  return post<Variable>('/variables', data)
}

export function updateVariable(id: string, data: Partial<Variable>) {
  return put<Variable>(`/variables/${id}`, data)
}

export function deleteVariable(id: string) {
  return del(`/variables/${id}`)
}

// --- 实验 ---
export function listExperiments(params?: PageParams & { status?: string }) {
  return get<PageResult<Experiment>>('/experiments', params)
}

export function getExperiment(id: string) {
  return get<Experiment>(`/experiments/${id}`)
}

export function createExperiment(data: Partial<Experiment>) {
  return post<Experiment>('/experiments', data)
}

export function updateExperiment(id: string, data: Partial<Experiment>) {
  return put<Experiment>(`/experiments/${id}`, data)
}

export function deleteExperiment(id: string) {
  return del(`/experiments/${id}`)
}
