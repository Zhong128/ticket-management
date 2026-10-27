import type { LoginInfo } from '../api/tradeTypes'

export type SessionScope = 'user' | 'admin'
const key = (scope: SessionScope) => (scope === 'admin' ? 'youxi-admin-auth' : 'youxi-auth')
export function readSession(scope: SessionScope = 'user'): (LoginInfo & { role?: string }) | null {
    try {
        const data = JSON.parse(sessionStorage.getItem(key(scope)) || 'null')
        return data && typeof data.id === 'string' && typeof data.token === 'string' ? data : null
    } catch {
        return null
    }
}
export function saveSession(
    info: (LoginInfo & { role?: string }) | null,
    scope: SessionScope = 'user',
) {
    if (info) sessionStorage.setItem(key(scope), JSON.stringify(info))
    else sessionStorage.removeItem(key(scope))
}
