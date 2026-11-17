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
      '/api/v2': {
        target: gateway,
        changeOrigin: true,
        secure: false,
      },
      '/api': {
        target: gateway,
        changeOrigin: true,
        secure: false,
        ws: true,
      },
    },
  },
  }
})
