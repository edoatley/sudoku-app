import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  // Optional dev-server proxy so the browser talks to the API SAME-ORIGIN (localhost:5173) and Vite
  // forwards to the deployed backend/image-rec server-side. This avoids cross-site request quirks
  // (CORS + the Authorization header not surviving the browser's cross-site path) when running the
  // local UI against a deployed GCP env. Set in ui/.env.local:
  //   VITE_API_URL=/api/v1
  //   VITE_DEV_PROXY_TARGET=https://<backend>.run.app
  //   (optional) VITE_IMAGE_RECOGNITION_URL=/imgrec  + VITE_DEV_IMG_PROXY_TARGET=https://<image-rec>.run.app
  // Mirrors how Firebase Hosting rewrites serve the UI + proxy /api/** to Cloud Run in production.
  const proxyTarget = env.VITE_DEV_PROXY_TARGET;
  const imgProxyTarget = env.VITE_DEV_IMG_PROXY_TARGET;
  const proxy = {};
  if (proxyTarget) {
    proxy['/api'] = { target: proxyTarget, changeOrigin: true, secure: true };
  }
  if (imgProxyTarget) {
    proxy['/imgrec'] = {
      target: imgProxyTarget,
      changeOrigin: true,
      secure: true,
      rewrite: (p) => p.replace(/^\/imgrec/, ''),
    };
  }

  return {
    plugins: [react()],
    ...(Object.keys(proxy).length > 0 && { server: { proxy } }),
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['src/test-setup.js'],
      include: ['src/**/*.test.{js,jsx,ts,tsx}'],
      reporters: ['default', ['junit', { outputFile: 'test-results/unit-results.xml' }]],
      coverage: {
        provider: 'v8',
        reporter: ['text', 'lcov', 'json-summary'],
      },
    },
  };
});
