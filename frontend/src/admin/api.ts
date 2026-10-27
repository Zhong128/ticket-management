import { request } from '../api/client'

export function adminRequest<T>(
    method: 'get' | 'post' | 'put' | 'delete',
    url: string,
    data?: object,
    params?: object,
    signal?: AbortSignal,
): Promise<T> {
    return request<T>(method, url, data, params, signal, 12000, 'admin')
}
