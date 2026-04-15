import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/ypc': {
        target: 'http://localhost:8080', // 指向你的 Spring Boot 地址
        changeOrigin: true
      }
    }
  }
})
