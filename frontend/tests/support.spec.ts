import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

function actionFixture(overrides: Record<string, unknown> = {}) {
    return { id: 'a1', conversationId: 'c', runId: 'r', type: 'PURCHASE', status: 'PENDING', targetId: '42', title: '确认购票申请', eventName: '隔离测试音乐会', sessionName: '周末晚场', sessionTime: '2026-10-30T19:00:00', tierName: 'A区', quantity: 2, unitPrice: 100, totalAmount: 200, orderId: null, requestStatus: null, message: '', expiresAt: '2026-10-30T19:00:00', expiresEpochMillis: Date.now() + 300000, ...overrides }
}
async function actionLogin(page: Page) {
    await page.addInitScript(() => sessionStorage.setItem('youxi-auth', JSON.stringify({ id: '1', token: 'fixture' })))
}

test('客服多会话历史、流式纯文本和断流查询恢复', async ({ page }) => {
    await page.addInitScript(() =>
        sessionStorage.setItem(
            'youxi-auth',
            JSON.stringify({
                id: '1',
                token: 'fixture',
                username: '测试',
                email: 'test@example.invalid',
            }),
        ),
    )
    let conversations = [{ id: '1', title: '历史咨询', activeRunId: null }],
        posts = 0
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        async (route) => {
            const url = new URL(route.request().url()),
                path = url.pathname,
                method = route.request().method()
            let data: unknown = []
            if (path.endsWith('app-status')) data = { customerServiceEnabled: true }
            else if (path.endsWith('/conversations')) {
                if (method === 'POST') {
                    const row = { id: '2', title: '新会话', activeRunId: null }
                    conversations.unshift(row)
                    data = row
                } else data = { records: conversations, hasNext: false }
            } else if (path.endsWith('/conversations/2') && method === 'PATCH') {
                conversations[0]!.title = route.request().postDataJSON().title
            } else if (path.endsWith('/conversations/2') && method === 'DELETE')
                conversations = conversations.filter((c) => c.id !== '2')
            else if (/\/conversations\/[^/]+$/.test(path) && method === 'GET')
                data = conversations.find((row) => path.endsWith('/' + row.id))
            else if (path.endsWith('/messages/stream')) {
                posts++
                return route.fulfill({
                    contentType: 'text/event-stream',
                    body: 'event: run_started\ndata: {"runId":"r1"}\n\nevent: text_delta\ndata: {"runId":"r1","text":"<b>查询中"}\n\n',
                })
            } else if (path.endsWith('/runs/r1'))
                data = { id: 'r1', status: 'SUCCEEDED', reply: '<b>已恢复', conversationId: '2' }
            else if (path.endsWith('/messages'))
                data = {
                    records: path.includes('/1/')
                        ? [{ id: 'm1', role: 'assistant', content: '服务端历史' }]
                        : [],
                    hasNext: false,
                }
            await route.fulfill({ json: { code: 1, data } })
        },
    )
    await page.goto('/support')
    await expect(page.getByText('服务端历史', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '新建会话', exact: true }).click()
    await page.getByLabel('会话名称').fill('我的咨询')
    await page.getByRole('button', { name: '保存名称', exact: true }).click()
    await page.getByLabel('咨询内容').fill('订单在哪')
    await page.getByRole('button', { name: '发送消息' }).click()
    await expect(page.locator('.chat-message.assistant')).toContainText('<b>已恢复')
    expect(posts).toBe(1)
    expect(await page.locator('.message-bubble b').count()).toBe(0)
    await page.getByRole('button', { name: '历史咨询', exact: true }).click()
    await expect(page.getByText('服务端历史', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '我的咨询', exact: true }).click()
    page.once('dialog', (dialog) => dialog.accept())
    await page.getByRole('button', { name: '删除会话', exact: true }).click()
    await expect(page.getByRole('button', { name: '我的咨询', exact: true })).toHaveCount(0)
    await page.setViewportSize({ width: 390, height: 844 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('首事件前断流只在明确恢复时复用原消息标识', async ({ page }) => {
    await page.addInitScript(() =>
        sessionStorage.setItem('youxi-auth', JSON.stringify({ id: '1', token: 'fixture' })),
    )
    const requests: { message: string; clientMessageId: string }[] = []
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        async (route) => {
            const path = new URL(route.request().url()).pathname
            let data: unknown = []
            if (path.endsWith('/app-status')) data = { customerServiceEnabled: true }
            else if (path.endsWith('/conversations'))
                data = { records: [{ id: 'c', title: '会话', activeRunId: null }], hasNext: false }
            else if (path.endsWith('/conversations/c'))
                data = { id: 'c', title: '会话', activeRunId: null }
            else if (path.endsWith('/messages')) data = { records: [], hasNext: false }
            else if (path.endsWith('/stream')) {
                requests.push(route.request().postDataJSON())
                return route.fulfill({
                    contentType: 'text/event-stream',
                    body:
                        requests.length === 1
                            ? ''
                            : 'event: run_started\ndata: {"runId":"r"}\n\nevent: done\ndata: {"id":"r","status":"FAILED","errorMessage":"服务繁忙"}\n\n',
                })
            }
            await route.fulfill({ json: { code: 1, data } })
        },
    )
    await page.goto('/support')
    await page.getByLabel('咨询内容').fill('原问题')
    await page.getByRole('button', { name: '发送消息' }).click()
    await expect(page.getByRole('button', { name: '恢复原请求' })).toBeVisible()
    expect(requests).toHaveLength(1)
    await page.getByRole('button', { name: '恢复原请求' }).click()
    await expect(page.getByRole('alert')).toHaveText('服务繁忙')
    expect(requests).toHaveLength(2)
    expect(requests[1]).toEqual(requests[0])
    await page.getByRole('button', { name: '未收到回复，重新编辑' }).click()
    await expect(page.getByLabel('咨询内容')).toHaveValue('原问题')
    await expect(page.locator('.chat-message.user')).toHaveCount(1)
})

test('刷新恢复运行，历史和会话可翻页，状态失败不开放聊天', async ({ page }) => {
    await page.addInitScript(() =>
        sessionStorage.setItem('youxi-auth', JSON.stringify({ id: '1', token: 'fixture' })),
    )
    let available = false,
        posts = 0
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        async (route) => {
            const url = new URL(route.request().url()),
                path = url.pathname
            let data: unknown = []
            if (path.endsWith('/app-status')) {
                if (!available) return route.fulfill({ json: { code: 503, msg: '状态查询失败' } })
                data = { customerServiceEnabled: true }
            } else if (path.endsWith('/conversations'))
                data = {
                    records: [
                        {
                            id: url.searchParams.get('page') === '2' ? 'older' : 'c',
                            title: url.searchParams.get('page') === '2' ? '更早会话' : '运行会话',
                            activeRunId: url.searchParams.get('page') === '2' ? null : 'r',
                        },
                    ],
                    hasNext: url.searchParams.get('page') !== '2',
                }
            else if (/\/conversations\/[^/]+$/.test(path))
                data = {
                    id: path.split('/').pop(),
                    title: path.endsWith('/older') ? '更早会话' : '运行会话',
                    activeRunId: path.endsWith('/older') ? null : 'r',
                }
            else if (path.endsWith('/messages'))
                data = {
                    records: [
                        {
                            id: url.searchParams.get('page') === '2' ? '1' : '2',
                            role: 'user',
                            content: url.searchParams.get('page') === '2' ? '更早问题' : '持久问题',
                        },
                    ],
                    hasNext: url.searchParams.get('page') !== '2',
                }
            else if (path.endsWith('/runs/r'))
                data = { id: 'r', status: 'SUCCEEDED', reply: '刷新恢复完成' }
            else if (path.endsWith('/stream')) posts++
            await route.fulfill({ json: { code: 1, data } })
        },
    )
    await page.goto('/support')
    await expect(page.getByRole('alert')).toHaveText('状态查询失败')
    await expect(page.getByLabel('咨询内容')).toHaveCount(0)
    available = true
    await page.getByRole('button', { name: '重试服务状态' }).click()
    await expect(page.getByText('刷新恢复完成', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '加载更早消息' }).click()
    await expect(page.locator('.chat-message.user').first()).toHaveText('更早问题')
    await page.getByRole('button', { name: '加载更多会话' }).click()
    await page.getByRole('button', { name: '更早会话', exact: true }).click()
    await expect(page.getByLabel('会话名称')).toHaveValue('更早会话')
    expect(posts).toBe(0)
})

test('切换会话后迟到清空响应不会污染新历史', async ({ page }) => {
    await page.addInitScript(() =>
        sessionStorage.setItem('youxi-auth', JSON.stringify({ id: '1', token: 'fixture' })),
    )
    let releaseClear: (() => void) | undefined
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        async (route) => {
            const path = new URL(route.request().url()).pathname
            let data: unknown = []
            if (path.endsWith('/app-status')) data = { customerServiceEnabled: true }
            else if (path.endsWith('/conversations'))
                data = {
                    records: [
                        { id: 'a', title: '会话甲', activeRunId: null },
                        { id: 'b', title: '会话乙', activeRunId: null },
                    ],
                    hasNext: false,
                }
            else if (/\/conversations\/[ab]$/.test(path))
                data = {
                    id: path.split('/').pop(),
                    title: path.endsWith('/a') ? '会话甲' : '会话乙',
                    activeRunId: null,
                }
            else if (path.endsWith('/messages') && route.request().method() === 'DELETE') {
                await new Promise<void>((resolve) => {
                    releaseClear = resolve
                })
            } else if (path.endsWith('/messages'))
                data = {
                    records: [
                        {
                            id: path.includes('/b/') ? 'mb' : 'ma',
                            role: 'assistant',
                            content: path.includes('/b/') ? '乙历史' : '甲历史',
                        },
                    ],
                    hasNext: false,
                }
            await route.fulfill({ json: { code: 1, data } })
        },
    )
    await page.goto('/support')
    await expect(page.getByText('甲历史', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '清空会话', exact: true }).click()
    await page.getByRole('button', { name: '确认清空', exact: true }).click()
    await expect.poll(() => !!releaseClear).toBe(true)
    await page.getByRole('button', { name: '保留会话', exact: true }).click()
    await page.getByRole('button', { name: '会话乙', exact: true }).click()
    await expect(page.getByText('乙历史', { exact: true })).toBeVisible()
    releaseClear!()
    await expect(page.getByText('乙历史', { exact: true })).toBeVisible()
    await expect(page.getByText('本次会话已清空', { exact: true })).toHaveCount(0)
})

