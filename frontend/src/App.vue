<script setup lang="ts">
import { useRoute } from 'vue-router'
import UserLayout from './UserLayout.vue'
const route = useRoute()
</script>
<template>
    <RouterView v-if="route.meta.admin" />
    <UserLayout v-else />
</template>
