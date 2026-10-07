import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { parse } from 'dotenv';

/** Brings the test database schema up to date once before all suites. */
export default function setup() {
  // Global setup runs outside the workers, so `test.env` does not apply here.
  const env = { ...process.env, ...parse(readFileSync('.env.test')) };
  execSync('npx prisma migrate deploy', { stdio: 'inherit', env });
}
