import { createApp, watch } from 'vue'
import { createPinia } from 'pinia'
import App from './app/App.vue'
import { router } from './app/router'
import { onUnauthorized } from './shared/api/http'
import { useAuthStore } from './features/auth/stores/auth'
import { useConversationsStore } from './features/chat/stores/conversations'
import './styles.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.mount('#app')

watch(() => useAuthStore(pinia).user?.userId, (userId, previous) => {
  if (userId !== previous) useConversationsStore(pinia).resetAll()
}, { flush: 'sync' })

// 会话中途失效（非路由守卫首次探测）时回登录页并保留回跳地址。
onUnauthorized(() => {
  const auth = useAuthStore(pinia)
  if (!auth.ready) {
    return
  }
  auth.clearUser()
  if (router.currentRoute.value.name !== 'login') {
    void router.push({
      name: 'login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
  }
})
