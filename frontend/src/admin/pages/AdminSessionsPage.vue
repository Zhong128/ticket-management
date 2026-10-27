<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { adminRequest } from '../api'
import type { Event, Session, Page } from '../../api/types'
import AdminPager from '../components/AdminPager.vue'
import AdminModal from '../components/AdminModal.vue'
import { useAdminTask, timeText, allOptions } from './contentSupport'

const route = useRoute()
const router = useRouter()
const { busy, error, run } = useAdminTask()
const records = ref<Session[]>([])
const events = ref<Event[]>([])
const total = ref(0)
const page = ref(1)
const eventId = ref('')
const status = ref('')
const startTime = ref('')
const endTime = ref('')
const open = ref(false)
const selected = ref('')
const removing = ref<Session | null>(null)
const form = ref({ eventId: '', sessionName: '', sessionTime: '', status: 1 })

async function load() {
    eventId.value = String(route.query.eventId || '')
    status.value = String(route.query.status ?? '')
    page.value = Math.max(1, Number(route.query.page) || 1)
    startTime.value = String(route.query.startTime || '')
    endTime.value = String(route.query.endTime || '')
    await run(
        async (signal) => {
            const [result, options] = await Promise.all([
                adminRequest<Page<Session>>(
                    'get',
                    '/admin/sessions/page',
                    undefined,
                    {
                        eventId: eventId.value || undefined,
                        status: status.value || undefined,
                        startTime: startTime.value || undefined,
                        endTime: endTime.value || undefined,
                        page: page.value,
                        size: 10,
                    },
                    signal,
                ),
                allOptions<Event>('/admin/events/query', signal),
            ])
            return { result, options }
        },
        ({ result, options }) => {
            records.value = result.records
            total.value = result.total
            events.value = options
        },
    )
}

function search(next = 1) {
    if (startTime.value && endTime.value && startTime.value > endTime.value) {
        error.value = '开始时间不能晚于结束时间'
        return
    }
    router.push({
        query: {
            eventId: eventId.value || undefined,
            status: status.value || undefined,
            startTime: startTime.value || undefined,
            endTime: endTime.value || undefined,
            page: next,
        },
    })
}

function create() {
    error.value = ''
    selected.value = ''
    form.value = { eventId: eventId.value, sessionName: '', sessionTime: '', status: 1 }
    open.value = true
}

async function edit(row: Session) {
    if (busy.value) {
        return
    }
    await run(
        (signal) =>
            adminRequest<Session>('get', `/admin/sessions/${row.id}`, undefined, undefined, signal),
        (detail) => {
            selected.value = row.id
            form.value = {
                eventId: detail.eventId,
                sessionName: detail.sessionName,
                sessionTime: detail.sessionTime.replace(' ', 'T'),
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
    await run(
        (signal) =>
            adminRequest(
                selected.value ? 'put' : 'post',
                selected.value ? `/admin/sessions/${selected.value}` : '/admin/sessions',
                { ...form.value },
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
                `/admin/sessions/${removing.value!.id}`,
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
                <h1>场次管理</h1>
                <p class="admin-muted">按演出维护排期，已下架场次保留历史记录。</p>
            </div>
            <button class="admin-btn primary" :disabled="busy" @click="create">新增场次</button>
        </div>
        <form class="admin-filters" @submit.prevent="search()">
            <label class="admin-field">
                演出
                <select v-model="eventId">
                    <option value="">全部演出</option>
                    <option v-for="event in events" :key="event.id" :value="event.id">
                        {{ event.name }}
                    </option>
                    <option
                        v-if="eventId && !events.some((e) => e.id === eventId)"
                        :value="eventId"
                    >
                        演出 {{ eventId }}
                    </option>
                </select>
            </label>
            <label class="admin-field">
                状态
                <select v-model="status">
                    <option value="">全部状态</option>
                    <option value="1">已上架</option>
                    <option value="0">已下架</option>
                </select>
            </label>
            <label class="admin-field">
                开始时间
                <input v-model="startTime" type="datetime-local" />
            </label>
            <label class="admin-field">
                结束时间
                <input v-model="endTime" type="datetime-local" />
            </label>
            <button class="admin-btn" :disabled="busy">查询</button>
        </form>
        <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
        <p v-if="busy" role="status">正在处理…</p>
        <div class="admin-table-scroll">
            <table class="admin-table">
                <thead>
                    <tr>
                        <th>场次</th>
                        <th>所属演出</th>
                        <th>时间</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="row in records" :key="row.id">
                        <td>{{ row.sessionName }}</td>
                        <td>
                            <RouterLink :to="`/admin/events/${row.eventId}`">
                                {{ events.find((e) => e.id === row.eventId)?.name || row.eventId }}
                            </RouterLink>
                        </td>
                        <td>{{ timeText(row.sessionTime) }}</td>
                        <td>
                            <span class="admin-badge">
                                {{ row.status === 1 ? '已上架' : '已下架' }}
                            </span>
                        </td>
                        <td>
                            <div class="admin-actions">
                                <button class="admin-btn small" :disabled="busy" @click="edit(row)">
                                    {{ row.status === 0 ? '编辑 / 恢复' : '编辑' }}
                                </button>
                                <RouterLink
                                    :to="{
                                        path: '/admin/ticket-tiers',
                                        query: { eventId: row.eventId, sessionId: row.id },
                                    }"
                                >
                                    票档
                                </RouterLink>
                                <button
                                    v-if="row.status === 1"
                                    class="admin-btn small danger"
                                    :disabled="busy"
                                    @click="removing = row"
                                >
                                    下架
                                </button>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <p v-if="!busy && !records.length" class="admin-empty">暂无符合条件的场次。</p>
        <AdminPager :page="page" :size="10" :total="total" @change="search" />
        <AdminModal
            :open="open"
            :title="selected ? '编辑场次' : '新增场次'"
            :busy="busy"
            @close="open = false"
        >
            <form class="admin-form" @submit.prevent="save">
                <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
                <label class="admin-field">
                    所属演出
                    <select v-model="form.eventId" required>
                        <option value="" disabled>选择演出</option>
                        <option v-for="event in events" :key="event.id" :value="event.id">
                            {{ event.name }}
                        </option>
                        <option
                            v-if="form.eventId && !events.some((e) => e.id === form.eventId)"
                            :value="form.eventId"
                        >
                            当前演出 {{ form.eventId }}
                        </option>
                    </select>
                </label>
                <label class="admin-field">
                    场次名称
                    <input v-model="form.sessionName" required maxlength="100" />
                </label>
                <label class="admin-field">
                    场次时间
                    <input v-model="form.sessionTime" required type="datetime-local" />
                </label>
                <label class="admin-field">
                    状态
                    <select v-model.number="form.status">
                        <option :value="1">已上架</option>
                        <option :value="0">已下架</option>
                    </select>
                </label>
                <button class="admin-btn primary" :disabled="busy">保存场次</button>
            </form>
        </AdminModal>
        <AdminModal :open="!!removing" title="下架场次" :busy="busy" @close="removing = null">
            <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
            <p>下架“{{ removing?.sessionName }}”后将保留历史记录。</p>
            <button class="admin-btn danger" :disabled="busy" @click="remove">确认下架</button>
        </AdminModal>
    </section>
</template>
