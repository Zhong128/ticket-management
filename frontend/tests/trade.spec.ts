import { test, expect, type Page } from '@playwright/test'

const user = {
    id: '9001',
    username: '体验用户',
    email: 'demo@example.invalid',
    token: 'frontend-fixture-token',
}
const order = {
    id: '9101',
    orderNo: 'DEMO9101',
    eventId: '9201',
    eventName: '周末音乐现场',
    eventImage: '',
    sessionId: '9301',
    sessionName: '晚场',
    sessionTime: '2026-11-08T19:30:00',
    venueName: '城市音乐厅',
    tierId: '9401',
    tierName: '普通票',
    tierPrice: 180,
    quantity: 2,
    totalAmount: 360,
    orderStatus: 0,
    expireTime: '2099-11-08T19:00:00',
    createTime: '2026-10-25T20:00:00',
}
const checkout = '/checkout?eventId=9201&sessionId=9301&tierId=9401&quantity=2'
const ok = (data: unknown) => ({ code: 1, data })
async function setup(page: Page, signedIn = true) {
    if (signedIn)
        await page.addInitScript(
            (info) => sessionStorage.setItem('youxi-auth', JSON.stringify(info)),
            user,
        )
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        async (route) => {
            const path = new URL(route.request().url()).pathname
            let data: unknown = []
            if (path === '/api/user/profile') data = user
            if (path === '/api/public/events/9201')
                data = {
                    id: '9201',
                    name: order.eventName,
                    venue: order.venueName,
                    status: 1,
                    saleStartTime: '2020-01-01T00:00:00',
                    saleEndTime: '2099-01-01T00:00:00',
                    eventStartTime: order.sessionTime,
                    eventEndTime: order.sessionTime,
                }
            if (path.includes('/sessions'))
                data = [
                    { id: '9301', sessionTime: order.sessionTime, sessionName: '晚场', status: 1 },
                ]
            if (path.includes('/ticket-tiers/'))
                data = {
                    records: [
                        { id: '9401', tierName: '普通票', currentPrice: 180, availableStock: 20 },
                    ],
                    page: 1,
                    total: 1,
                    totalPages: 1,
                }
            if (path === '/api/user/orders/page')
                data = { records: [order], page: 1, total: 1, totalPages: 1 }
            if (path === '/api/user/orders/9101') data = order
            await route.fulfill({ json: ok(data) })
        },
    )
}

test('注册验证携带选定密码，登录后回到原确认页且不自动下单', async ({ page }) => {
    await setup(page, false)
    let submissions = 0
    await page.route('**/api/user/seckill', async (route) => {
        submissions++
        await route.fulfill({ json: ok({}) })
    })
    await page.route('**/api/auth/user/login-or-register', (route) =>
        route.fulfill({ json: { code: 200, msg: '新用户，请验证邮箱验证码' } }),
    )
    await page.route('**/api/auth/user/verify-registration', async (route) => {
        expect(route.request().postDataJSON()).toEqual({
            email: user.email,
            password: 'demo-pass-123',
            code: '123456',
        })
        await route.fulfill({ json: ok(user) })
    })
    await page.goto(checkout)
    await expect(page).toHaveURL(/login/)
    await page.getByLabel('邮箱', { exact: true }).fill(user.email)
    await page.getByLabel('密码', { exact: true }).fill('demo-pass-123')
    await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
    await expect(page.getByRole('heading', { name: '验证你的邮箱' })).toBeVisible()
    await page.getByLabel('邮箱验证码').fill('123456')
    await page.getByRole('button', { name: '验证并登录' }).click()
    await expect(page.getByRole('heading', { name: '确认购票信息' })).toBeVisible()
    expect(submissions).toBe(0)
    expect(
        await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage })),
    ).not.toContain('demo-pass-123')
})

