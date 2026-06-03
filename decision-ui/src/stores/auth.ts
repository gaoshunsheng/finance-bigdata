import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, Permission } from '@/types'
import { login as apiLogin, logout as apiLogout, getCurrentUser } from '@/api/auth'
import { getAccessToken, setTokens, clearTokens } from '@/utils/token'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const permissions = ref<Permission[]>([])
  const loading = ref(false)

  const isLoggedIn = computed(() => !!user.value && !!getAccessToken())
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isApprover = computed(() => {
    const role = user.value?.role
    return role === 'ADMIN' || role === 'APPROVER'
  })

  /** 角色优先级: VIEWER < EDITOR < APPROVER < ADMIN */
  const roleLevel = computed(() => {
    const levels: Record<string, number> = { VIEWER: 0, EDITOR: 1, APPROVER: 2, ADMIN: 3 }
    return levels[user.value?.role || ''] ?? -1
  })

  function hasPermission(perm: Permission): boolean {
    return permissions.value.includes(perm)
  }

  function hasRoleLevel(minLevel: number): boolean {
    return roleLevel.value >= minLevel
  }

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const res = await apiLogin({ username, password })
      setTokens(res.accessToken, res.refreshToken)
      user.value = res.user
      // 从角色推导权限列表 (与后端 Role.java 保持一致)
      permissions.value = derivePermissions(res.user.role)
    } finally {
      loading.value = false
    }
  }

  async function fetchUser() {
    if (!getAccessToken()) return
    try {
      const u = await getCurrentUser()
      user.value = u
      permissions.value = derivePermissions(u.role)
    } catch {
      clearTokens()
      user.value = null
    }
  }

  async function logout() {
    try {
      await apiLogout()
    } catch {
      // ignore
    }
    clearTokens()
    user.value = null
    permissions.value = []
  }

  return {
    user,
    permissions,
    loading,
    isLoggedIn,
    isAdmin,
    isApprover,
    roleLevel,
    hasPermission,
    hasRoleLevel,
    login,
    fetchUser,
    logout,
  }
})

/** 从角色推导权限 (与后端 Role.java 一致) */
function derivePermissions(role: string): Permission[] {
  const VIEWER_PERMS: Permission[] = [
    'rule:read', 'scorecard:read', 'table:read', 'tree:read', 'flow:read',
    'variable:read', 'experiment:read', 'decision_log:read', 'analytics:read',
  ]
  const EDITOR_PERMS: Permission[] = [
    ...VIEWER_PERMS,
    'rule:write', 'scorecard:write', 'table:write', 'tree:write', 'flow:write',
    'variable:write', 'experiment:write', 'sandbox:execute',
  ]
  const APPROVER_PERMS: Permission[] = [
    ...EDITOR_PERMS,
    'publish:approve', 'publish:reject', 'grayscale:manage',
  ]
  const ADMIN_PERMS: Permission[] = [
    ...APPROVER_PERMS,
    'user:manage', 'audit:read',
  ]

  switch (role) {
    case 'ADMIN': return ADMIN_PERMS
    case 'APPROVER': return APPROVER_PERMS
    case 'EDITOR': return EDITOR_PERMS
    case 'VIEWER': return VIEWER_PERMS
    default: return VIEWER_PERMS
  }
}
