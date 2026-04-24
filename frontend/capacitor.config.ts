// ⚠️ CAPACITOR SCAFFOLD — this file is read by Capacitor CLI after you install it.
// 1) npm i -D @capacitor/cli && npm i @capacitor/core
// 2) Restore the type import below:
//      import type { CapacitorConfig } from '@capacitor/cli';
// 3) npx cap add ios / npx cap add android
//
// Kept as a plain object for now so `tsc` / `next build` stay green without the dep.

const config = {
  appId: 'com.againspring.app',
  appName: '다시봄',
  webDir: 'out',
  server: {
    androidScheme: 'https',
  },
};

export default config;
