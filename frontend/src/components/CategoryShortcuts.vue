<script setup lang="ts">
import type { Category } from '../api/types'
import CategoryIcon from './CategoryIcon.vue'

defineProps<{ categories: Category[]; cityId?: string }>()
</script>

<template>
    <nav v-if="categories.length" class="category-shortcuts" aria-label="按类型找演出">
        <RouterLink
            v-for="category in categories"
            :key="category.id"
            :to="{
                path: '/events',
                query: { categoryId: category.id, cityId: cityId || undefined },
            }"
            class="category-shortcut"
        >
            <span class="category-art"><CategoryIcon :name="category.name" /></span>
            <span>{{ category.name }}</span>
        </RouterLink>
    </nav>
</template>
