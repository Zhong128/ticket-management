import { test, expect, type Page } from '@playwright/test'
const user = {
    id: '8801',
    username: '体验用户',
    email: 'acceptance@example.invalid',
    token: 'acceptance-fixture',
}
async function setup(page: Page, signedIn = true) {
    if (signedIn)
        await page.addInitScript(
            (value) => sessionStorage.setItem('youxi-auth', JSON.stringify(value)),
            user,
        )
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        (route) => {
            const path = new URL(route.request().url()).pathname
            let data: unknown = []
            if (path === '/api/user/profile') data = user
            if (path === '/api/public/events/page')
                data = { records: [], page: 1, totalPages: 0, total: 0 }
            if (path === '/api/user/orders/8802')
                data = {
                    id: '8802',
                    orderNo: 'O8802',
                    eventName: '整体验收音乐会',
                    eventImage: '',
                    sessionTime: '2026-11-08T19:30:00',
                    venueName: '城市音乐厅',
                    tierName: '普通票',
                    quantity: 1,
                    totalAmount: 100,
                    orderStatus: 0,
                    expireTime: '2099-11-08T19:00:00',
                    createTime: '2026-10-26T09:00:00',
                }
            return route.fulfill({ json: { code: 1, data } })
        },
    )
}

test('手机个人中心当前栏目始终可见', async ({ page }) => {
    await setup(page)
    await page.setViewportSize({ width: 320, height: 740 })
    for (const path of ['/account/addresses', '/support', '/account/orders']) {
        await page.goto(path)
        const active = page.getByRole('navigation', { name: '个人中心导航' }).locator('a.active')
        await expect(active).toBeInViewport({ ratio: 1 })
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
            true,
        )
    }
})

test('登录期间离开页面，迟到的成功响应不会登录或强制跳转', async ({ page }) => {
    await setup(page, false)
    let release: () => void = () => {}
    const delayed = new Promise<void>((resolve) => {
        release = resolve
    })
    await page.route('**/api/auth/user/login-or-register', async (route) => {
        await delayed
        await route.fulfill({ json: { code: 1, data: user } })
    })
    await page.goto('/login?redirect=%2Faccount%2Forders')
    await page.getByLabel('邮箱', { exact: true }).fill(user.email)
    await page.getByLabel('密码', { exact: true }).fill('fixture-pass')
    const completed = page.waitForResponse('**/api/auth/user/login-or-register')
    await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
    await page.getByRole('link', { name: '全部演出', exact: true }).click()
    await expect(page).toHaveURL(/\/events$/)
    release()
    await completed
    await expect(page).toHaveURL(/\/events$/)
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('youxi-auth'))).toBeNull()
})

