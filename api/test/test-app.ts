import type { INestApplication } from '@nestjs/common';
import { Test, type TestingModuleBuilder } from '@nestjs/testing';
import request from 'supertest';
import type { App } from 'supertest/types.js';
import { AppModule } from '../src/app.module.js';
import { configureApp } from '../src/app.setup.js';
import { PrismaService } from '../src/prisma/prisma.service.js';

export const API = '/api/v1';

/** `customize` can replace providers, e.g. clients of external services. */
export async function createTestApp(
  customize: (builder: TestingModuleBuilder) => TestingModuleBuilder = (
    builder,
  ) => builder,
): Promise<INestApplication<App>> {
  const moduleRef = await customize(
    Test.createTestingModule({ imports: [AppModule] }),
  ).compile();
  const app = moduleRef.createNestApplication<INestApplication<App>>({
    logger: ['error'],
  });
  configureApp(app);
  await app.init();
  return app;
}

export async function resetDatabase(app: INestApplication) {
  await app
    .get(PrismaService)
    .$executeRawUnsafe('TRUNCATE TABLE users CASCADE');
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  userId: string;
}

let counter = 0;

export async function registerUser(
  app: INestApplication<App>,
  overrides: { email?: string; password?: string } = {},
): Promise<Session & { email: string; password: string }> {
  const email = overrides.email ?? `user${++counter}-${Date.now()}@example.com`;
  const password = overrides.password ?? 'correct-horse-battery';
  const res = await request(app.getHttpServer())
    .post(`${API}/auth/register`)
    .send({ email, password })
    .expect(201);
  return {
    email,
    password,
    accessToken: res.body.accessToken,
    refreshToken: res.body.refreshToken,
    userId: res.body.user.id,
  };
}
