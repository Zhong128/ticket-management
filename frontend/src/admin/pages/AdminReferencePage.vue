<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminRequest } from '../api'
import type { Page } from '../../api/types'
import AdminPager from '../components/AdminPager.vue'
import AdminModal from '../components/AdminModal.vue'
import { useAdminTask } from './contentSupport'

interface Reference {
    id: string
    name: string
    status: number
    code?: string
    province?: string
    hotLevel?: number
    sortOrder?: number
}
const route = useRoute()
const router = useRouter()
const isCity = computed(() => route.path.endsWith('/cities'))
const label = computed(() => (isCity.value ? '城市' : '分类'))
const endpoint = computed(() => (isCity.value ? '/admin/cities' : '/admin/categories'))
const { busy, error, run } = useAdminTask()
const records = ref<Reference[]>([])
const total = ref(0)
const page = ref(1)
const name = ref('')
const status = ref('')
const open = ref(false)
const selected = ref('')
const removing = ref<Reference | null>(null)
const form = ref({ name: '', code: '', province: '', hotLevel: 0, sortOrder: 0, status: 1 })

async function load() {
    open.value = false
    removing.value = null
    name.value = String(route.query.name || '')
    status.value = String(route.query.status ?? '')
    page.value = Math.max(1, Number(route.query.page) || 1)
    await run(
        (signal) =>
            adminRequest<Page<Reference>>(
                'get',
                `${endpoint.value}/query`,
                undefined,
                {
                    name: name.value || undefined,
                    status: status.value || undefined,
                    page: page.value,
                    size: 10,
                },
                signal,
            ),
        (result) => {
            records.value = result.records
            total.value = result.total
        },
    )
}

function search(next = 1) {
    router.push({
        query: { name: name.value || undefined, status: status.value || undefined, page: next },
    })
}

function create() {
    error.value = ''
    selected.value = ''
    form.value = { name: '', code: '', province: '', hotLevel: 0, sortOrder: 0, status: 1 }
    open.value = true
}

async function edit(row: Reference) {
    if (busy.value) {
        return
    }
    await run(
        (signal) =>
            adminRequest<Reference>(
                'get',
                `${endpoint.value}/${row.id}`,
                undefined,
                undefined,
                signal,
            ),
        (detail) => {
            selected.value = row.id
            form.value = {
                name: detail.name,
                code: detail.code || '',
                province: detail.province || '',
                hotLevel: detail.hotLevel || 0,
                sortOrder: detail.sortOrder || 0,
                status: detail.status,
            }
            open.value = true
        },
    )
}

async function save() {
    if (busy.value) {
        return
    }
    const payload = isCity.value
        ? {
              name: form.value.name,
              code: form.value.code,
              province: form.value.province,
              hotLevel: form.value.hotLevel,
              status: form.value.status,
          }
        : { name: form.value.name, sortOrder: form.value.sortOrder, status: form.value.status }
    await run(
        (signal) =>
            adminRequest(
                selected.value ? 'put' : 'post',
                `${endpoint.value}${selected.value ? `/${selected.value}` : ''}`,
                payload,
                undefined,
                signal,
            ),
        () => {
            open.value = false
        },
    )
    if (!error.value) {
        await load()
    }
}

async function remove() {
    if (busy.value) {
        return
    }
    if (!removing.value) {
        return
    }
    await run(
        (signal) =>
            adminRequest(
                'delete',
                `${endpoint.value}/${removing.value!.id}`,
                undefined,
                undefined,
                signal,
            ),
        () => {
            removing.value = null
        },
    )
    if (!error.value) {
        await load()
    }
}

