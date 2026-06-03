import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const breadcrumbs = ref<Array<{ title: string; path?: string }>>([])

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setBreadcrumbs(items: Array<{ title: string; path?: string }>) {
    breadcrumbs.value = items
  }

  return {
    sidebarCollapsed,
    breadcrumbs,
    toggleSidebar,
    setBreadcrumbs,
  }
})
