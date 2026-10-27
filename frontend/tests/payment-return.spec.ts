import { test, expect } from '@playwright/test'
const user={id:'9901',username:'返回验收',email:'return@example.invalid',token:'return-fixture'}

test('支付回跳不信任成功参数，以本人后端订单为准',async({page})=>{
    await page.addInitScript(value=>sessionStorage.setItem('youxi-auth',JSON.stringify(value)),user)
    let paid=false
    await page.route(url=>url.pathname.startsWith('/api/'),route=>{
        const path=new URL(route.request().url()).pathname
        let data:unknown=[]
        if(path==='/api/user/profile')data=user
        if(path==='/api/user/payments/status/P9901')data={paymentNo:'P9901',orderId:'9902',payStatus:paid?2:1}
        if(path==='/api/user/orders/9902')data={id:'9902',orderNo:'O9902',orderStatus:paid?1:0}
        return route.fulfill({json:{code:1,data}})
    })
    await page.goto('/payment/return?out_trade_no=P9901&trade_status=TRADE_SUCCESS')
    await expect(page.getByRole('heading',{name:'付款结果待确认'})).toBeVisible()
    await expect(page.getByRole('heading',{name:'支付成功',exact:true})).toHaveCount(0)
    paid=true
    await page.getByRole('button',{name:'查询最新结果'}).click()
    await expect(page.getByRole('heading',{name:'支付成功',exact:true})).toBeVisible()
    await expect(page.getByRole('link',{name:'查看订单详情'})).toHaveAttribute('href','/account/orders/9902')
})

test('新窗口无登录会话时保留流水回跳地址，不请求支付接口',async({page})=>{
    let queries=0
    await page.route(url=>url.pathname.startsWith('/api/'),route=>{
        if(new URL(route.request().url()).pathname.includes('/payments/status'))queries++
        return route.fulfill({json:{code:1,data:[]}})
    })
    await page.goto('/payment/return?out_trade_no=P9901')
    await expect(page.getByRole('link',{name:'登录后查询付款结果'})).toHaveAttribute('href',/redirect=.*payment.*return.*P9901/)
    expect(queries).toBe(0)
})
