import { defineConfig } from 'vite';
import legacy from '@vitejs/plugin-legacy';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [
    react(),
    legacy({
      targets: ['Safari >= 11', 'iOS >= 11', 'defaults', 'not IE 11']
    })
  ],
  build: {
    cssTarget: 'safari11'
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
});