test('后端已受理但首事件未到，切回会话读取最新运行而不重复提交', async ({ page }) => {
    await page.addInitScript(() =>
        sessionStorage.setItem('youxi-auth', JSON.stringify({ id: '1', token: 'fixture' })),
    )
    let posts = 0,
        runQueries = 0,
        accepted = false
    let releaseStream: (() => void) | undefined
    const rows = [
        { id: 'a', title: '会话甲', activeRunId: null },
        { id: 'b', title: '会话乙', activeRunId: null },
    ]
    const reads: string[] = []
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        async (route) => {
            const path = new URL(route.request().url()).pathname
            let data: unknown = []
            if (path.endsWith('/app-status')) data = { customerServiceEnabled: true }
            else if (path.endsWith('/conversations')) data = { records: rows, hasNext: false }
            else if (/\/conversations\/[ab]$/.test(path)) {
                reads.push('metadata')
                const row = rows.find((row) => path.endsWith('/' + row.id))!
                data = { ...row, activeRunId: row.id === 'a' && accepted ? 'accepted-run' : null }
            } else if (path.endsWith('/messages')) {
                reads.push('messages')
                data = {
                    records:
                        accepted && path.includes('/a/')
                            ? [{ id: 'user-accepted', role: 'user', content: '原问题' }]
                            : [],
                    hasNext: false,
                }
            } else if (path.endsWith('/stream')) {
                posts++
                accepted = true
                await new Promise<void>((resolve) => {
                    releaseStream = resolve
                })
                return route.fulfill({ contentType: 'text/event-stream', body: '' }).catch(() => {})
            } else if (path.endsWith('/runs/accepted-run')) {
                runQueries++
                data = {
                    id: 'accepted-run',
                    status: 'SUCCEEDED',
                    reply: '原运行已恢复',
                    conversationId: 'a',
                }
            }
            await route.fulfill({ json: { code: 1, data } })
        },
    )
    await page.goto('/support')
    await page.getByLabel('咨询内容').fill('原问题')
    await page.getByRole('button', { name: '发送消息' }).click()
    await expect.poll(() => accepted).toBe(true)
    await page.getByRole('button', { name: '会话乙', exact: true }).click()
    await expect(page.getByLabel('会话名称')).toHaveValue('会话乙')
    await expect.poll(() => reads).toEqual(['metadata', 'messages', 'metadata', 'messages'])
    await page.getByRole('button', { name: '会话甲', exact: true }).click()
    await expect(page.getByText('原运行已恢复', { exact: true })).toBeVisible()
    expect(posts).toBe(1)
    expect(runQueries).toBe(1)
    releaseStream!()
    expect(reads).toEqual(['metadata', 'messages', 'metadata', 'messages', 'metadata', 'messages'])
})

