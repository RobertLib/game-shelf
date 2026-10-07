import { randomUUID } from 'node:crypto';
import type { INestApplication } from '@nestjs/common';
import request from 'supertest';
import type { App } from 'supertest/types.js';
import {
  API,
  createTestApp,
  registerUser,
  resetDatabase,
  type Session,
} from './test-app.js';

interface GameChanges {
  games: { id: string; title: string }[];
  deletedIds: string[];
  cursor: string;
  hasMore: boolean;
}

describe('Offline sync (e2e)', () => {
  let app: INestApplication<App>;
  let user: Session;

  const as = (session: Session) => {
    const call =
      (method: 'get' | 'post' | 'patch' | 'put' | 'delete') => (url: string) =>
        request(app.getHttpServer())
          [method](`${API}${url}`)
          .auth(session.accessToken, { type: 'bearer' });
    return {
      get: call('get'),
      post: call('post'),
      patch: call('patch'),
      put: call('put'),
      delete: call('delete'),
    };
  };
  const create = async (body: object, session = user) =>
    (await as(session).post('/games').send(body).expect(201)).body;
  const changes = async (query = '', session = user): Promise<GameChanges> =>
    (await as(session).get(`/games/changes${query}`).expect(200)).body;

  beforeAll(async () => {
    app = await createTestApp();
    // Concurrent supertest requests would each start the server otherwise.
    await app.listen(0);
  });

  beforeEach(async () => {
    await resetDatabase(app);
    user = await registerUser(app);
  });

  afterAll(() => app.close());

  describe('create with a client-generated id', () => {
    it('keeps the id and returns the existing game when repeated', async () => {
      const id = randomUUID();
      const game = await create({ id, title: 'Doom', platform: 'PC' });
      expect(game.id).toBe(id);

      const replay = await as(user)
        .post('/games')
        .send({ id, title: 'Doom II', platform: 'PC' })
        .expect(200);
      expect(replay.body).toEqual(game);
      expect((await changes()).games).toHaveLength(1);
    });

    it('creates the game once when the same id arrives concurrently', async () => {
      const id = randomUUID();
      const statuses = await Promise.all(
        Array.from({ length: 5 }, () =>
          as(user)
            .post('/games')
            .send({ id, title: 'Doom', platform: 'PC' })
            .then((res) => res.status),
        ),
      );
      expect(statuses.sort((a, b) => a - b)).toEqual([200, 200, 200, 200, 201]);
      expect((await changes()).games).toHaveLength(1);
    });

    it("rejects an id of another user's game without revealing it", async () => {
      const stranger = await registerUser(app);
      const theirs = await create(
        { title: 'Secret', platform: 'PC' },
        stranger,
      );

      const res = await as(user)
        .post('/games')
        .send({ id: theirs.id, title: 'Mine', platform: 'PC' })
        .expect(409);
      expect(res.body.code).toBe('CONFLICT');
      expect(res.body).not.toHaveProperty('title');
    });

    it('answers a repeated create of a deleted game with 404', async () => {
      const game = await create({
        id: randomUUID(),
        title: 'Doom',
        platform: 'PC',
      });
      await as(user).delete(`/games/${game.id}`).expect(204);

      const res = await as(user)
        .post('/games')
        .send({ id: game.id, title: 'Doom', platform: 'PC' })
        .expect(404);
      expect(res.body.code).toBe('GAME_NOT_FOUND');
    });

    it('validates the id', async () => {
      const res = await as(user)
        .post('/games')
        .send({ id: 'not-a-uuid', title: 'Doom', platform: 'PC' })
        .expect(400);
      expect(res.body.details.join()).toContain('id');
    });
  });

  describe('PATCH', () => {
    it('changes only the fields in the body', async () => {
      const game = await create({
        title: 'Zelda',
        platform: 'N64',
        region: 'PAL',
        rating: 10,
        notes: 'Boxed',
        purchasePrice: 1299.9,
      });

      const res = await as(user)
        .patch(`/games/${game.id}`)
        .send({ rating: 7, region: null, currency: 'eur' })
        .expect(200);

      expect(res.body).toEqual({
        ...game,
        rating: 7,
        region: null,
        currency: 'EUR',
        updatedAt: expect.any(String),
      });
      expect(res.body.updatedAt >= game.updatedAt).toBe(true);
    });

    it('rejects null for fields that cannot be empty and unknown fields', async () => {
      const game = await create({ title: 'Zelda', platform: 'N64' });
      const res = await as(user)
        .patch(`/games/${game.id}`)
        .send({ title: null, quantity: null, owner: 'me' })
        .expect(400);
      expect(res.body.code).toBe('VALIDATION_FAILED');
      const details = res.body.details.join('\n');
      for (const field of ['title', 'quantity', 'owner']) {
        expect(details).toContain(field);
      }
    });

    it("answers 404 for deleted and other users' games", async () => {
      const stranger = await registerUser(app);
      const theirs = await create(
        { title: 'Secret', platform: 'PC' },
        stranger,
      );
      await as(user)
        .patch(`/games/${theirs.id}`)
        .send({ rating: 1 })
        .expect(404);

      const mine = await create({ title: 'Doom', platform: 'PC' });
      await as(user).delete(`/games/${mine.id}`).expect(204);
      const res = await as(user)
        .patch(`/games/${mine.id}`)
        .send({ rating: 1 })
        .expect(404);
      expect(res.body.code).toBe('GAME_NOT_FOUND');
    });
  });

  describe('DELETE', () => {
    it('is idempotent and hides the game everywhere except the change feed', async () => {
      const game = await create({
        title: 'Doom',
        platform: 'PC',
        genre: 'FPS',
      });
      await as(user).delete(`/games/${game.id}`).expect(204);
      await as(user).delete(`/games/${game.id}`).expect(204);

      await as(user).get(`/games/${game.id}`).expect(404);
      await as(user)
        .put(`/games/${game.id}`)
        .send({ title: 'Doom', platform: 'PC' })
        .expect(404);
      const list = await as(user).get('/games').expect(200);
      expect(list.body.totalItems).toBe(0);
      const facets = await as(user).get('/games/facets').expect(200);
      expect(facets.body).toMatchObject({ totalItems: 0, genres: [] });

      expect(await changes()).toMatchObject({
        games: [],
        deletedIds: [game.id],
      });
    });

    it('still answers 404 for games that never existed', async () => {
      await as(user).delete(`/games/${randomUUID()}`).expect(404);
    });
  });

  describe('change feed', () => {
    it('starts empty', async () => {
      expect(await changes()).toEqual({
        games: [],
        deletedIds: [],
        cursor: '0',
        hasMore: false,
      });
    });

    it('returns everything at first, then only what changed after the cursor', async () => {
      const a = await create({ title: 'A', platform: 'PC' });
      const b = await create({ title: 'B', platform: 'PC' });
      await create({ title: 'C', platform: 'PC' });

      const first = await changes();
      expect(first.games.map((g) => g.title)).toEqual(['A', 'B', 'C']);
      expect(first.hasMore).toBe(false);
      expect(await changes(`?cursor=${first.cursor}`)).toEqual({
        games: [],
        deletedIds: [],
        cursor: first.cursor,
        hasMore: false,
      });

      await as(user).patch(`/games/${b.id}`).send({ title: 'B2' }).expect(200);
      await as(user).delete(`/games/${a.id}`).expect(204);
      await create({ title: 'D', platform: 'PC' });

      const next = await changes(`?cursor=${first.cursor}`);
      expect(next.games.map((g) => g.title)).toEqual(['B2', 'D']);
      expect(next.deletedIds).toEqual([a.id]);
      expect(next.games[0]).toEqual(
        (await as(user).get(`/games/${b.id}`).expect(200)).body,
      );
    });

    it('pages by limit in the order of the changes', async () => {
      const a = await create({ title: 'A', platform: 'PC' });
      await create({ title: 'B', platform: 'PC' });
      await create({ title: 'C', platform: 'PC' });
      await as(user).patch(`/games/${a.id}`).send({ rating: 5 }).expect(200);

      const page1 = await changes('?limit=2');
      expect(page1.games.map((g) => g.title)).toEqual(['B', 'C']);
      expect(page1.hasMore).toBe(true);

      const page2 = await changes(`?limit=2&cursor=${page1.cursor}`);
      expect(page2.games.map((g) => g.title)).toEqual(['A']);
      expect(page2.hasMore).toBe(false);
    });

    it('gives concurrent writes distinct consecutive positions', async () => {
      await Promise.all(
        Array.from({ length: 10 }, (_, i) =>
          create({ title: `Game ${i}`, platform: 'PC' }),
        ),
      );
      const feed = await changes();
      expect(feed.games).toHaveLength(10);
      expect(feed.cursor).toBe('10');
    });

    it("never includes other users' changes", async () => {
      const stranger = await registerUser(app);
      const theirs = await create(
        { title: 'Secret', platform: 'PC' },
        stranger,
      );
      await as(stranger).delete(`/games/${theirs.id}`).expect(204);
      expect(await changes()).toMatchObject({ games: [], deletedIds: [] });
    });

    it('asks for a reset when the cursor is ahead of the server', async () => {
      await create({ title: 'A', platform: 'PC' });
      const res = await as(user).get('/games/changes?cursor=2').expect(410);
      expect(res.body.code).toBe('SYNC_RESET_REQUIRED');
    });

    it.each(['cursor=abc', 'cursor=-1', 'limit=0', 'limit=1001'])(
      'rejects %s',
      async (query) => {
        const res = await as(user).get(`/games/changes?${query}`).expect(400);
        expect(res.body.code).toBe('VALIDATION_FAILED');
      },
    );
  });
});
