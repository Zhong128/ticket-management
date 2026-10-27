<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminRequest } from '../api'
import type { Event, Session, Tier, Page } from '../../api/types'
import AdminPager from '../components/AdminPager.vue'
import AdminModal from '../components/AdminModal.vue'
import { useAdminTask, allOptions } from './contentSupport'

interface AdminTier extends Tier {
    eventId: string
    eventName?: string
    sessionName?: string
    originalPrice: number
    totalStock: number
}
interface SoldOutTier {
    tierId: string
    tierName: string
    price: number
    totalStock: number
    availableStock: number
    status: number
}
type TierRow = Omit<AdminTier, 'originalPrice'> & { originalPrice?: number }

interface Statistics {
    totalTiers: number
    totalStock: number
    totalAvailable: number
    totalSold: number
}
const route = useRoute()
const router = useRouter()
const { busy, error, run } = useAdminTask()
const records = ref<TierRow[]>([])
const events = ref<Event[]>([])
const sessions = ref<Session[]>([])
const total = ref(0)
const page = ref(1)
const eventId = ref('')
const sessionId = ref('')
const status = ref('')
const soldOutOnly = ref(false)
const displayedStatus = computed({
    get: () => (soldOutOnly.value ? '1' : status.value),
    set: (value: string) => {
        status.value = value
    },
})
const minPrice = ref('')
const maxPrice = ref('')
const statistics = ref<Statistics | null>(null)
const open = ref(false)
const selected = ref('')
const removing = ref<TierRow | null>(null)
const adjusting = ref<TierRow | null>(null)
const delta = ref(0)
const uncertain = ref(false)
const form = ref({
    eventId: '',
    sessionId: '',
    tierName: '',
    originalPrice: 0,
    currentPrice: 0,
    totalStock: 0,
    availableStock: 0,
    status: 0,
})

async function load() {
    eventId.value = String(route.query.eventId || '')
    sessionId.value = String(route.query.sessionId || '')
    status.value = String(route.query.status ?? '')
    soldOutOnly.value = route.query.soldOutOnly === '1'
    page.value = Math.max(1, Number(route.query.page) || 1)
    minPrice.value = String(route.query.minPrice || '')
    maxPrice.value = String(route.query.maxPrice || '')
    await run(
        async (signal) => {
            const params = {
                eventId: eventId.value || undefined,
                sessionId: sessionId.value || undefined,
                status: status.value || undefined,
                minPrice: minPrice.value || undefined,
                maxPrice: maxPrice.value || undefined,
                page: page.value,
                size: 10,
            }
            const [result, options, related, stock] = await Promise.all([
                queryTiers(signal, params),
                allOptions<Event>('/admin/events/query', signal),
                allOptions<Session>('/admin/sessions/page', signal, {
                    eventId: eventId.value || undefined,
                }),
                adminRequest<Statistics>(
                    'get',
                    '/admin/ticket-tiers/stock-statistics',
                    undefined,
                    {
                        eventId: eventId.value || undefined,
                        sessionId: sessionId.value || undefined,
                    },
                    signal,
                ),
            ])
            return { result, options, related, stock }
        },
        ({ result, options, related, stock }) => {
            records.value = result.records
            total.value = result.total
            events.value = options
            sessions.value = related
            statistics.value = stock
        },
    )
}

async function queryTiers(signal: AbortSignal, params: object) {
    if (!soldOutOnly.value) {
        return adminRequest<Page<AdminTier>>(
            'get',
            '/admin/ticket-tiers/page',
            undefined,
            params,
            signal,
        )
    }

    // 售罄接口返回全部启用票档，按票价过滤后在本地分页。
    const soldOut = await adminRequest<SoldOutTier[]>(
        'get',
        '/admin/ticket-tiers/sold-out',
        undefined,
        { eventId: eventId.value || undefined, sessionId: sessionId.value || undefined },
        signal,
    )
    const filtered = soldOut.filter(
        (tier) =>
            (!minPrice.value || tier.price >= Number(minPrice.value)) &&
            (!maxPrice.value || tier.price <= Number(maxPrice.value)),
    )
    const offset = (page.value - 1) * 10
    const rows: TierRow[] = filtered.slice(offset, offset + 10).map((tier) => ({
        id: tier.tierId,
        tierName: tier.tierName,
        eventId: eventId.value,
        sessionId: sessionId.value,
        currentPrice: tier.price,
        totalStock: tier.totalStock,
        availableStock: tier.availableStock,
        status: 1,
    }))
    return { records: rows, total: filtered.length }
}