test('延迟创建和详情加载期间禁用输入，就绪后草稿不会被吞掉', async ({ page }) => {
    await page.addInitScript(() =>
        sessionStorage.setItem('youxi-auth', JSON.stringify({ id: '1', token: 'fixture' })),
    )
    let releaseCreate: (() => void) | undefined, releaseDetail: (() => void) | undefined
    const row = { id: 'created', title: '新会话', activeRunId: null }
    let created = false,
        creates = 0
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        async (route) => {
            const path = new URL(route.request().url()).pathname
            let data: unknown = []
            if (path.endsWith('/app-status')) data = { customerServiceEnabled: true }
            else if (path.endsWith('/conversations')) {
                if (route.request().method() === 'POST') {
                    creates++
                    await new Promise<void>((resolve) => {
                        releaseCreate = resolve
                    })
                    created = true
                    data = row
                } else data = { records: created ? [row] : [], hasNext: false }
            } else if (path.endsWith('/conversations/created')) {
                await new Promise<void>((resolve) => {
                    releaseDetail = resolve
                })
                data = row
            } else if (path.endsWith('/messages')) data = { records: [], hasNext: false }
            await route.fulfill({ json: { code: 1, data } })
        },
    )
    await page.goto('/support')
    await page.getByRole('button', { name: '新建会话', exact: true }).click()
    await expect.poll(() => !!releaseCreate).toBe(true)
    await expect(page.getByRole('button', { name: '新建会话', exact: true })).toBeDisabled()
    await expect(page.getByLabel('咨询内容')).toBeDisabled()
    releaseCreate!()
    await expect.poll(() => !!releaseDetail).toBe(true)
    await expect(page.getByLabel('会话名称')).toBeDisabled()
    await expect(page.getByLabel('咨询内容')).toBeDisabled()
    releaseDetail!()
    await expect(page.getByLabel('咨询内容')).toBeEnabled()
    await page.getByLabel('咨询内容').fill('准备好的问题')
    await expect(page.getByRole('button', { name: '发送消息' })).toBeEnabled()
    await expect(page.getByLabel('咨询内容')).toHaveValue('准备好的问题')
    expect(creates).toBe(1)
})

