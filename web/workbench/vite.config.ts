import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'SQUAD_')
  const gateway = env.SQUAD_GATEWAY_URL || 'http://127.0.0.1:18081'
  return {
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/user-server': { target: gateway, changeOrigin: true, secure: false },
      '/v2-server': { target: gateway, changeOrigin: true, secure: false },
      '/gateway': { target: gateway, changeOrigin: true, secure: false },
    },
  },
  }
})
