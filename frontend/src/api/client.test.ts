import { describe, expect, it } from 'vitest'
import { decodeResponse, unwrapResult } from './client'

describe('API response contract', () => {
    it('preserves large numeric IDs without turning counts and prices into strings', () => {
        const result = decodeResponse(
            '{"code":1,"data":{"id":9223372036854775807,"eventId":12,"quantity":3,"minPrice":280.50}}',
        )
        expect(result.data.id).toBe('9223372036854775807')
        expect(result.data.eventId).toBe('12')
        expect(result.data.quantity).toBe(3)
        expect(result.data.minPrice).toBe(280.5)
    })
    it('does not mistake business errors or registration challenge for success', () => {
        expect(() => unwrapResult({ code: 0, msg: '演出已下架' })).toThrow('演出已下架')
        expect(() => unwrapResult({ code: 200, msg: '新用户，请验证邮箱验证码' })).toThrow('新用户')
        expect(unwrapResult({ code: 1, data: [] })).toEqual([])
    })
})
