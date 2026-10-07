import type { INestApplication } from '@nestjs/common';
import request from 'supertest';
import type { App } from 'supertest/types.js';
import { API, createTestApp, registerUser, resetDatabase } from './test-app.js';

describe('Auth (e2e)', () => {
  let app: INestApplication<App>;
  const http = () => request(app.getHttpServer());

  beforeAll(async () => {
    app = await createTestApp();
  });

  beforeEach(() => resetDatabase(app));

  afterAll(() => app.close());

  describe('POST /auth/register', () => {
    it('creates the account, normalises the e-mail and signs in', async () => {
      const res = await http()
        .post(`${API}/auth/register`)
        .send({
          email: '  Collector@Example.COM ',
          password: 'super-secret',
          displayName: ' Rob ',
        })
        .expect(201);

      expect(res.body).toMatchObject({
        accessToken: expect.any(String),
        refreshToken: expect.any(String),
        expiresIn: 900,
        user: { email: 'collector@example.com', displayName: 'Rob' },
      });
      expect(res.body.user).not.toHaveProperty('passwordHash');
    });

    it('rejects a duplicate e-mail regardless of case', async () => {
      await registerUser(app, { email: 'dup@example.com' });
      const res = await http()
        .post(`${API}/auth/register`)
        .send({ email: 'DUP@example.com', password: 'another-password' })
        .expect(409);
      expect(res.body.code).toBe('EMAIL_ALREADY_REGISTERED');
    });

    it('validates input', async () => {
      const res = await http()
        .post(`${API}/auth/register`)
        .send({ email: 'not-an-email', password: 'short' })
        .expect(400);
      expect(res.body.code).toBe('VALIDATION_FAILED');
      expect(res.body.details).toHaveLength(2);
    });
  });

  describe('POST /auth/login', () => {
    it('signs in with valid credentials', async () => {
      const user = await registerUser(app);
      const res = await http()
        .post(`${API}/auth/login`)
        .send({ email: user.email.toUpperCase(), password: user.password })
        .expect(200);
      expect(res.body.user.id).toBe(user.userId);
    });

    it.each([
      ['wrong password', 'registered', 'wrong-password'],
      ['unknown e-mail', 'nobody@example.com', 'whatever-password'],
    ])('rejects %s with the same error', async (_, email, password) => {
      const user = await registerUser(app);
      const res = await http()
        .post(`${API}/auth/login`)
        .send({ email: email === 'registered' ? user.email : email, password })
        .expect(401);
      expect(res.body.code).toBe('INVALID_CREDENTIALS');
    });
  });

  describe('protected routes', () => {
    it('require a valid bearer token', async () => {
      await http().get(`${API}/auth/me`).expect(401);
      await http()
        .get(`${API}/auth/me`)
        .auth('garbage', { type: 'bearer' })
        .expect(401);
    });

    it('return the current user', async () => {
      const user = await registerUser(app);
      const res = await http()
        .get(`${API}/auth/me`)
        .auth(user.accessToken, { type: 'bearer' })
        .expect(200);
      expect(res.body).toMatchObject({
        id: user.userId,
        email: user.email,
        displayName: null,
      });
    });
  });

  describe('POST /auth/refresh', () => {
    it('rotates the refresh token', async () => {
      const user = await registerUser(app);
      const res = await http()
        .post(`${API}/auth/refresh`)
        .send({ refreshToken: user.refreshToken })
        .expect(200);

      expect(res.body.refreshToken).not.toBe(user.refreshToken);
      await http()
        .get(`${API}/auth/me`)
        .auth(res.body.accessToken, { type: 'bearer' })
        .expect(200);
    });

    it('revokes every session when a used token is replayed', async () => {
      const user = await registerUser(app);
      const first = await http()
        .post(`${API}/auth/refresh`)
        .send({ refreshToken: user.refreshToken })
        .expect(200);

      const replay = await http()
        .post(`${API}/auth/refresh`)
        .send({ refreshToken: user.refreshToken })
        .expect(401);
      expect(replay.body.code).toBe('INVALID_REFRESH_TOKEN');

      // The legitimately rotated token was revoked too.
      await http()
        .post(`${API}/auth/refresh`)
        .send({ refreshToken: first.body.refreshToken })
        .expect(401);
    });

    it('rejects unknown tokens', async () => {
      await http()
        .post(`${API}/auth/refresh`)
        .send({ refreshToken: 'nope' })
        .expect(401);
    });
  });

  describe('POST /auth/logout', () => {
    it('revokes the refresh token', async () => {
      const user = await registerUser(app);
      await http()
        .post(`${API}/auth/logout`)
        .send({ refreshToken: user.refreshToken })
        .expect(204);
      await http()
        .post(`${API}/auth/refresh`)
        .send({ refreshToken: user.refreshToken })
        .expect(401);
    });
  });

  describe('POST /auth/change-password', () => {
    it('changes the password and signs out other sessions', async () => {
      const user = await registerUser(app);
      const other = await http()
        .post(`${API}/auth/login`)
        .send({ email: user.email, password: user.password })
        .expect(200);

      // Tokens carry second precision; make sure the change happens in a later second.
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const res = await http()
        .post(`${API}/auth/change-password`)
        .auth(user.accessToken, { type: 'bearer' })
        .send({
          currentPassword: user.password,
          newPassword: 'brand-new-password',
        })
        .expect(200);

      await http()
        .get(`${API}/auth/me`)
        .auth(res.body.accessToken, { type: 'bearer' })
        .expect(200);
      await http()
        .get(`${API}/auth/me`)
        .auth(other.body.accessToken, { type: 'bearer' })
        .expect(401);
      await http()
        .post(`${API}/auth/refresh`)
        .send({ refreshToken: other.body.refreshToken })
        .expect(401);

      await http()
        .post(`${API}/auth/login`)
        .send({ email: user.email, password: user.password })
        .expect(401);
      await http()
        .post(`${API}/auth/login`)
        .send({ email: user.email, password: 'brand-new-password' })
        .expect(200);
    });

    it('rejects a wrong current password', async () => {
      const user = await registerUser(app);
      const res = await http()
        .post(`${API}/auth/change-password`)
        .auth(user.accessToken, { type: 'bearer' })
        .send({
          currentPassword: 'wrong-password',
          newPassword: 'brand-new-password',
        })
        .expect(400);
      expect(res.body.code).toBe('INVALID_CURRENT_PASSWORD');
    });

    it('rejects reusing the same password', async () => {
      const user = await registerUser(app);
      const res = await http()
        .post(`${API}/auth/change-password`)
        .auth(user.accessToken, { type: 'bearer' })
        .send({ currentPassword: user.password, newPassword: user.password })
        .expect(400);
      expect(res.body.code).toBe('VALIDATION_FAILED');
    });
  });

  describe('DELETE /auth/me', () => {
    it('deletes the account with its collection', async () => {
      const user = await registerUser(app);
      await http()
        .post(`${API}/games`)
        .auth(user.accessToken, { type: 'bearer' })
        .send({ title: 'Doom', platform: 'PC' })
        .expect(201);

      await http()
        .delete(`${API}/auth/me`)
        .auth(user.accessToken, { type: 'bearer' })
        .send({ password: 'wrong-password' })
        .expect(400);
      await http()
        .delete(`${API}/auth/me`)
        .auth(user.accessToken, { type: 'bearer' })
        .send({ password: user.password })
        .expect(204);

      await http()
        .get(`${API}/games`)
        .auth(user.accessToken, { type: 'bearer' })
        .expect(401);
      await http()
        .post(`${API}/auth/login`)
        .send({ email: user.email, password: user.password })
        .expect(401);
    });
  });
});
