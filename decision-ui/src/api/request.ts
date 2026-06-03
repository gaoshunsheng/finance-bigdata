import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from '@/utils/token'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

/** 主 Axios 实例 */
const request: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// --- 刷新 Token 相关 ---
let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

function onTokenRefreshed(newToken: string) {
  pendingRequests.forEach((cb) => cb(newToken))
  pendingRequests = []
}

// --- 请求拦截器 ---
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getAccessToken()
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// --- 响应拦截器 ---
request.interceptors.response.use(
  (response) => {
    const { data } = response
    // 业务错误码处理
    if (data.code !== undefined && data.code !== 0 && data.code !== 200) {
      message.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  async (error) => {
    const { response, config } = error

    if (!response) {
      message.error('网络连接异常，请检查网络')
      return Promise.reject(error)
    }

    const { status } = response

    // 401 - Token 过期，尝试刷新
    if (status === 401 && !((config as any)._retry)) {
      const refreshToken = getRefreshToken()
      if (!refreshToken) {
        clearTokens()
        window.location.href = '/login'
        return Promise.reject(error)
      }

      if (isRefreshing) {
        return new Promise((resolve) => {
          pendingRequests.push((newToken: string) => {
            config.headers.Authorization = `Bearer ${newToken}`
            resolve(request(config))
          })
        })
      }

      (config as any)._retry = true
      isRefreshing = true

      try {
        const res = await axios.post(`${BASE_URL}/auth/refresh`, {
          refreshToken,
        })
        const { accessToken, refreshToken: newRefresh } = res.data.data || res.data
        setTokens(accessToken, newRefresh)
        onTokenRefreshed(accessToken)
        config.headers.Authorization = `Bearer ${accessToken}`
        return request(config)
      } catch {
        clearTokens()
        window.location.href = '/login'
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }

    // 403
    if (status === 403) {
      message.error('没有操作权限')
    } else if (status === 404) {
      message.error('请求的资源不存在')
    } else if (status >= 500) {
      message.error('服务器异常，请稍后重试')
    } else {
      message.error(response.data?.message || '请求失败')
    }

    return Promise.reject(error)
  },
)

/** 通用请求方法 */
export function get<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<T> {
  return request.get(url, { params, ...config })
}

export function post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  return request.post(url, data, config)
}

export function put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  return request.put(url, data, config)
}

export function del<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request.delete(url, config)
}

export default request
