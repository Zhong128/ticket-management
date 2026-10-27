import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { browse } from '../api/browse'
import type { Category, City } from '../api/types'

export const useCatalogStore = defineStore('catalog', () => {
    const cities = ref<City[]>([])
    const categories = ref<Category[]>([])
    const cityId = ref(localStorage.getItem('youxi-city') || '')
    const city = computed(() => cities.value.find((item) => item.id === cityId.value))
    const error = ref('')
    const loading = ref(false)

    function selectCity(id: string) {
        cityId.value = id
        localStorage.setItem('youxi-city', id)
    }
    async function load() {
        loading.value = true
        error.value = ''
        try {
            const [cityList, categoryList] = await Promise.all([
                browse.cities(),
                browse.categories(),
            ])
            cities.value = cityList
            categories.value = categoryList
            if (cityId.value && !cityList.some((item) => item.id === cityId.value)) selectCity('')
        } catch (cause) {
            error.value = cause instanceof Error ? cause.message : '分类与城市加载失败'
        } finally {
            loading.value = false
        }
    }
    return { cities, categories, cityId, city, error, loading, selectCity, load }
})