const citation = { entryId: 'KB-ORDER-001', documentId: 'order', title: '付款期限', version: 2, reviewedAt: '2026-10-27', url: 'https://evil.invalid/' }
test('检索来源与done及历史一致，恶意URL和回复URL保持纯文本', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-auth', JSON.stringify({ id: '1', token: 'fixture' })))
    let completed = false
    const reply = '规则回复 https://evil.invalid/ <a href="javascript:alert(1)">链接</a>'
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const path = new URL(route.request().url()).pathname
        let data: unknown = []
        if (path.endsWith('/app-status')) data = { customerServiceEnabled: true }
        else if (path.endsWith('/conversations')) data = { records: [{ id: 'c', title: '咨询', activeRunId: null }], hasNext: false }
        else if (path.endsWith('/conversations/c')) data = { id: 'c', title: '咨询', activeRunId: null }
        else if (path.endsWith('/messages')) data = { records: completed ? [{ id: 'm', role: 'assistant', content: reply, citations: [citation] }] : [], hasNext: false }
        else if (path.endsWith('/stream')) {
            completed = true
            const events = [
                ['run_started', { runId: 'r' }],
                ['knowledge_sources', { runId: 'wrong', citations: [{ ...citation, title: '串线来源' }] }],
                ['knowledge_sources', { runId: 'r', citations: [{ ...citation, title: '分片来源', version: 1 }] }],
                ['text_delta', { runId: 'r', text: reply }],
                ['done', { id: 'r', status: 'SUCCEEDED', reply, citations: [citation] }],
            ]
            return route.fulfill({ contentType: 'text/event-stream', body: events.map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join('') })
        }
        await route.fulfill({ json: { code: 1, data } })
    })
    await page.goto('/support')
    await expect(page.locator('.knowledge-sources')).toHaveCount(0)
    await page.getByLabel('咨询内容').fill('付款期限')
    await page.getByRole('button', { name: '发送消息' }).click()
    const link = page.locator('.knowledge-sources a')
    await expect(link).toHaveText('付款期限 · 版本 2')
    await expect(link).toHaveAttribute('href', '/support/knowledge/KB-ORDER-001?version=2')
    await expect(page.locator('.message-bubble a')).toHaveCount(0)
    await expect(page.getByText('串线来源')).toHaveCount(0)
    await page.reload()
    await expect(link).toHaveText('付款期限 · 版本 2')
    await page.setViewportSize({ width: 390, height: 844 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('规则页纯文本、历史版本与停用404，维护关闭仍能只读', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-auth', JSON.stringify({ id: '1', token: 'fixture' })))
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const url = new URL(route.request().url())
        if (url.pathname.includes('/knowledge/')) {
            if (url.searchParams.get('version') === '9') return route.fulfill({ json: { code: 404, msg: '规则不存在或已停用' } })
            return route.fulfill({ json: { code: 1, data: { ...citation, documentTitle: '订单规则', question: '付款期限', answer: '<b>刷新不会延长期限</b>', liveCheck: '具体订单截止时间需实时查询', current: url.searchParams.get('version') !== '1' } } })
        }
        await route.fulfill({ json: { code: 1, data: { customerServiceEnabled: false } } })
    })
    await page.goto('/support/knowledge/KB-ORDER-001?version=1')
    await expect(page.getByText('历史版本，仅供追溯，不可作为现行规则。')).toBeVisible()
    await expect(page.locator('.knowledge-text').first()).toHaveText('<b>刷新不会延长期限</b>')
    await expect(page.locator('.knowledge-text b')).toHaveCount(0)
    await expect(page.getByRole('heading', { name: '需实时核验' })).toBeVisible()
    await expect(page.getByRole('button', { name: /下单|付款|购买/ })).toHaveCount(0)
    await page.setViewportSize({ width: 390, height: 844 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.goto('/support/knowledge/KB-ORDER-001?version=2')
    await expect(page.getByRole('heading', { name: '付款期限' })).toBeVisible()
    await expect(page.getByText('历史版本，仅供追溯，不可作为现行规则。')).toHaveCount(0)
    await page.goto('/support/knowledge/KB-ORDER-001?version=9')
    await expect(page.getByRole('alert')).toHaveText('规则不存在或已停用')
})

test('规则切路由隔离迟到响应，无检索的done不保留分片假来源', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-auth', JSON.stringify({ id: '1', token: 'fixture' })))
    let release: (() => void) | undefined
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const path = new URL(route.request().url()).pathname
        let data: unknown = []
        if (path.endsWith('/knowledge/KB-ORDER-001')) {
            await new Promise<void>((resolve) => { release = resolve })
            data = { ...citation, documentTitle: '旧规则', question: '迟到旧规则', answer: '旧正文', liveCheck: '旧边界', current: true }
        } else if (path.endsWith('/knowledge/KB-ORDER-002')) {
            data = { ...citation, entryId: 'KB-ORDER-002', documentTitle: '新规则', question: '当前新规则', answer: '新正文', liveCheck: '新边界', current: true }
        } else if (path.endsWith('/app-status')) data = { customerServiceEnabled: true }
        else if (path.endsWith('/conversations')) data = { records: [{ id: 'c', title: '咨询', activeRunId: null }], hasNext: false }
        else if (path.endsWith('/conversations/c')) data = { id: 'c', title: '咨询', activeRunId: null }
        else if (path.endsWith('/messages')) data = { records: [], hasNext: false }
        else if (path.endsWith('/stream')) return route.fulfill({ contentType: 'text/event-stream', body: [
            ['run_started', { runId: 'r' }],
            ['knowledge_sources', { runId: 'r', citations: [citation] }],
            ['done', { id: 'r', status: 'SUCCEEDED', reply: '未检索到规则' }],
        ].map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join('') })
        await route.fulfill({ json: { code: 1, data } }).catch(() => {})
    })
    await page.goto('/support/knowledge/KB-ORDER-001?version=2')
    await expect.poll(() => !!release).toBe(true)
    await page.evaluate(() => {
        history.pushState({}, '', '/support/knowledge/KB-ORDER-002?version=2')
        dispatchEvent(new PopStateEvent('popstate'))
    })
    await expect(page.getByRole('heading', { name: '当前新规则' })).toBeVisible()
    release!()
    await expect(page.getByRole('heading', { name: '当前新规则' })).toBeVisible()
    await expect(page.getByText('迟到旧规则')).toHaveCount(0)
    await page.getByRole('link', { name: '返回客服', exact: true }).click()
    await page.getByLabel('咨询内容').fill('未知问题')
    await page.getByRole('button', { name: '发送消息' }).click()
    await expect(page.locator('.message-bubble').last()).toHaveText('未检索到规则')
    await expect(page.locator('.knowledge-sources')).toHaveCount(0)
})

