import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { trade } from '../api/trade'
import type { LoginInfo } from '../api/tradeTypes'
import { readSession, saveSession } from '../utils/session'

export const useAuthStore = defineStore('auth', () => {
    const user = ref<LoginInfo | null>(readSession())
    const loggedIn = computed(() => !!user.value?.token)
    function accept(info: LoginInfo) {
        saveSession(info)
        user.value = info
    }
    function clear() {
        saveSession(null)
        user.value = null
    }
    async function logout() {
        try {
            await trade.logout()
        } finally {
            clear()
        }
    }
    return { user, loggedIn, accept, clear, logout }
})
