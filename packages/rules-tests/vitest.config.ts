import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'node',
    include: ['tests/**/*.test.ts'],
    testTimeout: 20000,
    hookTimeout: 40000,
    // One emulator, one rules environment passed down to every suite.
    fileParallelism: false,
  },
})
