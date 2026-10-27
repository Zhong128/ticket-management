import {test,expect} from '@playwright/test'
import path from 'node:path'

test('管理端桌面手机页面、菜单与弹窗保持可用且无全页横向溢出',async({page})=>{
    test.setTimeout(120000)
    const errors:string[]=[]
    page.on('pageerror',error=>errors.push(error.message))
    await page.addInitScript(()=>sessionStorage.setItem('youxi-admin-auth',JSON.stringify({id:'8001',username:'演出运营',token:'visual-admin',role:'ADMIN'})))
    const event={id:'100',name:'孙燕姿「就在日落以后」巡回演唱会',artistName:'孙燕姿',cityId:'1',cityName:'上海',categoryId:'1',categoryName:'演唱会',venue:'上海体育场',coverImage:'',description:'演出资料用于本地视觉验收。',status:1,saleStartTime:'2026-10-01T10:00:00',saleEndTime:'2027-01-01T10:00:00',eventStartTime:'2027-01-02T18:00:00',eventEndTime:'2027-01-02T22:00:00'}
    const session={id:'200',eventId:'100',sessionName:'上海站 周六晚场',sessionTime:'2027-01-02T19:30:00',status:1}
    const order={id:'100',orderNo:'YX20261027123456789123456789',userName:'现场朋友',userId:'300',eventName:event.name,sessionName:session.sessionName,quantity:2,totalAmount:1160,orderStatus:0}
    await page.route(url=>url.pathname.startsWith('/api/'),route=>{
        const p=new URL(route.request().url()).pathname
        let data:unknown={records:[],total:0,page:1,size:10}
        if(p.endsWith('/userinfo'))data={role:'ADMIN',userId:'8001'}
        else if(p.endsWith('/app-status'))data={ready:true,tradingEnabled:true,customerServiceEnabled:false}
        else if(p.endsWith('/summary'))data={onSaleEventCount:36,upcomingSessionCount:8,pendingOrderCount:12,refundAttentionCount:2,asOf:'2026-10-27T16:00:00'}
        else if(p.endsWith('/stock-statistics'))data={totalTiers:1,totalStock:300,totalAvailable:160,totalSold:140,overallSellRate:46.67,tierDetails:[]}
        else if(p.endsWith('/events/query'))data={records:[event],total:1,page:1,size:10}
        else if(p.endsWith('/events/100'))data=event
        else if(p.endsWith('/sessions/page'))data={records:[session],total:1,page:1,size:10}
        else if(p.endsWith('/cities/query'))data={records:[{id:'1',name:'上海',code:'SHANGHAI',province:'上海市',status:1,hotLevel:1}],total:1,page:1,size:10}
        else if(p.endsWith('/categories/query'))data={records:[{id:'1',name:'演唱会',sortOrder:1,status:1}],total:1,page:1,size:10}
        else if(p.endsWith('/ticket-tiers/page'))data={records:[{id:'400',eventId:'100',sessionId:'200',tierName:'看台580元',currentPrice:580,originalPrice:580,totalStock:300,availableStock:160,status:1}],total:1,page:1,size:10}
        else if(p.endsWith('/orders/page'))data={records:[order],total:1,page:1,size:20}
        else if(p.endsWith('/orders/100'))data=order
        else if(p==='/api/admin/user')data={records:[{id:'300',username:'现场朋友',email:'viewer@example.invalid',role:'USER',status:1}],total:1,page:1,size:20}
        else if(p.endsWith('/refunds/page'))data={records:[{refund_no:'RF20261027123456789',order_id:'100',payment_no:'PAY20261027',amount:580,status:'MANUAL_REQUIRED',attempts:10,reason:'LATE_OR_DUPLICATE_PAYMENT',created_at:'2026-10-27T15:00:00'}],total:1,page:1,size:20}
        return route.fulfill({json:{code:1,data}})
    })
    for(const width of [1440,768,390,320]){
        await page.setViewportSize({width,height:1000})
        for(const url of ['/admin/dashboard','/admin/events','/admin/events/new','/admin/events/100','/admin/sessions','/admin/ticket-tiers','/admin/cities','/admin/categories','/admin/users','/admin/orders','/admin/orders/100','/admin/refunds','/admin/maintenance']){
            await page.goto(url)
            await expect(page.locator('h1')).toBeVisible()
            await page.waitForLoadState('networkidle')
            expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`${width}px ${url}`).toBeTruthy()
            if(url==='/admin/dashboard')await page.screenshot({path:path.resolve(`../output/admin-dashboard-${width}.png`),fullPage:true})
        }
        await page.goto('/admin/cities')
        await page.getByRole('button',{name:'新增城市',exact:true}).click()
        await expect(page.getByRole('dialog')).toBeVisible()
        expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy()
        await page.screenshot({path:path.resolve(`../output/admin-city-modal-${width}.png`),fullPage:true})
        await page.keyboard.press('Escape')
        await expect(page.getByRole('dialog')).not.toBeVisible()
        if(width<768){
            await page.getByRole('button',{name:'打开管理菜单'}).click()
            await expect(page.getByRole('dialog',{name:'管理菜单'})).toBeVisible()
            await page.keyboard.press('Escape')
            await expect(page.getByRole('dialog')).not.toBeVisible()
        }
    }
    expect(errors).toEqual([])
})
