<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminRequest } from '../api'
import AdminPager from '../components/AdminPager.vue'
import AdminModal from '../components/AdminModal.vue'
interface User {
    id: string
    username: string
    email: string
    phone?: string
    nickname?: string
    realName?: string
    gender?: number
    birthday?: string
    avatar?: string
    status: number
    role: string
    createTime?: string
    lastLoginTime?: string
}
const route = useRoute()
const router = useRouter()
const filters = reactive({ username: '', email: '', phone: '', role: '', status: '' })
const records = ref<User[]>([])
const selected = ref<string[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const message = ref('')
const detail = ref<User | null>(null)
const editing = ref(false)
const form = reactive({
    username: '',
    email: '',
    phone: '',
    nickname: '',
    realName: '',
    gender: 0,
    birthday: '',
    avatar: '',
})
const confirmation = ref<{ ids: string[]; kind: 'enable' | 'disable' | 'delete' } | null>(null)
const editable = (user: User) => ['USER', 'VIP'].includes(user.role)
const canEdit = computed(() => detail.value && editable(detail.value))
const life = new AbortController()
const isActive = () => !life.signal.aborted && route.path === '/admin/users'
let controller: AbortController | undefined
onBeforeUnmount(() => {
    life.abort()
    controller?.abort()
})
// 离开页面或切换筛选后，旧请求不得覆盖当前列表。
async function load() {
    if (!isActive()) {
        return
    }
    controller?.abort()
    const current = new AbortController()
    controller = current
    loading.value = true
    error.value = ''
    selected.value = []
    try {
        const value = await adminRequest<{ records: User[]; total: number }>(
            'get',
            '/admin/user',
            undefined,
            { ...filters, page: page.value, size: 20 },
            current.signal,
        )
        if (!isActive() || current.signal.aborted) {
            return
        }
        records.value = value.records
        total.value = value.total
    } catch (e) {
        if (isActive() && !current.signal.aborted) {
            error.value = e instanceof Error ? e.message : '用户加载失败，请重试。'
        }
    } finally {
        if (isActive() && !current.signal.aborted) {
            loading.value = false
        }
    }
}
watch(
    () => route.query,
    (q) => {
        for (const key of Object.keys(filters) as (keyof typeof filters)[]) {
            filters[key] = String(q[key] || '')
        }
        page.value = Math.max(1, Number(q.page) || 1)
        load()
    },
    { immediate: true },
)
function search(next = 1) {
    router.replace({ query: { ...filters, page: next } })
}
async function open(user: User, edit = false) {
    if (!isActive() || busy.value) {
        return
    }
    busy.value = true
    error.value = ''
    try {
        const value = await adminRequest<User>(
            'get',
            `/admin/user/${user.id}`,
            undefined,
            undefined,
            life.signal,
        )
        if (!isActive()) {
            return
        }
        detail.value = { ...user, ...value }
        editing.value = edit && editable(detail.value)
        for (const key of Object.keys(form) as (keyof typeof form)[]) {
            Object.assign(form, { [key]: detail.value[key] ?? (key === 'gender' ? 0 : '') })
        }
    } catch (e) {
        if (isActive()) {
            error.value = e instanceof Error ? e.message : '详情加载失败。'
        }
    } finally {
        if (isActive()) {
            busy.value = false
        }
    }
}
// 显式提交资料白名单，不携带账户权限或凭据字段。
async function save() {
    if (!isActive() || !detail.value || !canEdit.value || busy.value) {
        return
    }
    busy.value = true
    error.value = ''
    try {
        await adminRequest(
            'put',
            `/admin/user/${detail.value.id}`,
            { ...form, phone: form.phone || null, birthday: form.birthday || null },
            undefined,
            life.signal,
        )
        if (!isActive()) {
            return
        }
        detail.value = null
        message.value = '用户资料已保存。'
        await load()
    } catch (e) {
        if (isActive()) {
            error.value = e instanceof Error ? e.message : '资料保存失败，请检查后重试。'
        }
    } finally {
        if (isActive()) {
            busy.value = false
        }
    }
}
function ask(kind: 'enable' | 'disable' | 'delete', ids = [...selected.value]) {
    if (ids.length) {
        error.value = ''
        confirmation.value = { kind, ids }
    }
}
// 仅在页面仍活动时提交，失败保留确认内容供核对。
async function execute() {
    if (!isActive() || !confirmation.value || busy.value) {
        return
    }
    const { kind, ids } = confirmation.value
    busy.value = true
    error.value = ''
    try {
        if (kind === 'delete') {
            await adminRequest(
                'delete',
                ids.length === 1 ? `/admin/user/${ids[0]}` : '/admin/user/batch',
                undefined,
                ids.length === 1 ? undefined : { ids: ids.join(',') },
                life.signal,
            )
        } else {
            await adminRequest(
                'put',
                ids.length === 1 ? `/admin/user/${ids[0]}/status` : '/admin/user/batch/status',
                undefined,
                {
                    ...(ids.length > 1 ? { ids: ids.join(',') } : {}),
                    status: kind === 'enable' ? 1 : 0,
                },
                life.signal,
            )
        }
        if (!isActive()) {
            return
        }
        confirmation.value = null
        await load()
        if (!isActive()) {
            return
        }
        message.value = '用户操作已完成，列表已刷新。'
    } catch (e) {
        if (isActive()) {
            error.value = e instanceof Error ? e.message : '用户操作失败，请核对后重试。'
        }
    } finally {
        if (isActive()) {
            busy.value = false
        }
    }
}
</script>
<template>
    <section class="admin-page">
        <header class="admin-heading">
            <div>
                <h1>用户管理</h1>
                <p class="admin-muted">管理普通用户资料与账户状态，管理员账户仅可查看。</p>
            </div>
            <button class="admin-btn" :disabled="loading" @click="load">刷新用户</button>
        </header>
        <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
        <p v-if="message" role="status" class="admin-message success">{{ message }}</p>
        <form class="admin-filters" @submit.prevent="search()">
            <label
                v-for="(label, key) in { username: '用户名', email: '邮箱', phone: '手机号' }"
                :key="key"
                class="admin-field"
            >
                {{ label }}
                <input v-model="filters[key]" />
            </label>
            <label class="admin-field">
                角色
                <select v-model="filters.role">
                    <option value="">全部角色</option>
                    <option v-for="role in ['USER', 'VIP', 'ADMIN', 'SUPER_ADMIN']" :key="role">
                        {{ role }}
                    </option>
                </select>
            </label>
            <label class="admin-field">
                状态
                <select v-model="filters.status">
                    <option value="">全部状态</option>
                    <option value="1">启用</option>
                    <option value="0">禁用</option>
                </select>
            </label>
            <button class="admin-btn primary">查询用户</button>
        </form>
        <div class="admin-actions">
            <span class="admin-muted">已选 {{ selected.length }} 个普通用户</span>
            <button class="admin-btn" :disabled="!selected.length || busy" @click="ask('enable')">
                批量启用
            </button>
            <button class="admin-btn" :disabled="!selected.length || busy" @click="ask('disable')">
                批量禁用
            </button>
            <button
                class="admin-btn danger"
                :disabled="!selected.length || busy"
                @click="ask('delete')"
            >
                批量删除
            </button>
        </div>
        <p v-if="loading" role="status">正在加载用户…</p>
        <div class="admin-table-scroll">
            <table class="admin-table">
                <thead>
                    <tr>
                        <th>选择</th>
                        <th>用户</th>
                        <th>联系方式</th>
                        <th>角色</th>
                        <th>状态</th>
                        <th>注册时间</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="user in records" :key="user.id">
                        <td>
                            <input
                                v-model="selected"
                                type="checkbox"
                                :value="user.id"
                                :disabled="!editable(user) || busy"
                                :aria-label="`选择用户${user.username}`"
                            />
                        </td>
                        <td>
                            <strong>{{ user.nickname || user.username }}</strong>
                            <div class="admin-muted">{{ user.username }} / {{ user.id }}</div>
                        </td>
                        <td>
                            {{ user.email }}
                            <div class="admin-muted">{{ user.phone || '—' }}</div>
                        </td>
                        <td>
                            <span class="admin-badge">{{ user.role }}</span>
                        </td>
                        <td>{{ user.status === 1 ? '启用' : '禁用' }}</td>
                        <td>{{ user.createTime || '—' }}</td>
                        <td>
                            <div class="admin-actions">
                                <button
                                    class="admin-btn small"
                                    :disabled="busy"
                                    @click="open(user)"
                                >
                                    详情
                                </button>
                                <template v-if="editable(user)">
                                    <button
                                        class="admin-btn small"
                                        :disabled="busy"
                                        @click="open(user, true)"
                                    >
                                        编辑
                                    </button>
                                    <button
                                        class="admin-btn small"
                                        :disabled="busy"
                                        @click="
                                            ask(user.status === 1 ? 'disable' : 'enable', [user.id])
                                        "
                                    >
                                        {{ user.status === 1 ? '禁用' : '启用' }}
                                    </button>
                                    <button
                                        class="admin-btn small danger"
                                        :disabled="busy"
                                        @click="ask('delete', [user.id])"
                                    >
                                        删除
                                    </button>
                                </template>
                                <span v-else class="admin-muted">只读账户</span>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
            <p v-if="!loading && !records.length" class="admin-empty">
                暂无符合条件的用户，调整筛选后重试。
            </p>
        </div>
        <AdminPager :page="page" :size="20" :total="total" @change="search" />
        <AdminModal
            :open="!!detail"
            :title="editing ? '编辑用户资料' : '用户详情'"
            :busy="busy"
            @close="detail = null"
        >
            <form v-if="editing && canEdit" class="admin-form" @submit.prevent="save">
                <p class="admin-muted">仅修改个人资料，角色与密码由独立账户流程管理。</p>
                <div class="admin-grid">
                    <label
                        v-for="(label, key) in {
                            username: '用户名',
                            email: '邮箱',
                            phone: '手机号',
                            nickname: '昵称',
                            realName: '真实姓名',
                        }"
                        :key="key"
                        class="admin-field"
                    >
                        {{ label }}
                        <input
                            v-model="form[key]"
                            :type="key === 'email' ? 'email' : 'text'"
                            :required="key === 'username' || key === 'email'"
                            :maxlength="
                                key === 'username' || key === 'nickname'
                                    ? 20
                                    : key === 'realName'
                                      ? 10
                                      : 100
                            "
                            :minlength="key === 'username' ? 3 : undefined"
                            :disabled="busy"
                        />
                    </label>
                    <label class="admin-field">
                        性别
                        <select v-model.number="form.gender" :disabled="busy">
                            <option :value="0">未知</option>
                            <option :value="1">男</option>
                            <option :value="2">女</option>
                        </select>
                    </label>
                    <label class="admin-field">
                        生日
                        <input v-model="form.birthday" type="date" :disabled="busy" />
                    </label>
                </div>
                <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
                <button class="admin-btn primary" :disabled="busy">
                    {{ busy ? '正在保存…' : '保存资料' }}
                </button>
            </form>
            <dl v-else-if="detail" class="user-detail">
                <div
                    v-for="(label, key) in {
                        username: '用户名',
                        nickname: '昵称',
                        email: '邮箱',
                        phone: '手机号',
                        realName: '真实姓名',
                        birthday: '生日',
                        role: '角色',
                        createTime: '注册时间',
                        lastLoginTime: '最近登录',
                    }"
                    :key="key"
                >
                    <dt>{{ label }}</dt>
                    <dd>{{ detail[key] || '—' }}</dd>
                </div>
                <div>
                    <dt>账户状态</dt>
                    <dd>{{ detail.status === 1 ? '启用' : '禁用' }}</dd>
                </div>
            </dl>
        </AdminModal>
        <AdminModal
            :open="!!confirmation"
            title="确认用户操作"
            :busy="busy"
            @close="confirmation = null"
        >
            <p>
                将{{
                    confirmation?.kind === 'delete'
                        ? '删除'
                        : confirmation?.kind === 'enable'
                          ? '启用'
                          : '禁用'
                }}所选 {{ confirmation?.ids.length }} 个普通用户。{{
                    confirmation?.kind === 'delete'
                        ? '删除会影响账户访问，请确认对象无误。'
                        : '账户访问权限将随状态改变。'
                }}
            </p>
            <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
            <button class="admin-btn danger" :disabled="busy" @click="execute">
                {{ busy ? '正在提交…' : '确认操作' }}
            </button>
        </AdminModal>
    </section>
</template>
<style scoped>
.user-detail {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 20px;
}
.user-detail dt {
    font-size: 13px;
    color: #64748b;
}
.user-detail dd {
    margin: 8px 0 0;
    overflow-wrap: anywhere;
}
</style>
