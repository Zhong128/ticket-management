import { test, expect } from '@playwright/test'

test('演出管理保留筛选并将描述作为纯文本显示', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-admin-auth', JSON.stringify({ id: '1', username: '管理员', role: 'ADMIN', token: 'fixture' })))
    await page.route(url => url.pathname.startsWith('/api/'), route => {
        const path = new URL(route.request().url()).pathname
        if (path === '/api/auth/userinfo') return route.fulfill({json:{code:1,data:{id:'1',role:'ADMIN',username:'管理员'}}})
        const event = { id: '1', name: '测试演出', artistName: '测试艺人', cityId: '1', categoryId: '1', venue: '测试场馆', description: '<b>纯文本描述</b>', status: 0 }
        return route.fulfill({ json: { code: 1, data: path === '/api/admin/events/1' ? event : path === '/api/admin/sessions' ? [] : { records: path.includes('/events/') ? [event] : [], total: 1, page: 2, size: 10 } } })
    })
    await page.goto('/admin/events?keyword=测试&page=2')
    await expect(page.getByRole('textbox', { name: '搜索演出或艺人' })).toHaveValue('测试')
    await page.goto('/admin/events/1')
    await expect(page.getByText('<b>纯文本描述</b>', { exact: true })).toBeVisible()
})

test('场次恢复携带完整 DTO，库存调整失败不允许直接重复提交', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-admin-auth', JSON.stringify({ id: '1', username: '管理员', role: 'ADMIN', token: 'fixture' })))
    const event = { id: '1', name: '测试演出' }
    const session = { id: '2', eventId: '1', sessionName: '周末晚场', sessionTime: '2027-01-02T19:30:00', status: 0 }
    const tier = { id: '3', eventId: '1', sessionId: '2', tierName: '看台票', originalPrice: 100, currentPrice: 80, totalStock: 100, availableStock: 90, status: 0 }
    let sessionPayload: unknown
    let adjustments = 0
    await page.route(url => url.pathname.startsWith('/api/'), route => {
        const request = route.request(), path = new URL(request.url()).pathname
        if (path === '/api/auth/userinfo') return route.fulfill({json:{code:1,data:{id:'1',role:'ADMIN',username:'管理员'}}})
        if (request.method() === 'PUT' && path === '/api/admin/sessions/2') sessionPayload = request.postDataJSON()
        if (path.endsWith('/stock-adjustments')) { adjustments++; return route.fulfill({ json: {code:0,msg:'连接中断，请核实库存'} }) }
        const data = path === '/api/admin/sessions/2' ? session : path.endsWith('/stock-statistics') ? {totalTiers:1,totalStock:100,totalAvailable:90,totalSold:10} : { records: path.includes('/events/') ? [event] : path.includes('/sessions/') ? [session] : path.includes('/ticket-tiers/') ? [tier] : [], total:1, page:1, size:10 }
        return route.fulfill({ json: {code:1,data} })
    })
    await page.goto('/admin/sessions?eventId=1')
    await page.getByRole('button',{name:'编辑 / 恢复'}).click()
    const dialog = page.getByRole('dialog',{name:'编辑场次'})
    await dialog.getByLabel('状态').selectOption('1')
    await dialog.getByRole('button',{name:'保存场次'}).click()
    await expect.poll(() => sessionPayload).toEqual({eventId:'1',sessionName:'周末晚场',sessionTime:'2027-01-02T19:30:00',status:1})
    await page.goto('/admin/ticket-tiers?eventId=1&sessionId=2')
    await expect(page.getByText('占用 = 总库存 − 可用库存，包含锁定，不等于实际销量。统计范围按演出、场次计算。')).toBeVisible()
    await page.getByRole('button',{name:'调整库存',exact:true}).click()
    const stock = page.getByRole('dialog',{name:'调整库存'})
    await stock.getByLabel('库存调整量').fill('5')
    await stock.getByRole('button',{name:'确认调整'}).click()
    await expect(stock.getByRole('alert')).toContainText('不要直接重复提交')
    await expect(stock.getByRole('button',{name:'确认调整'})).toBeDisabled()
    expect(adjustments).toBe(1)
})

