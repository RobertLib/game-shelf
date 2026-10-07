import { existsSync, readFileSync } from 'node:fs';
import { parse } from 'dotenv';
import { defineConfig } from 'vitest/config';

const ENV_FILE = '.env.test';
if (!existsSync(ENV_FILE)) {
  throw new Error(
    `Missing ${ENV_FILE} – copy .env.test.example and point it to a disposable database.`,
  );
}

export default defineConfig({
  resolve: { tsconfigPaths: true },
  test: {
    globals: true,
    root: './',
    include: ['**/*.e2e-spec.ts'],
    env: parse(readFileSync(ENV_FILE)),
    globalSetup: ['./test/global-setup.ts'],
    // All suites share one database.
    fileParallelism: false,
    hookTimeout: 60_000,
  },
});
