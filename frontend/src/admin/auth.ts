import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginInfo } from '../api/tradeTypes'
import { readSession, saveSession } from '../utils/session'

export const isAdminRole = (role?: string) => role === 'ADMIN' || role === 'SUPER_ADMIN'
export const useAdminAuth = defineStore('admin-auth', () => {
    const user = ref(readSession('admin'))
    const loggedIn = computed(() => !!user.value?.token && isAdminRole(user.value.role))
    function accept(info: LoginInfo & { role: string }) {
        saveSession(info, 'admin')
        user.value = info
    }
    function clear() {
        saveSession(null, 'admin')
        user.value = null
    }
    return { user, loggedIn, accept, clear }
})
