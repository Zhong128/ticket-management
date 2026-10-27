import type { Event } from '../api/types'

export function dateLabel(value?: string) {
    if (!value) return '时间待公布'
    const match = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
    return match ? `${match[1]}.${match[2]}.${match[3]}` : '时间待公布'
}
export function money(value: number | null | undefined) {
    return value == null ? '' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
function localTimestamp(value?: string) {
    if (!value) return Number.NaN
    return Date.parse(
        value.replace(' ', 'T') + (/(Z|[+-]\d{2}:?\d{2})$/i.test(value) ? '' : '+08:00'),
    )
}

export function isUpcomingEvent(event: Event, now = Date.now()) {
    return event.status === 1 && localTimestamp(event.eventEndTime) > now
}

export function saleState(event: Event, now = Date.now()) {
    const start = localTimestamp(event.saleStartTime)
    const end = localTimestamp(event.saleEndTime)
    if (event.status !== 1 || !Number.isFinite(start) || !Number.isFinite(end)) return '暂不可售'
    if (now < start) return '即将开售'
    if (now >= end) return '已停售'
    return '售票中'
}
