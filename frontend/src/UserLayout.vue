<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronDown, MapPin, Search, Ticket, X, ArrowUpRight } from 'lucide-vue-next'
import { useCatalogStore } from './stores/catalog'

import { useAuthStore } from './stores/auth'
import { trade } from './api/trade'
import AccountMenu from './components/AccountMenu.vue'
const auth = useAuthStore()
const catalog = useCatalogStore()
const route = useRoute()
const router = useRouter()
const keyword = ref('')
const loggingOut = ref(false)
const citySearch = ref('')
const cityDialog = ref<HTMLDialogElement>()
const filteredCities = computed(() =>
    catalog.cities.filter((city) => city.name.includes(citySearch.value.trim())),
)
watch(
    () => route.query.name,
    (value) => {
        keyword.value = typeof value === 'string' ? value : ''
    },
    { immediate: true },
)
watch(
    () => [route.path, route.query.cityId],
    () => {
        if (route.path === '/events') {
            const id = typeof route.query.cityId === 'string' ? route.query.cityId : ''
            if (catalog.cityId !== id) catalog.selectCity(id)
        }
    },
    { immediate: true },
)

function openCityDialog() {
    citySearch.value = ''
    cityDialog.value?.showModal()
}

function search() {
    router.push({
        path: '/events',
        query: { name: keyword.value.trim() || undefined, cityId: catalog.cityId || undefined },
    })
}
function selectCity(id: string) {
    catalog.selectCity(id)
    cityDialog.value?.close()
    if (route.path === '/events')
        router.push({
            path: '/events',
            query: { ...route.query, cityId: id || undefined, page: undefined },
        })
}
function categoryLink(id?: string) {
    return { path: '/events', query: { categoryId: id, cityId: catalog.cityId || undefined } }
}
function expired() {
    auth.clear()
    if (route.meta.requiresAuth)
        router.replace({ path: '/login', query: { redirect: route.fullPath } })
}
async function logout() {
    if (loggingOut.value) return
    loggingOut.value = true
    try {
        await auth.logout()
    } catch {
        // 本地退出已完成，网络恢复后仍需重新登录。
    } finally {
        loggingOut.value = false
        if (route.meta.requiresAuth) router.replace('/login')
    }
}
onMounted(() => {
    catalog.load()
    window.addEventListener('youxi-auth-expired', expired)
    const current = auth.user
    if (current)
        trade
            .profile()
            .then((profile) => {
                if (auth.user?.token === current.token)
                    auth.accept({ ...current, username: profile.username, email: profile.email })
            })
            .catch(() => {})
})
onUnmounted(() => window.removeEventListener('youxi-auth-expired', expired))
</script>
<template>
    <a class="skip-link" href="#main">跳到主要内容</a>
    <div class="utility-bar">
        <div class="container utility-inner">
            <span>让喜欢发生在现场</span>
            <div class="header-account">
                <AccountMenu :signed-in="auth.loggedIn" :username="auth.user?.username || auth.user?.email" :busy="loggingOut" @logout="logout"/>
                <RouterLink to="/support">智能客服</RouterLink>
            </div>
        </div>
    </div>
    <header class="site-header">
        <div class="container header-main">
            <RouterLink to="/" class="brand" aria-label="有戏首页">
                <Ticket :size="34" :stroke-width="2.1" />
                <span>
                    有戏
                    <small>每一场，都值得</small>
                </span>
            </RouterLink>
            <button class="city-trigger" @click="openCityDialog">
                <MapPin :size="16" />
                {{ catalog.city?.name || '全国' }}
                <ChevronDown :size="13" />
            </button>
            <form class="search-form" role="search" @submit.prevent="search">
                <Search :size="19" />
                <input
                    v-model="keyword"
                    aria-label="搜索演出或艺人"
                    placeholder="搜索演出名称，发现你的下一场热爱"
                    maxlength="100"
                />
                <button type="submit">搜索</button>
            </form>
            <RouterLink to="/events" class="header-discover">
                发现演出
                <ArrowUpRight :size="16" />
            </RouterLink>
        </div>
        <nav class="container category-nav" aria-label="演出分类">
            <RouterLink to="/" :class="{ selected: route.path === '/' }">首页</RouterLink>
            <RouterLink
                :to="categoryLink()"
                :class="{ selected: route.path === '/events' && !route.query.categoryId }"
            >
                全部演出
            </RouterLink>
            <RouterLink
                v-for="category in catalog.categories"
                :key="category.id"
                :to="categoryLink(category.id)"
                :class="{ selected: route.query.categoryId === category.id }"
            >
                {{ category.name }}
            </RouterLink>
        </nav>
    </header>
    <div v-if="catalog.error" class="catalog-error container" role="status">
        城市和分类暂不可用
        <button @click="catalog.load">重试</button>
    </div>
    <main id="main" tabindex="-1"><RouterView :key="route.fullPath" /></main>
    <footer class="site-footer">
        <div class="container footer-inner">
            <div class="brand footer-brand">
                <Ticket :size="28" />
                <span>
                    有戏
                    <small>让喜欢发生在现场</small>
                </span>
            </div>
            <p>
                演出信息与海报来自公开演出页面，仅用于本地学习展示。
                <br />
                本项目不提供真实售票服务，演示库存与售票配置不代表官方信息。
            </p>
            <RouterLink to="/events">发现下一场现场</RouterLink>
        </div>
    </footer>
    <dialog
        ref="cityDialog"
        class="city-dialog"
        @click="
            (event) => {
                if (event.target === cityDialog) cityDialog?.close()
            }
        "
    >
        <div class="dialog-heading">
            <div>
                <h2>你想去哪个城市？</h2>
                <p class="muted">换一座城，发现另一种现场。</p>
            </div>
            <button class="icon-button" aria-label="关闭城市选择" @click="cityDialog?.close()">
                <X :size="22" />
            </button>
        </div>
        <input
            v-model="citySearch"
            class="city-search"
            aria-label="筛选城市"
            placeholder="输入城市名称"
        />
        <div class="city-options">
            <button :class="{ active: !catalog.cityId }" @click="selectCity('')">全国</button>
            <button
                v-for="city in filteredCities"
                :key="city.id"
                :class="{ active: city.id === catalog.cityId }"
                @click="selectCity(city.id)"
            >
                {{ city.name }}
            </button>
        </div>
        <p v-if="catalog.loading" class="muted">正在加载城市…</p>
        <p v-else-if="!filteredCities.length" class="muted">没有匹配的城市。</p>
    </dialog>
</template>
