import { test, expect } from '@playwright/test'
test('退款重试未受理时保留提示，不伪报成功', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-admin-auth', JSON.stringify({ id:'1', email:'admin@example.invalid', token: 'controlled-admin', role: 'ADMIN', username: '验收管理员' })))
    await page.route(url => url.pathname.startsWith('/api/'), route => {
        const path = new URL(route.request().url()).pathname
        const data = path === '/api/public/app-status' ? { trade: { enabled: true } } : path.endsWith('/retry') ? false : path === '/api/admin/refunds/page' ? { records: [{ refund_no: 'R_TEST', order_id: '31', amount: 100, status: 'MANUAL_REQUIRED', attempts: 10 }], total: 1 } : { role: 'ADMIN', username: '验收管理员' }
        return route.fulfill({ json: { code: 1, data } })
    })
    await page.goto('/admin/refunds')
    await expect(page.getByRole('heading', { name: '退款处理', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '重试', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '确认重试' }).click()
    await expect(page.getByRole('dialog').getByText('未受理：退款状态可能已变化，请刷新后核对。')).toBeVisible()
})

test('交易关闭时退款及维护按钮禁用，不发变更请求', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-admin-auth', JSON.stringify({ id:'1', email:'admin@example.invalid', token: 'controlled-admin', role: 'ADMIN', username: '验收管理员' })))
    let writes = 0
    await page.route(url => url.pathname.startsWith('/api/'), route => {
        if (route.request().method() !== 'GET') writes++
        const path = new URL(route.request().url()).pathname
        return route.fulfill({ json: { code: 1, data: path === '/api/public/app-status' ? { trade: { enabled: false } } : path === '/api/admin/refunds/page' ? { records: [{ refund_no: 'R_TEST', status: 'MANUAL_REQUIRED', attempts: 10 }], total: 1 } : {role:'ADMIN'} } })
    })
    await page.goto('/admin/refunds')
    await expect(page.getByRole('button', { name: '重试', exact: true })).toBeDisabled()
    await page.goto('/admin/maintenance')
    await expect(page.getByRole('button', { name: '触发支付补偿' })).toBeDisabled()
    await expect(page.getByRole('button', { name: '触发超时关单' })).toBeDisabled()
    expect(writes).toBe(0)
})

test('管理员用户只读，普通用户编辑不发送密码和角色', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-admin-auth', JSON.stringify({ id:'1', email:'admin@example.invalid', token: 'controlled-admin', role: 'ADMIN', username: '验收管理员' })))
    const normal = { id:'2', username:'reader', email:'reader@example.invalid', role:'USER', status:1 }
    let payload: Record<string,unknown> | undefined
    await page.route(url => url.pathname.startsWith('/api/'), route => {
        const path = new URL(route.request().url()).pathname
        if (route.request().method() === 'PUT') payload = route.request().postDataJSON()
        return route.fulfill({ json: { code:1, data: path === '/api/admin/user' ? { records:[normal,{...normal,id:'3',username:'rootadmin',role:'SUPER_ADMIN'}],total:2 } : path === '/api/admin/user/2' ? normal : {role:'ADMIN'} } })
    })
    await page.goto('/admin/users')
    const protectedRow = page.getByRole('row').filter({hasText:'rootadmin'})
    await expect(protectedRow.getByRole('button',{name:'编辑'})).toHaveCount(0)
    await expect(protectedRow.getByRole('checkbox')).toBeDisabled()
    await page.getByRole('row').filter({hasText:'reader / 2'}).getByRole('button',{name:'编辑',exact:true}).click()
    await page.getByRole('dialog').getByLabel('昵称',{exact:true}).fill('现场朋友')
    await page.getByRole('button',{name:'保存资料',exact:true}).click()
    await expect(page.getByText('用户资料已保存。')).toBeVisible()
    expect(payload?.nickname).toBe('现场朋友')
    expect(payload).not.toHaveProperty('password')
    expect(payload).not.toHaveProperty('role')
})


test('订单取消重新查询状态4时不宣称取消成功，备注只发remark', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-admin-auth', JSON.stringify({ id:'1', email:'admin@example.invalid', token:'controlled-admin', role:'ADMIN', username:'验收管理员' })))
    let status = 0
    const writes: Record<string,unknown>[] = []
    await page.route(url => url.pathname.startsWith('/api/'), route => {
        const path = new URL(route.request().url()).pathname
        if (route.request().method() === 'PUT') { const payload = route.request().postDataJSON(); writes.push(payload); if (payload.orderStatus === 2) status = 4 }
        return route.fulfill({ json:{code:1,data:path === '/api/public/app-status' ? {trade:{enabled:true}} : path === '/api/admin/orders/42' ? {id:'42',orderNo:'O_TEST',orderStatus:status,quantity:2,totalAmount:200,remark:''} : {role:'ADMIN'}} })
    })
    await page.goto('/admin/orders/42')
    await page.getByLabel('管理备注').fill('人工核对')
    await page.getByRole('button',{name:'保存备注',exact:true}).click()
    await expect(page.getByText('备注已保存。')).toBeVisible()
    expect(writes[0]).toEqual({remark:'人工核对'})
    await page.getByRole('button',{name:'取消订单',exact:true}).click()
    await page.getByRole('dialog').getByRole('button',{name:'确认取消订单',exact:true}).click()
    await expect(page.getByText('取消请求已提交，当前状态尚待确认，请刷新核对。')).toBeVisible()
    await expect(page.getByText('已重新查询：订单已取消。')).toHaveCount(0)
    expect(writes[1]).toEqual({orderStatus:2})
})


test('切换管理页时不再以新页面查询参数请求旧用户列表', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-admin-auth', JSON.stringify({ id:'1', email:'admin@example.invalid', token:'controlled-admin', role:'ADMIN', username:'验收管理员' })))
    let userQueries = 0
    await page.route(url => url.pathname.startsWith('/api/'), route => {
        const path = new URL(route.request().url()).pathname
        if (path === '/api/admin/user') userQueries++
        return route.fulfill({ json:{code:1,data:path === '/api/public/app-status' ? {trade:{enabled:true}} : path === '/api/auth/userinfo' ? {role:'ADMIN'} : {records:[],total:0}} })
    })
    await page.goto('/admin/users?username=reader')
    await expect(page.getByText('暂无符合条件的用户，调整筛选后重试。')).toBeVisible()
    expect(userQueries).toBe(1)
    await page.getByRole('link',{name:'订单管理',exact:true}).click()
    await expect(page.getByText('暂无符合条件的订单，调整筛选后重试。')).toBeVisible()
    expect(userQueries).toBe(1)
})