test('交易草稿等来源成功并明确确认，重复点击只发一次，未知结果只查询', async ({ page }) => {
    await actionLogin(page)
    let action = actionFixture(), finished = false, confirms = 0, queries = 0
    let releaseConfirm: (() => void) | undefined, releaseOldList: (() => void) | undefined
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const path = new URL(route.request().url()).pathname
        let data: unknown = []
        if (path.endsWith('/app-status')) data = { customerServiceEnabled: true, tradingEnabled: true }
        else if (path.endsWith('/conversations')) data = { records: [{ id: 'c', title: '咨询', activeRunId: null }], hasNext: false }
        else if (path.endsWith('/conversations/c')) data = { id: 'c', title: '咨询', activeRunId: null }
        else if (path.endsWith('/messages')) data = { records: [], hasNext: false }
        else if (path.endsWith('/conversations/c/actions')) {
            data = finished ? [{ ...action }] : []
            if (finished) await new Promise<void>((resolve) => { releaseOldList = resolve })
        }
        else if (path.endsWith('/stream')) return route.fulfill({ contentType: 'text/event-stream', body: [
            ['run_started', { runId: 'r' }], ['action_proposed', { runId: 'wrong', action: { ...action, id: 'wrong' } }],
            ['action_proposed', { runId: 'r', action }], ['text_delta', { runId: 'r', text: '请核对卡片，https://evil.invalid/confirm 不会成为执行入口。' }],
        ].map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join('') })
        else if (path.endsWith('/runs/r')) data = { id: 'r', status: finished ? 'SUCCEEDED' : 'RUNNING', reply: '请核对卡片', deadlineEpochMillis: Date.now() + 60000 }
        else if (path.endsWith('/actions/a1/confirm')) {
            confirms++
            expect(route.request().postData()).toBeNull()
            await new Promise<void>((resolve) => { releaseConfirm = resolve })
            action = { ...action, status: 'UNKNOWN', message: '申请结果暂未确定，请查询原结果' }
            data = action
        } else if (path.endsWith('/actions/a1')) {
            queries++
            action = { ...action, status: 'SUCCEEDED', orderId: '123' as any, requestStatus: 'CREATED' as any, message: '已生成订单，请前往订单付款' }
            data = action
        }
        await route.fulfill({ json: { code: 1, data } })
    })
    await page.goto('/support')
    await page.getByLabel('咨询内容').fill('帮我买42票档两张')
    await page.getByRole('button', { name: '发送消息' }).click()
    const card = page.getByRole('region', { name: '购票确认卡片' })
    await expect(card).toHaveCount(1)
    await expect(card.getByRole('button', { name: '核对购票申请' })).toBeDisabled()
    expect(confirms).toBe(0)
    finished = true
    await expect(card.getByRole('button', { name: '核对购票申请' })).toBeEnabled()
    await expect.poll(() => !!releaseOldList).toBe(true)
    await page.setViewportSize({ width: 390, height: 844 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await card.screenshot({ path: '../output/agent-stage4/purchase-card-mobile.png' })
    await card.getByRole('button', { name: '核对购票申请' }).click()
    await expect(page.getByRole('dialog')).toContainText('2 张，申请金额 ¥200.00')
    expect(confirms).toBe(0)
    await page.getByRole('button', { name: '确认提交购票申请', exact: true }).click()
    await expect.poll(() => confirms).toBe(1)
    await expect(card.getByRole('button', { name: '核对购票申请' })).toBeDisabled()
    releaseConfirm!()
    await expect(card).toContainText('结果待核实')
    const oldListReceived = page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/conversations/c/actions'))
    releaseOldList!()
    await oldListReceived
    await expect(card).toContainText('结果待核实')
    await expect(card.getByRole('button', { name: '核对购票申请' })).toHaveCount(0)
    await expect(card.getByRole('link', { name: '查看这笔订单' })).toHaveCount(0)
    await card.getByRole('button', { name: '查询原结果' }).click()
    await expect(card.getByRole('link', { name: '查看这笔订单' })).toHaveAttribute('href', '/account/orders/123')
    expect(confirms).toBe(1)
    expect(queries).toBe(1)
})

