const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
const DEFAULT_TIMEOUT = 15000

export class ApiError extends Error {
  constructor(public status: number, message: string, public code?: string, public details?: unknown) { super(message) }
}

type RequestOptions = RequestInit & { timeout?: number; skipAuth?: boolean }
type AuthCallbacks = { getToken: () => string | null; onUnauthorized: () => void; onForbidden: () => void }
let authCallbacks: AuthCallbacks = { getToken: () => sessionStorage.getItem('cognivault_token'), onUnauthorized: () => window.dispatchEvent(new CustomEvent('auth:unauthorized')), onForbidden: () => window.dispatchEvent(new CustomEvent('auth:forbidden')) }
export const configureApiAuth = (callbacks: AuthCallbacks) => { authCallbacks = callbacks }

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), options.timeout ?? DEFAULT_TIMEOUT)
  const token = options.skipAuth ? null : authCallbacks.getToken()
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  headers.set('Accept', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers, signal: controller.signal })
    if (response.status === 401) authCallbacks.onUnauthorized()
    if (response.status === 403) authCallbacks.onForbidden()
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new ApiError(response.status, body.message || `Request failed (${response.status})`, body.code, body)
    }
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error instanceof DOMException && error.name === 'AbortError') throw new ApiError(408, 'The request timed out.')
    throw new ApiError(0, 'Unable to reach the CogniVault backend.', 'NETWORK_ERROR', error)
  } finally { window.clearTimeout(timer) }
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, options),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>(path, { ...options, method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: <T>(path: string, body?: unknown) => request<T>(path, { method: 'DELETE', body: body === undefined ? undefined : JSON.stringify(body) }),
}

export const queryString = (params: object) => {
  const query = new URLSearchParams()
  Object.entries(params as Record<string, unknown>).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') query.set(key, String(value)) })
  const result = query.toString()
  return result ? `?${result}` : ''
}
