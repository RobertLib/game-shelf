import type { INestApplication } from '@nestjs/common';
import type { NestExpressApplication } from '@nestjs/platform-express';
import request from 'supertest';
import type { App } from 'supertest/types.js';
import { API, createTestApp, registerUser, resetDatabase } from './test-app.js';

// The other suites switch rate limiting off. The app module reads the
// environment when it is imported, so this runs before the imports.
vi.hoisted(() => {
  vi.stubEnv('THROTTLE_ENABLED', 'true');
});

describe('Throttling (e2e)', () => {
  let app: INestApplication<App>;
  let clients = 0;
  /** A new client address each time (the app trusts X-Forwarded-For here). */
  const newAddress = () =>
    `10.0.${Math.floor(++clients / 250)}.${clients % 250}`;
  const login = (email: string, address = newAddress()) =>
    request(app.getHttpServer())
      .post(`${API}/auth/login`)
      .set('X-Forwarded-For', address)
      .send({ email, password: 'wrong-password' });

  beforeAll(async () => {
    app = await createTestApp();
    (app as unknown as NestExpressApplication).set('trust proxy', 1);
  });

  beforeEach(() => resetDatabase(app));

  afterAll(async () => {
    await app.close();
    vi.unstubAllEnvs();
  });

  it('limits sign-ins per address', async () => {
    const address = newAddress();
    for (let i = 0; i < 10; i++) {
      await login(`someone${i}@example.com`, address).expect(401);
    }
    const res = await login('someone@example.com', address).expect(429);
    expect(res.body.code).toBe('TOO_MANY_REQUESTS');
  });

  it('limits sign-ins per account from any number of addresses, however the e-mail is written', async () => {
    const user = await registerUser(app);
    const [name, domain] = user.email.split('@');
    // All of them sign in to the same account: the e-mail is trimmed and
    // lower-cased, and U+0000 is stripped before it is looked up.
    const spellings = [
      ` ${user.email.toUpperCase()} `,
      `${user.email}\u0000`,
      ...Array.from(
        { length: name.length },
        (_, i) => `${name.slice(0, i)}\u0000${name.slice(i)}@${domain}`,
      ),
    ];
    for (let i = 0; i < 20; i++) {
      await login(spellings[i % spellings.length]).expect(401);
    }
    const res = await login(user.email).expect(429);
    expect(res.body.code).toBe('TOO_MANY_REQUESTS');

    // Other accounts have their own limit.
    await login('someone-else@example.com').expect(401);
  });
});
