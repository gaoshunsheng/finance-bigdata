import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import App from './App.vue'
import router from './router'
import 'ant-design-vue/dist/reset.css'
import './assets/styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

// 初始化用户信息
import { useAuthStore } from '@/stores/auth'
const authStore = useAuthStore()
authStore.fetchUser().catch(() => {})

app.mount('#app')
