import { afterEach, describe, expect, it, vi } from 'vitest'
import { readEventStream, streamMessage, runPollingDeadline, normalizeCitations, citationPath, normalizeActions, actionOrderPath, canConfirmAction } from './support'

afterEach(() => vi.unstubAllGlobals())

describe('可信交易卡片', () => {
    const action = { id: 'a1', conversationId: 'c', runId: 'r', type: 'PURCHASE', status: 'PENDING', targetId: '42', title: '购票申请', eventName: '音乐会', sessionName: '晚场', sessionTime: '2026-10-30T19:00:00', tierName: 'A区', quantity: 2, unitPrice: 100, totalAmount: 200, orderId: null, requestStatus: null, message: '', expiresAt: '2026-10-28T12:00:00', expiresEpochMillis: 2000 }
    it('只接受完整服务端卡片，不从文本或任意链接生成操作', () => {
        expect(normalizeActions('请确认购票')).toEqual([])
        expect(normalizeActions([action, { ...action, id: '../other' }, { ...action, type: 'PAY' }, { ...action, totalAmount: -1 }])).toEqual([action])
        expect(actionOrderPath({ ...action, orderId: '123' } as any)).toBe('/account/orders/123')
        expect(actionOrderPath({ ...action, orderId: '//evil.invalid' } as any)).toBeNull()
    })
    it('只有成功运行、未到期、待确认且功能可用时才可确认', () => {
        const card = normalizeActions([action])[0]!
        expect(canConfirmAction(card, true, true, 1000)).toBe(true)
        expect(canConfirmAction(card, false, true, 1000)).toBe(false)
        expect(canConfirmAction(card, true, false, 1000)).toBe(false)
        expect(canConfirmAction(card, true, true, 2000)).toBe(false)
        expect(canConfirmAction({ ...card, status: 'UNKNOWN' }, true, true, 1000)).toBe(false)
    })
})

describe('客服事件流', () => {
    it('恢复时优先使用服务端绝对时间，无效ISO退回本地上限', () => {
        const limit = 200000
        expect(runPollingDeadline({ deadlineEpochMillis: 100000, deadlineAt: 'invalid' }, limit)).toBe(110000)
        expect(runPollingDeadline({ deadlineAt: 'invalid' }, limit)).toBe(limit)
        expect(runPollingDeadline({ deadlineAt: '1970-01-01T00:01:00Z' }, limit)).toBe(70000)
        expect(runPollingDeadline({ deadlineEpochMillis: 300000 }, limit)).toBe(limit)
    })
    it('跨 UTF8 分片、CRLF 和多 data 行仍保留纯文本', async () => {
        const bytes = new TextEncoder().encode(
            'event: text_delta\r\ndata: {"text":\r\ndata: "你好<b>"}\r\n\r\nevent: done\ndata: {"status":"SUCCEEDED"}\n\n',
        )
        const stream = new ReadableStream<Uint8Array>({
            start(controller) {
                for (const byte of bytes) controller.enqueue(new Uint8Array([byte]))
                controller.close()
            },
        })
        const events: unknown[] = []
        await readEventStream(stream, (event, data) => events.push([event, data]))
        expect(events).toEqual([
            ['text_delta', { text: '你好<b>' }],
            ['done', { status: 'SUCCEEDED' }],
        ])
    })
    it('缺少终态时允许调用方识别断流', async () => {
        const events: string[] = []
        await readEventStream(
            new ReadableStream({
                start(controller) {
                    controller.close()
                },
            }),
            (event) => events.push(event),
        )
        expect(events).toEqual([])
    })
    for (const switched of [false, true]) {
        it(`流开始前 JSON 401 正确处理账号切换 switched=${switched}`, async () => {
            let session = JSON.stringify({ id: '1', token: 'old-token' })
            const expire = vi.fn()
            vi.stubGlobal('sessionStorage', {
                getItem: () => session,
                removeItem: () => { session = '' },
            })
            vi.stubGlobal('window', { dispatchEvent: expire })
            vi.stubGlobal('fetch', async (_url: string, options: RequestInit) => {
                expect(options.headers).toMatchObject({ 'ticket-token': 'old-token' })
                if (switched) session = JSON.stringify({ id: '2', token: 'new-token' })
                return new Response(JSON.stringify({ code: 401, msg: '登录过期' }), {
                    headers: { 'Content-Type': 'application/json' },
                })
            })
            await expect(streamMessage('c', '问题', 'request-1', new AbortController().signal, () => {
                throw new Error('JSON 错误不应分派为流事件')
            })).rejects.toThrow('登录已失效')
            if (switched) {
                expect(JSON.parse(session).token).toBe('new-token')
                expect(expire).not.toHaveBeenCalled()
            } else {
                expect(session).toBe('')
                expect(expire).toHaveBeenCalledOnce()
            }
        })
    }
})

 describe('客服规则来源', () => {
    it('缺省为空且只按安全条目和正整数版本构造站内链接', () => {
        expect(normalizeCitations(undefined)).toEqual([])
        const source = { entryId: 'KB-ORDER-001', documentId: 'orders', title: '付款期限', version: 2, reviewedAt: '2026-10-27', url: 'javascript:alert(1)' }
        expect(citationPath(source)).toBe('/support/knowledge/KB-ORDER-001?version=2')
        expect(normalizeCitations([source, { ...source, entryId: '../evil' }, { ...source, version: 1.5 }])).toEqual([source])
    })
})
