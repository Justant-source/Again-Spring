import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  resolve: {
    tsconfigPaths: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: [
      'tests/unit/**/*.spec.{ts,tsx}',
      'tests/component/**/*.spec.{ts,tsx}',
      'tests/integration/**/*.spec.{ts,tsx}',
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      include: ['lib/**', 'components/**', 'app/**'],
      exclude: [
        'mocks/**',
        '**/*.d.ts',
        'app/**/page.tsx',
        'app/**/layout.tsx',
        'app/**/not-found.tsx',
        'app/**/error.tsx',
      ],
      all: true,
      lines: 80,
      functions: 80,
      branches: 70,
      statements: 80,
    },
  },
})
