/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    // Las pantallas con formularios de Ant Design (socios) tardan mas de los 5 s por defecto en los
    // runners de CI con cobertura activa; el limite solo corta pruebas colgadas.
    testTimeout: 20_000,
    // Cobertura para SonarQube Cloud (frontend/coverage/lcov.info). Se genera con npm run test:coverage.
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: 'coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/setupTests.ts', 'src/vite-env.d.ts'],
    },
  },
  build: {
    // Ant Design pesa ~1 MB sin comprimir (~330 kB con gzip): es una consola interna, no se divide.
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        entryFileNames: 'assets/[name].[hash].js',
        chunkFileNames: 'assets/[name].[hash].js',
        assetFileNames: 'assets/[name].[hash].[ext]',
      },
    },
  },
})