test('提交响应丢失后刷新恢复原申请，手动重试仍使用相同编号', async ({ page }) => {
    await setup(page)
    const keys: string[] = []
    let recovered = false
    await page.route('**/api/user/seckill', async (route) => {
        keys.push(route.request().postDataJSON().requestKey)
        if (keys.length === 1) await route.abort('failed')
        else {
            recovered = true
            await route.fulfill({ json: ok({ requestStatus: 'ORDER_CREATED', orderId: '9101' }) })
        }
    })
    await page.route('**/api/user/seckill/requests/by-key?*', (route) =>
        route.fulfill({
            json: ok(
                recovered
                    ? { requestStatus: 'ORDER_CREATED', orderId: '9101', orderNo: 'DEMO9101' }
                    : { requestStatus: 'UNKNOWN' },
            ),
        }),
    )
    await page.goto(checkout)
    await page.getByRole('checkbox').check()
    await page.getByRole('button', { name: '确认并提交申请' }).dblclick()
    await expect(page.getByRole('heading', { name: '正在确认购票结果' })).toBeVisible()
    expect(keys).toHaveLength(1)
    await page.reload()
    await expect(page.getByRole('heading', { name: '正在确认购票结果' })).toBeVisible()
    expect(keys).toHaveLength(1)
    await page.getByRole('button', { name: '使用原申请重试' }).click()
    await expect(page.getByRole('link', { name: '去支付', exact: true })).toBeVisible()
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBe(keys[1])
})

test('取消确认中仍展示中间状态，服务端取消完成后才显示已取消', async ({ page }) => {
    await setup(page)
    let status = 0
    await page.route('**/api/user/orders/9101', (route) =>
        route.fulfill({ json: ok({ ...order, orderStatus: status }) }),
    )
    await page.route('**/api/user/orders/9101/cancel', async (route) => {
        status = 4
        await route.fulfill({ json: ok(null) })
    })
    await page.goto('/account/orders/9101')
    await page.getByRole('button', { name: '取消订单', exact: true }).click()
    await page.getByRole('button', { name: '确认取消', exact: true }).click()
    await expect(page.locator('.order-state')).toHaveText('取消确认中')
    await expect(page.getByRole('link', { name: '去支付', exact: true })).toHaveCount(0)
    status = 2
    await page.getByRole('button', { name: '刷新状态' }).click()
    await expect(page.locator('.order-state')).toHaveText('已取消')
})

test('支付仅允许沙箱表单且不执行返回脚本，支付结果以订单为准', async ({ page }) => {
    await setup(page)
    let paid = false
    await page.route('**/api/user/orders/9101', (route) =>
        route.fulfill({ json: ok({ ...order, orderStatus: paid ? 1 : 0 }) }),
    )
    await page.goto('/orders/9101/pay')
    await expect(page.getByRole('heading', { name: '订单收银台' })).toBeVisible()
    const result = await page.evaluate(async () => {
        // @ts-expect-error 浏览器通过 Vite 导入源码模块。
        const { parsePaymentForm } = await import('/src/utils/payment.ts')
        const html =
            '<form method="post" action="https://openapi-sandbox.dl.alipaydev.com/gateway.do"><input type="hidden" name="biz_content" value="demo"></form><script>window.__injected=true</script>'
        const parsed = parsePaymentForm(html)
        let rejected = false
        try {
            parsePaymentForm(html.replace('openapi-sandbox.dl.alipaydev.com', 'evil.example'))
        } catch {
            rejected = true
        }
        return { parsed, rejected, injected: '__injected' in window }
    })
    expect(result.rejected).toBe(true)
    expect(result.injected).toBe(false)
    expect(result.parsed.fields).toEqual([{ name: 'biz_content', value: 'demo' }])
    await expect(page.getByRole('heading', { name: '已支付', exact: true })).toHaveCount(0)
    paid = true
    await page.getByRole('button', { name: '查询支付结果' }).click()
    await expect(page.getByRole('heading', { name: '已支付', exact: true })).toBeVisible()
})

test('登录过期跳回登录并保留订单地址，手机各交易页面无溢出', async ({ page }) => {
    await setup(page)
    await page.setViewportSize({ width: 390, height: 844 })
    for (const path of [
        '/login',
        checkout,
        '/account/orders',
        '/account/orders/9101',
        '/orders/9101/pay',
    ]) {
        await page.goto(path)
        await expect(page.locator('main h1, main h2').first()).toBeVisible()
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
            true,
        )
    }
    await page.route('**/api/user/orders/9101', (route) =>
        route.fulfill({ json: { code: 401, msg: '请先登录' } }),
    )
    await page.getByRole('button', { name: '查询支付结果' }).click()
    await expect(page).toHaveURL(/login\?redirect=.*orders/)
})

