// 不注入SDK HTML、不执行其中脚本；仅重建沙箱网关的隐藏表单。
export function parsePaymentForm(html: string) {
    const document = new DOMParser().parseFromString(html, 'text/html')
    const forms = document.querySelectorAll('form')
    if (forms.length !== 1) throw new Error('支付页面格式异常，请重新发起支付')
    const form = forms[0]!
    const action = new URL(form.getAttribute('action') || '')
    const hosts = ['openapi-sandbox.dl.alipaydev.com', 'openapi.alipaydev.com']
    if (
        action.protocol !== 'https:' ||
        !hosts.includes(action.hostname) ||
        action.port ||
        action.pathname !== '/gateway.do' ||
        action.username ||
        action.password ||
        form.method.toLowerCase() !== 'post'
    )
        throw new Error('支付网关不是支付宝沙箱，已停止跳转')
    const fields = Array.from(form.querySelectorAll('input[type="hidden"]')).map((input) => ({
        name: input.getAttribute('name') || '',
        value: input.getAttribute('value') || '',
    }))
    if (!fields.length || fields.some((field) => !field.name))
        throw new Error('支付参数不完整，请重试')
    return { action: action.href, fields }
}

export function submitPaymentForm(html: string, popup: Window) {
    const payment = parsePaymentForm(html)
    const name = `youxi-payment-${crypto.randomUUID()}`
    popup.name = name
    popup.opener = null
    const form = document.createElement('form')
    form.method = 'post'
    form.action = payment.action
    form.target = name
    for (const field of payment.fields) {
        const input = document.createElement('input')
        input.type = 'hidden'
        input.name = field.name
        input.value = field.value
        form.appendChild(input)
    }
    document.body.appendChild(form)
    HTMLFormElement.prototype.submit.call(form)
    form.remove()
}
