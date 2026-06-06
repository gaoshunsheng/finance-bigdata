import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [
        AntDesignVueResolver({
          importStyle: false,
        }),
      ],
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  css: {
    preprocessorOptions: {
      less: {
        javascriptEnabled: true,
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api/v1/decision': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api/v1/features': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/api/v1/profile': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/api/v1/reports': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