test('历史卡片刷新恢复、过期不能确认、放弃及清空不发交易请求', async ({ page }) => {
    await actionLogin(page)
    let actions = [actionFixture({ expiresEpochMillis: Date.now() - 1000 })], cleared = false, writes = 0
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const path = new URL(route.request().url()).pathname, method = route.request().method()
        let data: unknown = []
        if (path.endsWith('/app-status')) data = { customerServiceEnabled: true, tradingEnabled: true }
        else if (path.endsWith('/conversations')) data = { records: [{ id: 'c', title: '咨询', activeRunId: null }], hasNext: false }
        else if (path.endsWith('/conversations/c')) data = { id: 'c', title: '咨询', activeRunId: null }
        else if (path.endsWith('/messages') && method === 'DELETE') { cleared = true; actions = [] }
        else if (path.endsWith('/messages')) data = { records: cleared ? [] : [{ id: 'm', runId: 'r', role: 'assistant', content: '请确认购票' }], hasNext: false }
        else if (path.endsWith('/actions')) data = actions
        else if (path.endsWith('/dismiss')) { actions[0]!.status = 'DISMISSED'; data = actions[0] }
        else if (path.endsWith('/confirm')) writes++
        await route.fulfill({ json: { code: 1, data } })
    })
    await page.goto('/support')
    const card = page.getByRole('region', { name: '购票确认卡片' })
    await expect(card).toContainText('草稿已到期')
    await expect(card.getByRole('button', { name: '核对购票申请' })).toBeDisabled()
    await page.reload()
    await expect(card).toHaveCount(1)
    await card.getByRole('button', { name: '放弃草稿' }).click()
    await expect(card).toContainText('已放弃')
    await page.getByRole('button', { name: '清空会话', exact: true }).click()
    await page.getByRole('button', { name: '确认清空', exact: true }).click()
    await expect(card).toHaveCount(0)
    expect(writes).toBe(0)
})

