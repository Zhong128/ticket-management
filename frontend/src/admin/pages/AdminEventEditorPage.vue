<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import type { Event } from '../../api/types'
import { adminRequest } from '../api'
import PosterImage from '../../components/PosterImage.vue'
import { useAdminTask, referenceOptions, safeCover } from './contentSupport'

const route = useRoute()
const router = useRouter()
const { busy, error, run } = useAdminTask()
const cities = ref<{ id: string; name: string }[]>([])
const categories = ref<{ id: string; name: string }[]>([])
const form = ref({
    name: '',
    artistName: '',
    cityId: '',
    categoryId: '',
    venue: '',
    coverImage: '',
    description: '',
    status: 0,
    saleStartTime: '',
    saleEndTime: '',
    eventStartTime: '',
    eventEndTime: '',
})
const loaded = ref(false)
const saved = ref(false)
const timeFields = [
    { key: 'saleStartTime', label: '开售时间' },
    { key: 'saleEndTime', label: '停售时间' },
    { key: 'eventStartTime', label: '演出开始时间' },
    { key: 'eventEndTime', label: '演出结束时间' },
] as const

watch(
    () => route.params.id,
    () => {
        loaded.value = false
        form.value = {
            name: '',
            artistName: '',
            cityId: '',
            categoryId: '',
            venue: '',
            coverImage: '',
            description: '',
            status: 0,
            saleStartTime: '',
            saleEndTime: '',
            eventStartTime: '',
            eventEndTime: '',
        }
        run(
            async (signal) => {
                const options = await referenceOptions(signal)
                const event = route.params.id
                    ? await adminRequest<Event>(
                          'get',
                          `/admin/events/${route.params.id}`,
                          undefined,
                          undefined,
                          signal,
                      )
                    : null
                return { options, event }
            },
            ({ options, event }) => {
                cities.value = options.cities
                categories.value = options.categories
                if (event) {
                    for (const key of Object.keys(form.value) as (keyof typeof form.value)[]) {
                        ;(form.value as Record<string, unknown>)[key] = event[key] ?? ''
                    }
                }
                for (const field of timeFields) {
                    form.value[field.key] = form.value[field.key].replace(' ', 'T')
                }
                loaded.value = true
            },
        )
    },
    { immediate: true },
)

async function save() {
    if (busy.value) {
        return
    }
    const f = form.value
    if (
        f.saleStartTime >= f.saleEndTime ||
        f.eventStartTime >= f.eventEndTime ||
        f.saleEndTime > f.eventStartTime
    ) {
        error.value = '请检查时间：开售早于停售，演出开始早于结束，停售不晚于演出开始。'
        return
    }
    await run(
        (signal) =>
            adminRequest(
                route.params.id ? 'put' : 'post',
                route.params.id ? `/admin/events/${route.params.id}` : '/admin/events',
                { ...f },
                undefined,
                signal,
            ),
        () => {
            saved.value = true
            router.push('/admin/events')
        },
    )
}
</script>
<template>
    <section class="admin-page">
        <div class="admin-heading">
            <h1>{{ route.params.id ? '编辑演出' : '新增演出' }}</h1>
            <RouterLink class="admin-btn" to="/admin/events">返回演出列表</RouterLink>
        </div>
        <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
        <p v-if="busy && !loaded" role="status">正在加载…</p>
        <form v-if="loaded" class="admin-form editor" @submit.prevent="save">
            <div class="editor-main">
                <h2>基本信息</h2>
                <label class="admin-field">
                    演出名称
                    <input v-model="form.name" required maxlength="200" />
                </label>
                <label class="admin-field">
                    艺人名称
                    <input v-model="form.artistName" maxlength="100" />
                </label>
                <div class="admin-grid">
                    <label class="admin-field">
                        城市
                        <select v-model="form.cityId" required>
                            <option value="" disabled>选择城市</option>
                            <option v-for="city in cities" :key="city.id" :value="city.id">
                                {{ city.name }}
                            </option>
                            <option
                                v-if="form.cityId && !cities.some((c) => c.id === form.cityId)"
                                :value="form.cityId"
                            >
                                当前城市（{{ form.cityId }}）
                            </option>
                        </select>
                    </label>
                    <label class="admin-field">
                        分类
                        <select v-model="form.categoryId" required>
                            <option value="" disabled>选择分类</option>
                            <option
                                v-for="category in categories"
                                :key="category.id"
                                :value="category.id"
                            >
                                {{ category.name }}
                            </option>
                            <option
                                v-if="
                                    form.categoryId &&
                                    !categories.some((c) => c.id === form.categoryId)
                                "
                                :value="form.categoryId"
                            >
                                当前分类（{{ form.categoryId }}）
                            </option>
                        </select>
                    </label>
                </div>
                <label class="admin-field">
                    场馆
                    <input v-model="form.venue" required maxlength="200" />
                </label>
                <label class="admin-field">
                    演出介绍
                    <textarea
                        v-model="form.description"
                        rows="8"
                        placeholder="填写演出介绍，按纯文本保存与展示"
                    />
                </label>
                <h2>时间与发布</h2>
                <div class="admin-grid">
                    <label v-for="field in timeFields" :key="field.key" class="admin-field">
                        {{ field.label }}
                        <input v-model="form[field.key]" type="datetime-local" required />
                    </label>
                </div>
                <label class="admin-field">
                    状态
                    <select v-model.number="form.status">
                        <option :value="0">草稿</option>
                        <option :value="1">已发布</option>
                        <option :value="2">已结束</option>
                    </select>
                </label>
            </div>
            <aside>
                <h2>演出海报</h2>
                <label class="admin-field">
                    封面 URL
                    <input
                        v-model="form.coverImage"
                        type="url"
                        maxlength="500"
                        placeholder="https://"
                    />
                </label>
                <PosterImage
                    v-if="safeCover(form.coverImage)"
                    class="cover-preview"
                    :src="safeCover(form.coverImage)"
                    alt="封面预览"
                />
                <div v-else class="poster-placeholder">填写图片链接预览海报</div>
            </aside>
            <div class="editor-footer">
                <button class="admin-btn primary" :disabled="busy || saved">
                    {{ busy ? '保存中…' : '保存演出' }}
                </button>
                <RouterLink class="admin-btn" to="/admin/events">取消</RouterLink>
            </div>
        </form>
    </section>
</template>
<style scoped>
.editor {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 260px;
    gap: 32px;
}
.editor-main {
    display: grid;
    gap: 18px;
}
.editor h2 {
    font-size: 17px;
    margin: 12px 0;
}
.cover-preview,
.poster-placeholder {
    width: 100%;
    aspect-ratio: 3/4;
    object-fit: cover;
    border-radius: 6px;
}
.poster-placeholder {
    background: #f7f8fa;
    display: grid;
    place-items: center;
    color: #777d8b;
    font-size: 13px;
}
.editor-footer {
    grid-column: 1/-1;
    display: flex;
    gap: 12px;
    border-top: 1px solid #eceef2;
    padding-top: 24px;
}
@media (max-width: 800px) {
    .editor {
        grid-template-columns: 1fr;
    }
    .editor aside {
        max-width: 260px;
    }
}
</style>
