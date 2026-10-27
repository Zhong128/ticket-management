import { describe, expect, it } from 'vitest'
import type { Event } from '../api/types'
import { isUpcomingEvent } from './format'

describe('首页演出可见性', () => {
    it('只推荐已发布且尚未结束的演出，不把旧数据带入近期推荐', () => {
        const now = new Date('2026-10-25T12:00:00+08:00').getTime()
        const event = (end: string, status = 1) => ({ eventEndTime: end, status }) as Event
        expect(isUpcomingEvent(event('2026-11-01T21:30:00'), now)).toBe(true)
        expect(isUpcomingEvent(event('2026-10-25T11:59:00'), now)).toBe(false)
        expect(isUpcomingEvent(event('2026-10-25T12:00:00'), now)).toBe(false)
        expect(isUpcomingEvent(event('2026-11-01T21:30:00', 0), now)).toBe(false)
        expect(isUpcomingEvent(event('not-a-date'), now)).toBe(false)
    })
})