test('城市被引用时在删除弹窗内显示后端拒绝原因', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-admin-auth', JSON.stringify({ id: '1', username: '管理员', role: 'ADMIN', token: 'fixture' })))
    let deletions = 0
    await page.route(url => url.pathname.startsWith('/api/'), route => {
        if (new URL(route.request().url()).pathname === '/api/auth/userinfo') return route.fulfill({json:{code:1,data:{id:'1',role:'ADMIN',username:'管理员'}}})
        if (route.request().method() === 'DELETE') {
            deletions++
            return route.fulfill({ json: {code:0,msg:'城市已被演出引用，请先停用'} })
        }
        return route.fulfill({ json: {code:1,data:{records:[{id:'1',name:'杭州市',code:'HANGZHOU',province:'浙江省',hotLevel:1,status:1}],total:1,page:1,size:10}} })
    })
    await page.goto('/admin/cities')
    await page.getByRole('button',{name:'删除',exact:true}).click()
    const dialog = page.getByRole('dialog',{name:'删除城市'})
    await expect(dialog).toBeVisible()
    expect(deletions).toBe(0)
    await dialog.getByRole('button',{name:'确认删除'}).click()
    await expect(dialog.getByRole('alert')).toContainText('城市已被演出引用')
    expect(deletions).toBe(1)
})

test('售罄筛选调用专用接口并分页，退出恢复原状态筛选', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('youxi-admin-auth', JSON.stringify({ id: '1', username: '管理员', role: 'ADMIN', token: 'fixture' })))
    const soldRequests: URL[] = []
    const pageRequests: URL[] = []
    await page.route(url => url.pathname.startsWith('/api/'), route => {
        const url = new URL(route.request().url())
        let data: unknown = {records:[],total:0}
        if (url.pathname === '/api/auth/userinfo') data = {role:'ADMIN'}
        if (url.pathname.endsWith('/stock-statistics')) data = {totalTiers:12,totalStock:120,totalAvailable:0,totalSold:120}
        if (url.pathname.endsWith('/sold-out')) {
            soldRequests.push(url)
            data = Array.from({length:12}, (_, index) => ({tierId:String(index + 10),tierName:`售罄票档${index}`,price:index === 0 ? 50 : 150,totalStock:10,availableStock:0,soldStock:10,status:1}))
        }
        if (url.pathname === '/api/admin/ticket-tiers/page') {
            pageRequests.push(url)
            data = {records:[{id:'88',eventId:'1',sessionId:'2',tierName:'原下架票档',currentPrice:150,originalPrice:150,totalStock:10,availableStock:5,status:0}],total:1}
        }
        return route.fulfill({json:{code:1,data}})
    })
    await page.goto('/admin/ticket-tiers?eventId=1&sessionId=2&status=0&minPrice=100&maxPrice=200')
    await expect(page.getByRole('checkbox',{name:'仅看售罄'})).toBeVisible({timeout:2000})
    await page.getByRole('checkbox',{name:'仅看售罄'}).check()
    await expect(page.getByRole('combobox',{name:'状态',exact:true})).toBeDisabled()
    await expect(page.getByRole('combobox',{name:'状态',exact:true})).toHaveValue('1')
    await expect(page.getByRole('row',{name:/^售罄票档1 /})).toBeVisible()
    await expect(page.getByRole('row',{name:/^售罄票档0 /})).toHaveCount(0)
    await expect(page.getByText('共 11 条')).toBeVisible()
    await page.getByRole('button',{name:'下一页'}).click()
    await expect(page.getByRole('row',{name:/^售罄票档11 /})).toBeVisible()
    expect(soldRequests[0]?.searchParams.get('eventId')).toBe('1')
    expect(soldRequests[0]?.searchParams.get('sessionId')).toBe('2')
    await page.getByRole('checkbox',{name:'仅看售罄'}).uncheck()
    await expect(page.getByRole('combobox',{name:'状态',exact:true})).toBeEnabled()
    await expect(page.getByRole('combobox',{name:'状态',exact:true})).toHaveValue('0')
    await expect(page.getByText('原下架票档')).toBeVisible()
    expect(pageRequests.at(-1)?.searchParams.get('status')).toBe('0')
})