function search(next = 1) {
    if (minPrice.value && maxPrice.value && Number(minPrice.value) > Number(maxPrice.value)) {
        error.value = '最低价格不能高于最高价格'
        return
    }
    router.push({
        query: {
            eventId: eventId.value || undefined,
            sessionId: sessionId.value || undefined,
            status: status.value || undefined,
            soldOutOnly: soldOutOnly.value ? '1' : undefined,
            minPrice: minPrice.value || undefined,
            maxPrice: maxPrice.value || undefined,
            page: next,
        },
    })
}

async function loadSessions(id: string) {
    await run(
        (signal) =>
            allOptions<Session>('/admin/sessions/page', signal, { eventId: id || undefined }),
        (result) => {
            sessions.value = result
        },
    )
}

function changeEventFilter() {
    sessionId.value = ''
    void loadSessions(eventId.value)
}

function changeFormEvent() {
    form.value.sessionId = ''
    void loadSessions(form.value.eventId)
}

function create() {
    error.value = ''
    selected.value = ''
    form.value = {
        eventId: eventId.value,
        sessionId: sessionId.value,
        tierName: '',
        originalPrice: 0,
        currentPrice: 0,
        totalStock: 0,
        availableStock: 0,
        status: 0,
    }
    open.value = true
}

async function edit(row: TierRow) {
    if (busy.value) {
        return
    }
    await run(
        async (signal) => {
            const detail = await adminRequest<AdminTier>(
                'get',
                `/admin/ticket-tiers/${row.id}`,
                undefined,
                undefined,
                signal,
            )
            const related = await allOptions<Session>('/admin/sessions/page', signal, {
                eventId: detail.eventId,
            })
            return { detail, related }
        },
        ({ detail, related }) => {
            selected.value = row.id
            form.value = {
                eventId: detail.eventId,
                sessionId: detail.sessionId,
                tierName: detail.tierName,
                originalPrice: detail.originalPrice,
                currentPrice: detail.currentPrice,
                totalStock: detail.totalStock,
                availableStock: detail.availableStock,
                status: detail.status,
            }
            sessions.value = related
            open.value = true
        },
    )
}