test('取消卡片迟到确认不会污染另一会话，交易维护不能确认', async ({ page }) => {
    await actionLogin(page)
    const action = actionFixture({ type: 'CANCEL', title: '取消本人订单', orderId: '123' })
    let release: (() => void) | undefined, trading = true, confirms = 0
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const path = new URL(route.request().url()).pathname
        let data: unknown = []
        if (path.endsWith('/app-status')) data = { customerServiceEnabled: true, tradingEnabled: trading }
        else if (path.endsWith('/conversations')) data = { records: [{ id: 'c', title: '会话甲', activeRunId: null }, { id: 'b', title: '会话乙', activeRunId: null }], hasNext: false }
        else if (/\/conversations\/[cb]$/.test(path)) data = { id: path.endsWith('/c') ? 'c' : 'b', title: path.endsWith('/c') ? '会话甲' : '会话乙', activeRunId: null }
        else if (path.endsWith('/messages')) data = { records: [{ id: 'm', runId: path.includes('/c/') ? 'r' : 'r2', role: 'assistant', content: path.includes('/c/') ? '请确认取消' : '另一会话内容' }], hasNext: false }
        else if (path.endsWith('/actions')) data = path.includes('/c/') ? [action] : []
        else if (path.endsWith('/confirm')) {
            confirms++
            await new Promise<void>((resolve) => { release = resolve })
            data = { ...action, status: 'SUCCEEDED', message: '迟到取消结果' }
        }
        await route.fulfill({ json: { code: 1, data } }).catch(() => {})
    })
    await page.goto('/support')
    await page.getByRole('button', { name: '核对取消订单' }).click()
    await page.getByRole('button', { name: '确认取消此订单' }).click()
    await expect.poll(() => confirms).toBe(1)
    await page.getByRole('button', { name: '会话乙', exact: true }).click()
    await expect(page.getByText('另一会话内容', { exact: true })).toBeVisible()
    release!()
    await expect(page.getByText('迟到取消结果')).toHaveCount(0)
    await expect(page.locator('.agent-action-card')).toHaveCount(0)
    trading = false
    await page.reload()
    await expect(page.getByRole('button', { name: '核对取消订单' })).toBeDisabled()
    await expect(page.locator('.agent-action-card')).toContainText('当前功能维护中')
    expect(confirms).toBe(1)
})

