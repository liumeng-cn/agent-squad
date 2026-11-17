import { createApp, watch } from 'vue'
import { createPinia } from 'pinia'
import App from './app/App.vue'
import { router } from './app/router'
import { onUnauthorized } from './shared/http/http'
import { useAuthStore } from './features/auth/stores/auth'
import { resetChatSessions } from './features/chat/composables/useChat'
import { useSessionsStore } from './features/chat/stores/sessions'
import './shared/styles/base.css'
import './shared/styles/markdown.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.mount('#app')
watch(() => useAuthStore(pinia).user?.userId, (user, previous) => {
  if (user !== previous) { resetChatSessions(); useSessionsStore(pinia).reset() }
}, { flush: 'sync' })

// 请求中的登录态失效统一清理；路由切换会卸载聊天并关闭订阅。
onUnauthorized(() => {
  useAuthStore(pinia).clearUser()
  if (!['login', 'register'].includes(String(router.currentRoute.value.name))) {
    void router.replace({ name: 'login' })
  }
})