watch(() => route.fullPath, load, { immediate: true })
</script>
<template>
    <section class="admin-page">
        <div class="admin-heading">
            <div>
                <h1>{{ label }}管理</h1>
                <p class="admin-muted">维护演出的{{ label }}选项，停用后保留已有引用。</p>
            </div>
            <button class="admin-btn primary" :disabled="busy" @click="create">
                新增{{ label }}
            </button>
        </div>
        <form class="admin-filters" @submit.prevent="search()">
            <label class="admin-field">
                {{ label }}名称
                <input v-model="name" />
            </label>
            <label class="admin-field">
                状态
                <select v-model="status">
                    <option value="">全部状态</option>
                    <option value="1">已启用</option>
                    <option value="0">已停用</option>
                </select>
            </label>
            <button class="admin-btn" :disabled="busy">查询</button>
        </form>
        <p v-if="error && !open && !removing" role="alert" class="admin-message error">
            {{ error }}
        </p>
        <p v-if="busy" role="status">正在处理…</p>
        <div class="admin-table-scroll">
            <table class="admin-table">
                <thead>
                    <tr>
                        <th>{{ label }}名称</th>
                        <template v-if="isCity">
                            <th>代码</th>
                            <th>省份</th>
                            <th>热门</th>
                        </template>
                        <th v-else>排序</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="row in records" :key="row.id">
                        <td>{{ row.name }}</td>
                        <template v-if="isCity">
                            <td>{{ row.code }}</td>
                            <td>{{ row.province }}</td>
                            <td>{{ row.hotLevel === 1 ? '热门' : '普通' }}</td>
                        </template>
                        <td v-else>{{ row.sortOrder }}</td>
                        <td>
                            <span class="admin-badge">
                                {{ row.status === 1 ? '已启用' : '已停用' }}
                            </span>
                        </td>
                        <td>
                            <div class="admin-actions">
                                <button class="admin-btn small" :disabled="busy" @click="edit(row)">
                                    编辑 / {{ row.status === 1 ? '停用' : '启用' }}
                                </button>
                                <button
                                    class="admin-btn small danger"
                                    :disabled="busy"
                                    @click="removing = row"
                                >
                                    删除
                                </button>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <p v-if="!busy && !records.length" class="admin-empty">暂无符合条件的{{ label }}。</p>
        <AdminPager :page="page" :size="10" :total="total" @change="search" />
        <AdminModal
            :open="open"
            :title="`${selected ? '编辑' : '新增'}${label}`"
            :busy="busy"
            @close="open = false"
        >
            <form class="admin-form" @submit.prevent="save">
                <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
                <label class="admin-field">
                    {{ label }}名称
                    <input
                        v-model="form.name"
                        required
                        :minlength="isCity ? 2 : undefined"
                        :maxlength="isCity ? 20 : undefined"
                    />
                </label>
                <template v-if="isCity">
                    <label class="admin-field">
                        城市代码
                        <input
                            v-model="form.code"
                            required
                            pattern="[A-Z_]+"
                            maxlength="50"
                            placeholder="大写字母和下划线"
                        />
                    </label>
                    <label class="admin-field">
                        省份
                        <input v-model="form.province" required minlength="2" maxlength="20" />
                    </label>
                    <label class="admin-field">
                        热门等级
                        <select v-model.number="form.hotLevel">
                            <option :value="0">普通</option>
                            <option :value="1">热门</option>
                        </select>
                    </label>
                </template>
                <label v-else class="admin-field">
                    排序值
                    <input v-model.number="form.sortOrder" type="number" step="1" required />
                </label>
                <label class="admin-field">
                    状态
                    <select v-model.number="form.status">
                        <option :value="1">启用</option>
                        <option :value="0">停用</option>
                    </select>
                </label>
                <button class="admin-btn primary" :disabled="busy">保存{{ label }}</button>
            </form>
        </AdminModal>
        <AdminModal
            :open="!!removing"
            :title="`删除${label}`"
            :busy="busy"
            @close="removing = null"
        >
            <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
            <p>
                确定删除“{{
                    removing?.name
                }}”？已有演出引用时，系统可能拒绝删除。可改为停用以保留引用。
            </p>
            <button class="admin-btn danger" :disabled="busy" @click="remove">确认删除</button>
        </AdminModal>
    </section>
</template>
