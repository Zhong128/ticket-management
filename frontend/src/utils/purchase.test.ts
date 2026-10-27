import { describe, expect, it } from 'vitest'
import {
    getOrCreateIntent,
    safeRedirect,
    reservationView,
    orderLabel,
    remainingSeconds,
} from './purchase'

describe('交易前端约束', () => {
    it('提交结果未知时同一购买复用原key，不会用新key重复抢票', () => {
        const first = getOrCreateIntent(null, {
            userId: '1',
            eventId: '2',
            sessionId: '3',
            tierId: '4',
            quantity: 2,
        })
        expect(getOrCreateIntent(first, { ...first })).toBe(first)
        expect(() => getOrCreateIntent(first, { ...first, tierId: '5' })).toThrow('已有购票申请')
    })
    it('拦截外站与伪协议回跳，只保留本站路径', () => {
        for (const path of [
            'https://evil.invalid',
            '//evil.invalid',
            '/\\evil.invalid',
            '/login',
            '/%2f%2fevil.invalid',
        ])
            expect(safeRedirect(path)).toBe('/')
        expect(safeRedirect('/checkout?eventId=2&tierId=4')).toBe('/checkout?eventId=2&tierId=4')
    })
    it('受理中和取消确认中不能显示成已完成，过期倒计时不为负', () => {
        expect(reservationView({ requestStatus: 'RESERVED', orderId: null })).toBe('processing')
        expect(reservationView({ requestStatus: 'HELD', orderId: '77' })).toBe('created')
        expect(reservationView({ requestStatus: 'RELEASED' })).toBe('released')
        expect(reservationView({ requestStatus: 'UNKNOWN' })).toBe('unknown')
        expect(orderLabel(4)).toBe('取消确认中')
        expect(
            remainingSeconds('2026-10-25 12:00:00', Date.parse('2026-10-25T12:00:01+08:00')),
        ).toBe(0)
    })
})
