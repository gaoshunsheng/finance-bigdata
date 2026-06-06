/**
 * 用户 & 认证
 */
export interface User {
  id: number
  username: string
  displayName: string
  email: string
  role: 'VIEWER' | 'EDITOR' | 'APPROVER' | 'ADMIN'
  enabled: boolean
  createdAt: string
  updatedAt: string
  lastLoginAt?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  username: string
  displayName: string
  role: 'VIEWER' | 'EDITOR' | 'APPROVER' | 'ADMIN'
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
}

/**
 * 权限枚举
 */
export type Permission =
  | 'rule:read' | 'rule:write'
  | 'scorecard:read' | 'scorecard:write'
  | 'table:read' | 'table:write'
  | 'tree:read' | 'tree:write'
  | 'flow:read' | 'flow:write'
  | 'variable:read' | 'variable:write'
  | 'experiment:read' | 'experiment:write'
  | 'decision_log:read'
  | 'analytics:read'
  | 'publish:approve' | 'publish:reject'
  | 'grayscale:manage'
  | 'sandbox:execute'
  | 'user:manage'
  | 'audit:read'
