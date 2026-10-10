import type { INestApplication } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import request from 'supertest';
import type { App } from 'supertest/types.js';
import { revokeAllSessions } from '../src/auth/token.service.js';
import { PrismaService } from '../src/prisma/prisma.service.js';
import { API, createTestApp, registerUser, resetDatabase } from './test-app.js';

describe('Auth (e2e)', () => {
  let app: INestApplication<App>;
  const http = () => request(app.getHttpServer());
  const prisma = () => app.get(PrismaService);
  const refresh = (refreshToken: string) =>
    http().post(`${API}/auth/refresh`).send({ refreshToken });
  const logout = (refreshToken: string) =>
    http().post(`${API}/auth/logout`).send({ refreshToken });
  const login = async (user: { email: string; password: string }) =>
    (
      await http()
        .post(`${API}/auth/login`)
        .send({ email: user.email, password: user.password })
        .expect(200)
    ).body as { accessToken: string; refreshToken: string };
  const me = (accessToken: string) =>
    http().get(`${API}/auth/me`).auth(accessToken, { type: 'bearer' });

  beforeAll(async () => {
    app = await createTestApp();
    // Concurrent supertest requests would each start the server otherwise.
    await app.listen(0);
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

    it('answers a retry of a refresh whose response was lost with a new pair', async () => {
      const user = await registerUser(app);
      const lost = await refresh(user.refreshToken).expect(200);

      const retry = await refresh(user.refreshToken).expect(200);
      expect(retry.body.refreshToken).not.toBe(lost.body.refreshToken);
      expect(retry.body.user.id).toBe(user.userId);
      await me(retry.body.accessToken).expect(200);

      await refresh(retry.body.refreshToken).expect(200);
    });

    it('revokes every session when the pair voided by a retry turns up after all', async () => {
      // The "lost" pair did arrive – at the legitimate app – and a thief retried the stolen token.
      const user = await registerUser(app);
      const other = await login(user);
      const legitimate = await refresh(user.refreshToken).expect(200);
      const stolen = await refresh(user.refreshToken).expect(200);

      const voided = await refresh(legitimate.body.refreshToken).expect(401);
      expect(voided.body.code).toBe('INVALID_REFRESH_TOKEN');
      await refresh(stolen.body.refreshToken).expect(401);
      await refresh(other.refreshToken).expect(401);
    });

    it('revokes every session when a rotated token is replayed after the grace period', async () => {
      const user = await registerUser(app);
      const other = await login(user);
      const first = await refresh(user.refreshToken).expect(200);
      // Pretend the rotation happened longer ago than the 120 s grace period.
      await prisma().refreshToken.updateMany({
        where: { userId: user.userId, replacedById: { not: null } },
        data: { revokedAt: new Date(Date.now() - 121_000) },
      });

      const replay = await refresh(user.refreshToken).expect(401);
      expect(replay.body.code).toBe('INVALID_REFRESH_TOKEN');

      // The legitimately rotated token and every other session were revoked too.
      await refresh(first.body.refreshToken).expect(401);
      await refresh(other.refreshToken).expect(401);
    });

    it('revokes every session when a rotated token is replayed after its successor was used', async () => {
      const user = await registerUser(app);
      const first = await refresh(user.refreshToken).expect(200);
      const second = await refresh(first.body.refreshToken).expect(200);

      await refresh(user.refreshToken).expect(401);
      await refresh(second.body.refreshToken).expect(401);
    });

    it('leaves one valid token after concurrent refreshes with the same token', async () => {
      const user = await registerUser(app);
      const results = await Promise.all([
        refresh(user.refreshToken),
        refresh(user.refreshToken),
      ]);
      expect(results.map((res) => res.status)).toEqual([200, 200]);

      // The later one counts as a retry: the earlier pair is voided (the apps never refresh twice
      // at once, so presenting a voided token signs every session out).
      const tokens = await prisma().refreshToken.findMany({
        where: { userId: user.userId, revokedAt: null },
      });
      expect(tokens).toHaveLength(1);
      expect(
        await prisma().refreshToken.count({
          where: { userId: user.userId, voided: true },
        }),
      ).toBe(1);
    });

    it('rejects a signed-out token without signing out other sessions', async () => {
      const user = await registerUser(app);
      const other = await login(user);
      await logout(user.refreshToken).expect(204);

      const late = await refresh(user.refreshToken).expect(401);
      expect(late.body.code).toBe('INVALID_REFRESH_TOKEN');
      await refresh(other.refreshToken).expect(200);
    });

    it('rejects expired tokens and deletes them when signing in', async () => {
      const user = await registerUser(app);
      const stranger = await registerUser(app);
      await prisma().refreshToken.updateMany({
        data: { expiresAt: new Date(Date.now() - 1000) },
      });
      await refresh(user.refreshToken).expect(401);

      const tokens = (userId: string) =>
        prisma().refreshToken.findMany({
          where: { userId },
          select: { expiresAt: true },
        });
      await login(user);
      const left = await tokens(user.userId);
      expect(left).toHaveLength(1);
      expect(left[0].expiresAt.getTime()).toBeGreaterThan(Date.now());
      // Other users' tokens are left for their own next sign-in.
      expect(await tokens(stranger.userId)).toHaveLength(1);
    });

    it('rejects every token after all sessions were revoked (restoring a backup)', async () => {
      const user = await registerUser(app);
      const other = await login(user);
      expect(await revokeAllSessions(prisma())).toBe(2);
      for (const token of [user.refreshToken, other.refreshToken]) {
        expect((await refresh(token).expect(401)).body.code).toBe(
          'INVALID_REFRESH_TOKEN',
        );
      }
      // Signing in again works as usual.
      expect((await refresh((await login(user)).refreshToken)).status).toBe(
        200,
      );
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
      await logout(user.refreshToken).expect(204);
      await refresh(user.refreshToken).expect(401);
    });

    it('also revokes what an earlier refresh with the token returned', async () => {
      const user = await registerUser(app);
      const other = await login(user);
      // A refresh that raced the logout, or whose response was lost.
      const raced = await refresh(user.refreshToken).expect(200);
      const retried = await refresh(user.refreshToken).expect(200);

      await logout(user.refreshToken).expect(204);
      await refresh(retried.body.refreshToken).expect(401);
      await refresh(other.refreshToken).expect(200);
      // The pair the retry voided never reached the app: presenting it means it leaked.
      await refresh(raced.body.refreshToken).expect(401);
    });
  });

  describe('POST /auth/change-password', () => {
    it('changes the password and signs out other sessions', async () => {
      const user = await registerUser(app);
      const other = await http()
        .post(`${API}/auth/login`)
        .send({ email: user.email, password: user.password })
        .expect(200);

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

    it('rejects a refresh token issued by a refresh that raced the change', async () => {
      const user = await registerUser(app);
      const raced = await refresh(user.refreshToken).expect(200);
      await http()
        .post(`${API}/auth/change-password`)
        .auth(raced.body.accessToken, { type: 'bearer' })
        .send({
          currentPassword: user.password,
          newPassword: 'brand-new-password',
        })
        .expect(200);
      // The refresh committed its successor after the change revoked the sessions it could see.
      await prisma().refreshToken.updateMany({
        where: { userId: user.userId, replacedById: null },
        data: { revokedAt: null },
      });

      const res = await refresh(raced.body.refreshToken).expect(401);
      expect(res.body.code).toBe('INVALID_REFRESH_TOKEN');
    });

    it('rejects access tokens issued before the change, even within the same second', async () => {
      const user = await registerUser(app);
      await http()
        .post(`${API}/auth/change-password`)
        .auth(user.accessToken, { type: 'bearer' })
        .send({
          currentPassword: user.password,
          newPassword: 'brand-new-password',
        })
        .expect(200);
      const { passwordChangedAt } = await prisma().user.findUniqueOrThrow({
        where: { id: user.userId },
      });
      const changedAt = passwordChangedAt?.getTime() ?? 0;
      const sameSecond = Math.floor(changedAt / 1000);
      const token = (payload: object) =>
        app
          .get(JwtService)
          .signAsync({ sub: user.userId, iat: sameSecond, ...payload });

      await me(await token({ pwc: 0 })).expect(401);
      await me(await token({ pwc: changedAt })).expect(200);
      // Tokens without the claim (issued by an older version) compare whole seconds.
      await me(await token({ iat: sameSecond - 1 })).expect(401);
      await me(await token({})).expect(200);
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

      const res = await http()
        .get(`${API}/games`)
        .auth(user.accessToken, { type: 'bearer' })
        .expect(401);
      expect(res.body.code).toBe('ACCOUNT_DELETED');
      await http()
        .post(`${API}/auth/login`)
        .send({ email: user.email, password: user.password })
        .expect(401);
      expect((await refresh(user.refreshToken).expect(401)).body.code).toBe(
        'INVALID_REFRESH_TOKEN',
      );
    });

    it('lets a refresh and a sign-out wait for a deletion instead of deadlocking with it', async () => {
      const user = await registerUser(app);
      const other = await login(user);
      let proceed = () => {};
      const deleting = new Promise<void>((resolve) => (proceed = resolve));
      // Deleting the account locks the user's row first, then its tokens.
      const deletion = prisma().$transaction(async (tx) => {
        await tx.$queryRaw`
          SELECT 1 FROM "users" WHERE "id" = ${user.userId}::uuid FOR UPDATE`;
        await deleting;
        await tx.user.delete({ where: { id: user.userId } });
      });
      // `then` sends them now; supertest waits for it.
      const refreshed = refresh(user.refreshToken).then((res) => res.status);
      const loggedOut = logout(other.refreshToken).then((res) => res.status);
      // Let both requests reach their locks before the deletion goes on.
      await new Promise((resolve) => setTimeout(resolve, 300));
      proceed();

      await deletion;
      expect(await refreshed).toBe(401);
      expect(await loggedOut).toBe(204);
    });

    it('tells devices with an expired access token that the account is gone', async () => {
      const deleted = await registerUser(app);
      const kept = await registerUser(app);
      const expired = (sub: string) =>
        app.get(JwtService).signAsync({
          sub,
          pwc: 0,
          exp: Math.floor(Date.now() / 1000) - 60,
        });
      const expiredOfDeleted = await expired(deleted.userId);
      await http()
        .delete(`${API}/auth/me`)
        .auth(deleted.accessToken, { type: 'bearer' })
        .send({ password: deleted.password })
        .expect(204);

      expect((await me(expiredOfDeleted).expect(401)).body.code).toBe(
        'ACCOUNT_DELETED',
      );
      // An expired token of an existing account is just refreshed.
      expect((await me(await expired(kept.userId)).expect(401)).body.code).toBe(
        'UNAUTHORIZED',
      );
      // Only a token this server signed says anything about the account.
      const forged = await new JwtService({
        secret: 'another-secret',
      }).signAsync({ sub: deleted.userId, pwc: 0 });
      expect((await me(forged).expect(401)).body.code).toBe('UNAUTHORIZED');
    });
  });
});