test('点击支付在独立窗口提交沙箱表单，返回脚本不会执行', async ({ page, context }) => {
    await setup(page)
    await page.route('**/api/user/payments/create', (route) =>
        route.fulfill({
            json: ok({
                paymentNo: 'P9101',
                orderNo: 'DEMO9101',
                payStatus: 0,
                totalAmount: 360,
                expireTime: order.expireTime,
                paymentPageHtml:
                    '<form method="post" action="https://openapi-sandbox.dl.alipaydev.com/gateway.do"><input type="hidden" name="biz_content" value="demo"></form><script>window.__injected=true</script>',
            }),
        }),
    )
    await context.route('https://openapi-sandbox.dl.alipaydev.com/**', (route) =>
        route.fulfill({ body: '<h1>沙箱测试网关</h1>', contentType: 'text/html' }),
    )
    await page.route('**/api/user/payments/status/P9101', (route) =>
        route.fulfill({ json: ok({ paymentNo: 'P9101', payStatus: 1 }) }),
    )
    await page.goto('/orders/9101/pay')
    const posted = context.waitForEvent('request', {
        predicate: (request) =>
            request.url().startsWith('https://openapi-sandbox.dl.alipaydev.com/') &&
            request.method() === 'POST',
    })
    await page.getByRole('button', { name: '前往支付宝支付' }).click()
    const request = await posted
    expect(request.postData()).toBe('biz_content=demo')
    await expect(page.getByText('支付页面已打开。', { exact: false })).toBeVisible()
    expect(await page.evaluate(() => '__injected' in window)).toBe(false)
    await expect(page.getByRole('heading', { name: '已支付', exact: true })).toHaveCount(0)
})

for (const http401 of [false, true]) {
    test(`旧令牌请求的迟到401不会清除刚登录的新会话 HTTP=${http401}`, async ({ page }) => {
        await setup(page)
        let release: () => void = () => {}
        const delayed = new Promise<void>((resolve) => {
            release = resolve
        })
        let profileStarted = false
        await page.route('**/api/user/profile', async (route) => {
            profileStarted = true
            await delayed
            await route.fulfill({
                status: http401 ? 401 : 200,
                json: { code: 401, msg: '旧令牌失效' },
            })
        })
        await page.route('**/api/auth/user/login-or-register', (route) =>
            route.fulfill({ json: ok({ ...user, token: 'new-token' }) }),
        )
        await page.goto('/login?redirect=' + encodeURIComponent(checkout))
        await expect.poll(() => profileStarted).toBe(true)
        await page.getByLabel('邮箱', { exact: true }).fill(user.email)
        await page.getByLabel('密码', { exact: true }).fill('demo-pass-123')
        await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
        await expect(page).toHaveURL(/checkout/)
        release()
        await expect(page.getByRole('heading', { name: '确认购票信息' })).toBeVisible()
        await expect
            .poll(() =>
                page.evaluate(
                    () => JSON.parse(sessionStorage.getItem('youxi-auth') || 'null')?.token,
                ),
            )
            .toBe('new-token')
    })
}

test('交易关闭时展示拒绝受理，返回选票后可清理原申请', async ({ page }) => {
    await setup(page)
    let queries = 0
    await page.route('**/api/user/seckill', route => route.fulfill({json:{code:0,msg:'交易功能尚未启用',data:{requestStatus:'REJECTED'}}}))
    await page.route('**/api/user/seckill/requests/by-key?*', async route => { queries++; await route.fulfill({json:{code:0,msg:'交易功能尚未启用'}}) })
    await page.goto(checkout)
    await page.getByRole('checkbox').check()
    await page.getByRole('button',{name:'确认并提交申请'}).click()
    await expect(page.getByRole('heading',{name:'本次申请未受理'})).toBeVisible()
    await expect(page.getByText('交易功能尚未启用',{exact:true})).toBeVisible()
    expect(queries).toBe(0)
    await page.getByRole('button',{name:'重新选择演出'}).click()
    expect(await page.evaluate(()=>localStorage.getItem('youxi-purchase:9001'))).toBeNull()
})
