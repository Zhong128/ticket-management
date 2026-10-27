import { ref, onBeforeUnmount } from 'vue'
import { adminRequest } from '../api'

export function useAdminTask() {
    const error = ref('')
    const busy = ref(false)
    let active = true
    let controller = new AbortController()
    onBeforeUnmount(() => {
        active = false
        controller.abort()
    })
    async function run<T>(task: (signal: AbortSignal) => Promise<T>, apply?: (value: T) => void) {
        if (!active) {
            return
        }
        controller.abort()
        const current = new AbortController()
        controller = current
        busy.value = true
        error.value = ''
        try {
            const result = await task(current.signal)
            if (active && !current.signal.aborted) {
                apply?.(result)
            }
        } catch (failure) {
            if (active && !current.signal.aborted) {
                error.value = failure instanceof Error ? failure.message : '操作失败，请重试'
            }
        } finally {
            if (active && !current.signal.aborted) {
                busy.value = false
            }
        }
    }
    return { error, busy, run }
}

export async function allOptions<T>(url: string, signal: AbortSignal, params: object = {}) {
    const records: T[] = []
    let page = 1
    while (true) {
        const result = await adminRequest<{
            records: T[]
            total: number
        }>('get', url, undefined, { ...params, page, size: 100 }, signal)
        records.push(...result.records)
        if (!result.records.length || records.length >= result.total) {
            return records
        }
        page++
    }
}

export async function referenceOptions(signal: AbortSignal) {
    const [cities, categories] = await Promise.all([
        allOptions<{ id: string; name: string }>('/admin/cities/query', signal),
        allOptions<{ id: string; name: string }>('/admin/categories/query', signal),
    ])
    return { cities, categories }
}

export const eventStatus = (status: number) => ['草稿', '已发布', '已结束'][status] || '未知状态'
export const timeText = (value?: string) => value?.replace('T', ' ').slice(0, 16) || '—'
export const safeCover = (value?: string) => (/^https?:\/\//i.test(value || '') ? value : undefined)