test('发起支付后离开收银台，迟到响应只保存流水而不提交外部表单', async ({ page, context }) => {
    await setup(page)
    let release: () => void = () => {},
        posted = 0
    const delayed = new Promise<void>((resolve) => {
        release = resolve
    })
    await page.route('**/api/user/payments/create', async (route) => {
        await delayed
        await route.fulfill({
            json: {
                code: 1,
                data: {
                    paymentNo: 'P8802',
                    payStatus: 0,
                    paymentPageHtml:
                        '<form method="post" action="https://openapi-sandbox.dl.alipaydev.com/gateway.do"><input type="hidden" name="biz_content" value="demo"></form>',
                },
            },
        })
    })
    await context.route('https://openapi-sandbox.dl.alipaydev.com/**', (route) => {
        posted++
        return route.fulfill({ body: 'sandbox intercepted' })
    })
    await page.goto('/orders/8802/pay')
    const popupPromise = page.waitForEvent('popup')
    const completed = page.waitForResponse('**/api/user/payments/create')
    await page.getByRole('button', { name: '前往支付宝支付' }).click()
    const popup = await popupPromise
    await page.getByRole('link', { name: '返回订单详情', exact: true }).click()
    await expect(page).toHaveURL(/account\/orders\/8802/)
    release()
    await completed
    await expect
        .poll(() => page.evaluate(() => localStorage.getItem('youxi-payment:8801:8802')))
        .toBe('P8802')
    await expect.poll(() => popup.isClosed()).toBe(true)
    expect(posted).toBe(0)
})
for (const width of [320, 768]) {
    test(`${width}px主要页面、长订单编号与地址弹窗没有横向溢出`, async ({ page, request }) => {
        await setup(page)
        await page.route(
            (url) => url.pathname.startsWith('/api/public/'),
            (route) => route.continue(),
        )
        const api = await request.get(
            '/api/public/events/page?name=' + encodeURIComponent('奇妙萌可'),
        )
        const event = (await api.json()).data.records[0]
        const shows = (await (await request.get(`/api/public/sessions?eventId=${event.id}`)).json())
            .data
        const tier = (
            await (
                await request.get(
                    `/api/public/ticket-tiers/page?eventId=${event.id}&sessionId=${shows[0].id}&page=1&size=10`,
                )
            ).json()
        ).data.records[0]
        const order = {
            id: '8802',
            orderNo: 'ACCEPTANCE' + '9'.repeat(54),
            eventName: '整体验收音乐会'.repeat(8),
            eventImage: '',
            sessionTime: '2026-11-08T19:30:00',
            venueName: '城市音乐厅',
            tierName: '普通票',
            quantity: 1,
            totalAmount: 100,
            orderStatus: 0,
            expireTime: '2099-11-08T19:00:00',
            createTime: '2026-10-26T09:00:00',
        }
        await page.route('**/api/user/orders/8802', (route) =>
            route.fulfill({ json: { code: 1, data: order } }),
        )
        await page.route('**/api/user/orders/page?*', (route) =>
            route.fulfill({
                json: { code: 1, data: { records: [order], total: 1, page: 1, totalPages: 1 } },
            }),
        )
        await page.route('**/api/user/address', (route) =>
            route.fulfill({
                json: {
                    code: 1,
                    data: [
                        {
                            id: '8810',
                            receiverName: '小林',
                            receiverPhone: '13800138000',
                            province: '北京市',
                            city: '北京市',
                            district: '海淀区',
                            detailAddress: '验收地址'.repeat(20),
                            isDefault: 1,
                        },
                    ],
                },
            }),
        )
        await page.route('**/api/user/refunds', (route) =>
            route.fulfill({
                json: {
                    code: 1,
                    data: [
                        {
                            refund_no: 'RF' + '9'.repeat(62),
                            order_id: '8802',
                            amount: 100,
                            status: 'UNKNOWN',
                            reason: 'LATE_OR_DUPLICATE_PAYMENT',
                        },
                    ],
                },
            }),
        )
        await page.route('**/api/user/seckill/requests/by-key?*', (route) =>
            route.fulfill({
                json: { code: 1, data: { requestStatus: 'RELEASED', releaseReason: '申请已释放' } },
            }),
        )
        await page.setViewportSize({ width, height: 850 })
        const paths = [
            ['/', '.hero-copy'],
            ['/events', '.event-card'],
            [`/events/${event.id}`, '.detail-info'],
            ['/login', '.login-form-panel'],
            [
                `/checkout?eventId=${event.id}&sessionId=${shows[0].id}&tierId=${tier.id}&quantity=1`,
                '.order-summary',
            ],
            ['/purchase/result?requestKey=layout-check', '.result-panel'],
            ['/account/orders', '.order-card'],
            ['/account/orders/8802', '.order-summary'],
            ['/orders/8802/pay', '.payment-method'],
            ['/account/profile', '.account-form'],
            ['/account/security', '.account-form'],
            ['/account/addresses', '.address-card'],
            ['/account/refunds', '.refund-card'],
            ['/support', '.chat-composer'],
        ]
        for (const [path, selector] of paths) {
            await page.goto(path!)
            await expect(page.locator(selector!).first()).toBeVisible()
            expect(
                await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
                `${width}px ${path}`,
            ).toBe(true)
        }
        await page.goto('/account/addresses')
        await page.getByRole('button', { name: '新增地址', exact: true }).click()
        await expect(page.getByRole('dialog')).toBeVisible()
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
            true,
        )
    })
}
