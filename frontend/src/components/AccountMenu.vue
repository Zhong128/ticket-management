<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronDown, UserRound } from 'lucide-vue-next'

const props = defineProps<{ signedIn: boolean; username?: string; busy?: boolean }>()
const emit = defineEmits<{ logout: [] }>()
const route = useRoute()
const open = ref(false)
const root = ref<HTMLElement>()
const trigger = ref<HTMLButtonElement>()
watch(
    () => [route.fullPath, props.signedIn],
    () => {
        open.value = false
    },
)
function outside(event: PointerEvent) {
    if (event.target instanceof Node && !root.value?.contains(event.target)) open.value = false
}
function focusOut(event: FocusEvent) {
    if (!(event.relatedTarget instanceof Node) || !root.value?.contains(event.relatedTarget))
        open.value = false
}
function hover(event: PointerEvent, value: boolean) {
    if (event.pointerType === 'mouse') open.value = value
}
function escape() {
    if (!open.value) return
    open.value = false
    trigger.value?.focus()
}
async function firstLink() {
    open.value = true
    await nextTick()
    root.value?.querySelector<HTMLAnchorElement>('nav a')?.focus()
}
function toggle(event: MouseEvent) {
    // 鼠标悬停已展开时，点击保持展开；触屏与键盘仍可切换。
    if (event instanceof PointerEvent && event.pointerType === 'mouse') open.value = true
    else open.value = !open.value
}
function logout() {
    open.value = false
    emit('logout')
}
onMounted(() => document.addEventListener('pointerdown', outside))
onUnmounted(() => document.removeEventListener('pointerdown', outside))
</script>
<template>
    <div
        ref="root"
        class="account-dropdown"
        @pointerenter="hover($event, true)"
        @pointerleave="hover($event, false)"
        @focusout="focusOut"
        @keydown.esc.stop.prevent="escape"
    >
        <button
            ref="trigger"
            class="account-trigger"
            type="button"
            :aria-label="`账户菜单：${signedIn ? username || '我的账户' : '登录 / 注册'}`"
            :aria-expanded="open"
            aria-controls="header-account-menu"
            @click="toggle"
            @keydown.down.prevent="firstLink"
        >
            <span class="account-avatar"><UserRound :size="16" /></span>
            <span class="account-trigger-name">
                {{ signedIn ? username || '我的账户' : '登录 / 注册' }}
            </span>
            <ChevronDown :size="12" :class="{ expanded: open }" />
        </button>
        <div v-if="open" id="header-account-menu" class="account-dropdown-panel">
            <nav aria-label="账户菜单">
                <RouterLink to="/account/profile" @click="open = false">个人信息</RouterLink>
                <RouterLink to="/account/security" @click="open = false">账户设置</RouterLink>
                <RouterLink to="/account/orders" @click="open = false">订单管理</RouterLink>
                <button v-if="signedIn" type="button" :disabled="busy" @click="logout">
                    {{ busy ? '正在退出…' : '退出登录' }}
                </button>
                <RouterLink
                    v-else
                    :to="{ path: '/login', query: { redirect: route.fullPath } }"
                    @click="open = false"
                >
                    登录 / 注册
                </RouterLink>
            </nav>
        </div>
    </div>
</template>
<style scoped>
.account-dropdown {
    position: relative;
    align-self: stretch;
    display: flex;
    align-items: center;
    z-index: 60;
}
.account-trigger {
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 4px 0;
    min-height: 30px;
    cursor: pointer;
    font: inherit;
    color: #68707d;
    border: 0;
    background: none;
}
.account-trigger:hover,
.account-trigger[aria-expanded='true'] {
    color: #eb1761;
}
.account-avatar {
    width: 25px;
    height: 25px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background: #fce5ef;
    color: #be104c;
    flex-shrink: 0;
}
.account-trigger-name {
    max-width: 105px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.account-trigger > svg {
    transition: transform 0.15s ease;
}
.account-trigger > svg.expanded {
    transform: rotate(180deg);
}
.account-dropdown-panel {
    position: absolute;
    right: -17px;
    top: 100%;
    width: 166px;
    padding-top: 8px;
}
.account-dropdown-panel::before {
    content: '';
    position: absolute;
    top: 3px;
    right: 41px;
    width: 10px;
    height: 10px;
    background: white;
    border-left: 1px solid #e9eaf0;
    border-top: 1px solid #e9eaf0;
    transform: rotate(45deg);
}
.account-dropdown-panel nav {
    background: white;
    border: 1px solid #e9eaf0;
    border-radius: 3px;
    box-shadow: 0 8px 24px rgb(32 33 42 / 10%);
    overflow: hidden;
}
.account-dropdown-panel a,
.account-dropdown-panel button {
    display: flex;
    width: 100%;
    align-items: center;
    justify-content: center;
    min-height: 51px;
    padding: 12px 18px;
    box-sizing: border-box;
    color: #20212a;
    font: inherit;
    font-size: 14px;
    text-decoration: none;
    border: 0;
    border-bottom: 1px solid #f0f1f5;
    border-radius: 0;
    background: white;
    cursor: pointer;
}
.account-dropdown-panel nav > :last-child {
    border-bottom: 0;
}
.account-dropdown-panel a:hover,
.account-dropdown-panel button:hover {
    color: #eb1761;
    background: #fff5f8;
}
.account-dropdown-panel button:disabled {
    cursor: wait;
    color: #8a8f9d;
}
.account-trigger:focus-visible,
.account-dropdown-panel a:focus-visible,
.account-dropdown-panel button:focus-visible {
    outline: 2px solid #eb1761;
    outline-offset: -3px;
}
@media (max-width: 700px) {
    .account-trigger-name {
        max-width: 90px;
    }
    .account-dropdown-panel {
        right: 0;
    }
}
@media (prefers-reduced-motion: reduce) {
    .account-trigger > svg {
        transition: none;
    }
}
</style>
