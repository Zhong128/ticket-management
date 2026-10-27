import axios from 'axios'
import { isLosslessNumber, parse } from 'lossless-json'
import { readSession, saveSession } from '../utils/session'
import type { SessionScope } from '../utils/session'

export interface Result<T> {
    code: number
    msg?: string
    data?: T
}

export class ApiError extends Error {
    constructor(
        public code: number,
        message: string,
        public data?: unknown,
    ) {
        super(message)
    }
}

// 在转成 JS number 前保留 ID 的原始十进制文本。
export function decodeResponse(raw: string): any {
    return parse(raw, (key, value) => {
        if (isLosslessNumber(value)) {
            return /(^id$|Id$|_id$)/.test(key) ? value.toString() : Number(value.toString())
        }
        return value
    })
}

export function unwrapResult<T>(result: Result<T>): T {
    if (result.code !== 1)
        throw new ApiError(result.code, result.msg || '请求未完成，请重试', result.data)
    return result.data as T
}

const client = axios.create({
    baseURL: '/api',
    timeout: 12000,
    transformResponse: [decodeResponse],
})

export async function request<T>(
    method: 'get' | 'post' | 'put' | 'delete',
    url: string,
    data?: object,
    params?: object,
    signal?: AbortSignal,
    timeout = 12000,
    scope: SessionScope = 'user',
    tokenOverride?: string,
): Promise<T> {
    const token = tokenOverride ?? readSession(scope)?.token
    function expireCurrentSession() {
        if (readSession(scope)?.token !== token) return
        saveSession(null, scope)
        window.dispatchEvent(
            new Event(scope === 'admin' ? 'youxi-admin-auth-expired' : 'youxi-auth-expired'),
        )
    }
    try {
        const response = await client.request<Result<T>>({
            method,
            url,
            data,
            params,
            signal,
            timeout,
            headers: token ? { 'ticket-token': token } : {},
        })
        if (response.data.code === 401) {
            expireCurrentSession()
        }
        if (response.data.code === 403)
            throw new ApiError(403, response.data.msg || '当前账号没有此操作权限')
        return unwrapResult(response.data)
    } catch (error) {
        if (axios.isCancel(error)) throw error
        if (axios.isAxiosError(error)) {
            if (error.response?.status === 403) throw new ApiError(403, '当前账号没有此操作权限')
            if (error.response?.status === 401) {
                expireCurrentSession()
                throw new ApiError(401, '登录已失效，请重新登录')
            }
            throw new ApiError(
                0,
                error.code === 'ECONNABORTED'
                    ? '连接超时，请稍后重试'
                    : '暂时无法加载，请检查网络后重试',
            )
        }
        throw error
    }
}
export const get = <T>(url: string, params?: object, signal?: AbortSignal) =>
    request<T>('get', url, undefined, params, signal)
export const post = <T>(url: string, data?: object) => request<T>('post', url, data)
export const put = <T>(url: string, data?: object) => request<T>('put', url, data)

export const remove = <T>(url: string, signal?: AbortSignal) =>
    request<T>('delete', url, undefined, undefined, signal)