test('失败来源不能确认，确认断网保留未知且仅查询原卡片', async ({ page }) => {
    await actionLogin(page)
    let failed = true, confirms = 0, queries = 0
    const action = actionFixture()
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const path = new URL(route.request().url()).pathname
        let data: unknown = []
        if (path.endsWith('/app-status')) data = { customerServiceEnabled: true, tradingEnabled: true }
        else if (path.endsWith('/conversations')) data = { records: [{ id: 'c', title: '咨询', activeRunId: null }], hasNext: false }
        else if (path.endsWith('/conversations/c')) data = { id: 'c', title: '咨询', activeRunId: null }
        else if (path.endsWith('/messages')) data = { records: [], hasNext: false }
        else if (path.endsWith('/actions')) data = [action]
        else if (path.endsWith('/stream')) return route.fulfill({ contentType: 'text/event-stream', body: [
            ['run_started', { runId: 'r' }], ['action_proposed', { runId: 'r', action }],
            ['done', { id: 'r', status: failed ? 'FAILED' : 'SUCCEEDED', reply: '操作草稿仅供核对', errorMessage: failed ? '回复未完成' : null }],
        ].map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join('') })
        else if (path.endsWith('/confirm')) { confirms++; return route.abort('failed') }
        else if (path.endsWith('/actions/a1')) { queries++; data = { ...action, status: 'UNKNOWN', message: '仍待核实' } }
        await route.fulfill({ json: { code: 1, data } })
    })
    await page.goto('/support')
    await page.getByLabel('咨询内容').fill('提出购票草稿')
    await page.getByRole('button', { name: '发送消息' }).click()
    const card = page.getByRole('region', { name: '购票确认卡片' })
    await expect(card.getByRole('button', { name: '核对购票申请' })).toBeDisabled()
    await expect(card).toContainText('来源回复尚未核实成功')
    expect(confirms).toBe(0)
    failed = false
    await page.getByLabel('咨询内容').fill('重新提出明确草稿')
    await page.getByRole('button', { name: '发送消息' }).click()
    await expect(card.getByRole('button', { name: '核对购票申请' })).toBeEnabled()
    await card.getByRole('button', { name: '核对购票申请' }).click()
    await page.getByRole('button', { name: '确认提交购票申请', exact: true }).click()
    await expect(card).toContainText('确认响应未收到')
    await expect(card.getByRole('button', { name: '核对购票申请' })).toHaveCount(0)
    await card.getByRole('button', { name: '查询原结果' }).click()
    await expect(card).toContainText('仍待核实')
    expect(confirms).toBe(1)
    expect(queries).toBe(1)
})