async function save() {
    if (busy.value) {
        return
    }
    if (!selected.value && form.value.availableStock > form.value.totalStock) {
        error.value = '可用库存不能超过总库存'
        return
    }
    const { totalStock, availableStock, ...metadata } = form.value
    await run(
        (signal) =>
            adminRequest(
                selected.value ? 'put' : 'post',
                selected.value ? `/admin/ticket-tiers/${selected.value}` : '/admin/ticket-tiers',
                selected.value ? metadata : { ...metadata, totalStock, availableStock },
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
                `/admin/ticket-tiers/${removing.value!.id}`,
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

function stock(row: TierRow) {
    adjusting.value = row
    delta.value = 0
    uncertain.value = false
    error.value = ''
}

async function adjust() {
    if (busy.value) {
        return
    }
    if (!adjusting.value || uncertain.value || !Number.isInteger(delta.value) || !delta.value) {
        error.value = '请输入非零整数调整量'
        return
    }
    await run(
        (signal) =>
            adminRequest(
                'post',
                `/admin/ticket-tiers/${adjusting.value!.id}/stock-adjustments`,
                { delta: delta.value },
                undefined,
                signal,
            ),
        () => {
            adjusting.value = null
        },
    )
    if (error.value) {
        uncertain.value = true
        error.value += '。调整结果可能未知，请关闭窗口并刷新库存核实，不要直接重复提交。'
    } else {
        await load()
    }
}

watch(() => route.fullPath, load, { immediate: true })
</script>
<template>
    <section class="admin-page">
        <div class="admin-heading">
            <div>
                <h1>票档与库存</h1>
                <p class="admin-muted">管理票价、上下架和库存调整。</p>
            </div>
            <button class="admin-btn primary" :disabled="busy" @click="create">新增票档</button>
        </div>
        <form class="admin-filters" @submit.prevent="search()">
            <label class="admin-field">
                演出
                <select v-model="eventId" @change="changeEventFilter">
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
                场次
                <select v-model="sessionId">
                    <option value="">全部场次</option>
                    <option v-for="session in sessions" :key="session.id" :value="session.id">
                        {{ session.sessionName }}
                    </option>
                    <option
                        v-if="sessionId && !sessions.some((s) => s.id === sessionId)"
                        :value="sessionId"
                    >
                        场次 {{ sessionId }}
                    </option>
                </select>
            </label>
            <label class="admin-field">
                状态
                <select v-model="displayedStatus" :disabled="soldOutOnly">
                    <option value="">全部状态</option>
                    <option value="1">已上架</option>
                    <option value="0">已下架</option>
                </select>
            </label>
            <label class="admin-field">
                最低价格
                <input v-model="minPrice" type="number" min="0" step="0.01" />
            </label>
            <label class="admin-field">
                最高价格
                <input v-model="maxPrice" type="number" min="0" step="0.01" />
            </label>
            <label class="sold-out-filter">
                <input v-model="soldOutOnly" type="checkbox" :disabled="busy" @change="search()" />
                仅看售罄
            </label>
            <button class="admin-btn" :disabled="busy">查询</button>
        </form>
        <div v-if="statistics" class="stock-summary">
            <span>
                票档
                <strong>{{ statistics.totalTiers }}</strong>
            </span>
            <span>
                总库存
                <strong>{{ statistics.totalStock }}</strong>
            </span>
            <span>
                可用
                <strong>{{ statistics.totalAvailable }}</strong>
            </span>
            <span>
                占用
                <strong>{{ statistics.totalSold }}</strong>
            </span>
            <small>
                占用 = 总库存 − 可用库存，包含锁定，不等于实际销量。统计范围按演出、场次计算。
            </small>
        </div>
        <p
            v-if="error && !open && !removing && !adjusting"
            role="alert"
            class="admin-message error"
        >
            {{ error }}
        </p>
        <p v-if="busy" role="status">正在处理…</p>
        <div class="admin-table-scroll">
            <table class="admin-table">
                <thead>
                    <tr>
                        <th>票档 / 场次</th>
                        <th>票价</th>
                        <th>总库存</th>
                        <th>可用 / 占用</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="row in records" :key="row.id">
                        <td>
                            {{ row.tierName }}
                            <small>{{ row.eventName }} / {{ row.sessionName }}</small>
                        </td>
                        <td>
                            ¥{{ row.currentPrice }}
                            <small v-if="row.originalPrice !== undefined">
                                原价 ¥{{ row.originalPrice }}
                            </small>
                        </td>
                        <td>{{ row.totalStock }}</td>
                        <td>
                            {{ row.availableStock }} / {{ row.totalStock - row.availableStock }}
                        </td>
                        <td>
                            <span class="admin-badge">
                                {{ row.status === 1 ? '已上架' : '已下架' }}
                            </span>
                        </td>
                        <td>
                            <div class="admin-actions">
                                <button class="admin-btn small" :disabled="busy" @click="edit(row)">
                                    详情 / 编辑
                                </button>
                                <button
                                    class="admin-btn small"
                                    :disabled="busy || row.status !== 0"
                                    @click="stock(row)"
                                >
                                    调整库存
                                </button>
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
        <p v-if="!busy && !records.length" class="admin-empty">暂无符合条件的票档。</p>
        <AdminPager :page="page" :size="10" :total="total" @change="search" />
        <AdminModal
            :open="open"
            :title="selected ? '票档详情 / 编辑' : '新增票档'"
            :busy="busy"
            @close="open = false"
        >
            <form class="admin-form" @submit.prevent="save">
                <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
                <label class="admin-field">
                    演出
                    <select
                        v-model="form.eventId"
                        required
                        :disabled="!!selected"
                        @change="changeFormEvent"
                    >
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
                    场次
                    <select v-model="form.sessionId" required :disabled="!!selected">
                        <option value="" disabled>选择场次</option>
                        <option v-for="session in sessions" :key="session.id" :value="session.id">
                            {{ session.sessionName }}
                        </option>
                        <option
                            v-if="form.sessionId && !sessions.some((s) => s.id === form.sessionId)"
                            :value="form.sessionId"
                        >
                            当前场次 {{ form.sessionId }}
                        </option>
                    </select>
                </label>
                <label class="admin-field">
                    票档名称
                    <input v-model="form.tierName" required maxlength="100" />
                </label>
                <div class="admin-grid">
                    <label class="admin-field">
                        原价
                        <input
                            v-model.number="form.originalPrice"
                            type="number"
                            min="0"
                            max="9999999999.99"
                            step="0.01"
                            required
                        />
                    </label>
                    <label class="admin-field">
                        现价
                        <input
                            v-model.number="form.currentPrice"
                            type="number"
                            min="0"
                            max="9999999999.99"
                            step="0.01"
                            required
                        />
                    </label>
                    <label class="admin-field">
                        总库存
                        <input
                            v-model.number="form.totalStock"
                            type="number"
                            min="0"
                            step="1"
                            :disabled="!!selected"
                            required
                        />
                    </label>
                    <label class="admin-field">
                        可用库存
                        <input
                            v-model.number="form.availableStock"
                            type="number"
                            min="0"
                            max="100000"
                            step="1"
                            :disabled="!!selected"
                            required
                        />
                    </label>
                </div>
                <p v-if="selected" class="admin-muted">
                    编辑不修改库存。调整库存前请先下架票档，并确保没有未结订单；系统会再次校验。
                </p>
                <label class="admin-field">
                    状态
                    <select v-model.number="form.status">
                        <option :value="0">已下架</option>
                        <option :value="1">已上架</option>
                    </select>
                </label>
                <button class="admin-btn primary" :disabled="busy">保存票档</button>
            </form>
        </AdminModal>
        <AdminModal :open="!!removing" title="下架票档" :busy="busy" @close="removing = null">
            <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
            <p>确定下架“{{ removing?.tierName }}”？历史订单将保留。</p>
            <button class="admin-btn danger" :disabled="busy" @click="remove">确认下架</button>
        </AdminModal>
        <AdminModal :open="!!adjusting" title="调整库存" :busy="busy" @close="adjusting = null">
            <form class="admin-form" @submit.prevent="adjust">
                <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
                <p>{{ adjusting?.tierName }} · 当前可用库存 {{ adjusting?.availableStock }}</p>
                <p class="admin-muted">
                    仅允许已下架且没有未结订单的票档调整。输入正数增加库存，负数减少库存。
                </p>
                <label class="admin-field">
                    库存调整量
                    <input
                        v-model.number="delta"
                        type="number"
                        step="1"
                        required
                        :disabled="uncertain"
                    />
                </label>
                <button class="admin-btn primary" :disabled="busy || uncertain || !delta">
                    确认调整
                </button>
            </form>
        </AdminModal>
    </section>
</template>
<style scoped>
.sold-out-filter {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 40px;
    white-space: nowrap;
}
.sold-out-filter input {
    accent-color: #eb1761;
}

.stock-summary {
    display: flex;
    gap: 28px;
    flex-wrap: wrap;
    padding: 22px 0;
    border-block: 1px solid #eceef2;
    margin: 24px 0;
}
.stock-summary strong {
    font-size: 21px;
    margin-left: 8px;
}
.stock-summary small {
    flex-basis: 100%;
    color: #777d8b;
}
td small {
    display: block;
    color: #777d8b;
    margin-top: 6px;
}
</style>
