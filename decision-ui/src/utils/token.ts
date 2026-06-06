/**
 * Token 管理工具
 *
 * 安全说明:
 * - Access Token: 存储在 sessionStorage（关闭浏览器自动清除，XSS 攻击窗口较小）
 * - Refresh Token: 存储在 sessionStorage（同上）
 *
 * 生产环境建议:
 * - Access Token 由后端通过 httpOnly + Secure + SameSite=Strict cookie 下发
 * - Refresh Token 同样使用 httpOnly cookie
 * - 前端不需要手动管理 Token 存储
 *
 * 当前实现为过渡方案，后续应由后端 cookie 策略替换。
 */

const ACCESS_TOKEN_KEY = 'decision_access_token'
const REFRESH_TOKEN_KEY = 'decision_refresh_token'

/** 使用 sessionStorage 替代 localStorage，降低 XSS 窃取风险 */
function getStorage(): Storage {
  return sessionStorage
}

export function getAccessToken(): string | null {
  return getStorage().getItem(ACCESS_TOKEN_KEY)
}

export function setAccessToken(token: string): void {
  getStorage().setItem(ACCESS_TOKEN_KEY, token)
}

export function getRefreshToken(): string | null {
  return getStorage().getItem(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token: string): void {
  getStorage().setItem(REFRESH_TOKEN_KEY, token)
}

export function setTokens(access: string, refresh: string): void {
  setAccessToken(access)
  setRefreshToken(refresh)
}

export function clearTokens(): void {
  getStorage().removeItem(ACCESS_TOKEN_KEY)
  getStorage().removeItem(REFRESH_TOKEN_KEY)
}
