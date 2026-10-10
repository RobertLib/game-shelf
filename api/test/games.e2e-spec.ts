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

const ZELDA = {
  title: 'The Legend of Zelda: Ocarina of Time',
  platform: 'N64',
  region: 'PAL',
  completeness: 'CIB',
  condition: 'VERY_GOOD',
  genre: 'Action-adventure',
  developer: 'Nintendo EAD',
  publisher: 'Nintendo',
  releaseYear: 1998,
  productCode: 'NUS-NZLP-EUR',
  purchasePrice: 1299.9,
  purchaseDate: '2024-05-17',
  estimatedValue: 2500,
  storageLocation: 'Shelf A',
  rating: 10,
  favorite: true,
  coverImageUrl: 'https://example.com/zelda.jpg',
};

describe('Games (e2e)', () => {
  let app: INestApplication<App>;
  let user: Session;

  const http = () => request(app.getHttpServer());
  const as = (session: Session) => ({
    get: (url: string) =>
      http().get(`${API}${url}`).auth(session.accessToken, { type: 'bearer' }),
    post: (url: string) =>
      http().post(`${API}${url}`).auth(session.accessToken, { type: 'bearer' }),
    put: (url: string) =>
      http().put(`${API}${url}`).auth(session.accessToken, { type: 'bearer' }),
    delete: (url: string) =>
      http()
        .delete(`${API}${url}`)
        .auth(session.accessToken, { type: 'bearer' }),
  });
  const create = async (body: object, session = user) =>
    (await as(session).post('/games').send(body).expect(201)).body;
  const titles = async (query: string) => {
    const res = await as(user).get(`/games?${query}`).expect(200);
    return res.body.items.map((g: { title: string }) => g.title);
  };

  beforeAll(async () => {
    app = await createTestApp();
  });

  beforeEach(async () => {
    await resetDatabase(app);
    user = await registerUser(app);
  });

  afterAll(() => app.close());

  describe('CRUD', () => {
    it('creates a game with defaults and returns it', async () => {
      const game = await create({ title: '  Doom  ', platform: 'PC' });
      expect(game).toMatchObject({
        id: expect.any(String),
        title: 'Doom',
        platform: 'PC',
        status: 'OWNED',
        format: 'PHYSICAL',
        quantity: 1,
        currency: 'CZK',
        favorite: false,
        region: null,
        purchasePrice: null,
        purchaseDate: null,
      });
      expect(game).not.toHaveProperty('userId');

      const fetched = await as(user).get(`/games/${game.id}`).expect(200);
      expect(fetched.body).toEqual(game);
    });

    it('round-trips every collector field', async () => {
      const game = await create(ZELDA);
      expect(game).toMatchObject(ZELDA);
    });

    it('replaces the whole record on PUT', async () => {
      const game = await create(ZELDA);
      const res = await as(user)
        .put(`/games/${game.id}`)
        .send({
          title: 'Majora’s Mask',
          platform: 'N64',
          status: 'WISHLIST',
          currency: 'eur',
        })
        .expect(200);

      expect(res.body).toMatchObject({
        id: game.id,
        title: 'Majora’s Mask',
        status: 'WISHLIST',
        currency: 'EUR',
        region: null,
        purchasePrice: null,
        favorite: false,
        coverImageUrl: null,
      });
    });

    it('deletes a game', async () => {
      const game = await create(ZELDA);
      await as(user).delete(`/games/${game.id}`).expect(204);
      const res = await as(user).get(`/games/${game.id}`).expect(404);
      expect(res.body.code).toBe('GAME_NOT_FOUND');
    });

    it('validates the payload', async () => {
      const res = await as(user)
        .post('/games')
        .send({
          title: '',
          platform: 'TAMAGOTCHI',
          status: null,
          rating: 11,
          releaseYear: 1800,
          purchasePrice: 10.999,
          purchaseDate: '2024-02-30',
          barcode: '12ab',
          currency: 'XXXX',
          coverImageUrl: 'ftp://example.com/a.jpg',
          hacker: true,
        })
        .expect(400);

      expect(res.body.code).toBe('VALIDATION_FAILED');
      const details = res.body.details.join('\n');
      for (const field of [
        'hacker',
        'title',
        'platform',
        'status',
        'rating',
        'releaseYear',
        'purchasePrice',
        'purchaseDate',
        'barcode',
        'currency',
        'coverImageUrl',
      ]) {
        expect(details).toContain(field);
      }
    });

    it('accepts what the apps accept', async () => {
      const game = await create({
        title: '😀'.repeat(200),
        platform: 'PC',
        genre: '🇨🇿'.repeat(50),
        currency: 'dem',
        coverImageUrl: 'HTTPS://img.example.co.uk/a_b/%C3%A9.jpg?x=1#y',
      });
      expect(game).toMatchObject({
        currency: 'DEM',
        coverImageUrl: 'HTTPS://img.example.co.uk/a_b/%C3%A9.jpg?x=1#y',
      });
    });

    it('rejects what the apps reject', async () => {
      const res = await as(user)
        .post('/games')
        .send({
          title: '❤️'.repeat(101),
          platform: 'PC',
          currency: 'ıab',
          coverImageUrl: 'https://example.com/é.png',
        })
        .expect(400);
      const details = res.body.details.join('\n');
      for (const field of ['title', 'currency', 'coverImageUrl']) {
        expect(details).toContain(field);
      }
    });

    it('rejects malformed ids', async () => {
      await as(user).get('/games/not-a-uuid').expect(400);
    });

    it('answers a body that is too large with VALIDATION_FAILED', async () => {
      const res = await as(user)
        .post('/games')
        .send({ title: 'Doom', platform: 'PC', notes: 'x'.repeat(200_000) })
        .expect(413);
      expect(res.body.code).toBe('VALIDATION_FAILED');
    });

    it('answers an unsupported charset with VALIDATION_FAILED', async () => {
      const res = await as(user)
        .post('/games')
        .set('Content-Type', 'application/json; charset=latin2')
        .send('{"title":"Doom","platform":"PC"}')
        .expect(415);
      expect(res.body.code).toBe('VALIDATION_FAILED');
    });

    it('answers unknown routes with NOT_FOUND', async () => {
      const res = await as(user).get('/no-such-route').expect(404);
      expect(res.body.code).toBe('NOT_FOUND');
    });
  });

  describe('privacy', () => {
    it("hides other users' games completely", async () => {
      const stranger = await registerUser(app);
      const game = await create(ZELDA, stranger);

      await as(user).get(`/games/${game.id}`).expect(404);
      await as(user)
        .put(`/games/${game.id}`)
        .send({ title: 'Mine', platform: 'PC' })
        .expect(404);
      await as(user).delete(`/games/${game.id}`).expect(404);

      const list = await as(user).get('/games').expect(200);
      expect(list.body.totalItems).toBe(0);
      const facets = await as(user).get('/games/facets').expect(200);
      expect(facets.body.totalItems).toBe(0);

      await as(stranger).get(`/games/${game.id}`).expect(200);
    });
  });

  describe('listing', () => {
    beforeEach(async () => {
      await create(ZELDA);
      await create({
        title: 'Gran Turismo 3: A-Spec',
        platform: 'PS2',
        genre: 'Racing',
        publisher: 'Sony Computer Entertainment',
        releaseYear: 2001,
        purchasePrice: 150,
        purchaseDate: '2023-01-10',
        completeness: 'LOOSE',
        rating: 7,
      });
      await create({
        title: 'Dizzy: Prince of the Yolkfolk',
        platform: 'ZX_SPECTRUM',
        status: 'WISHLIST',
        genre: 'platformer',
        publisher: 'Codemasters',
        releaseYear: 1991,
        notes: 'Looking for the original cassette',
      });
      await create({
        title: 'Super Mario 64',
        platform: 'N64',
        genre: 'Platformer',
        publisher: 'Nintendo',
        releaseYear: 1996,
        estimatedValue: 900,
        favorite: true,
        format: 'DIGITAL',
      });
    });

    it('sorts by title by default and paginates', async () => {
      const res = await as(user).get('/games?pageSize=3').expect(200);
      expect(res.body).toMatchObject({
        page: 1,
        pageSize: 3,
        totalItems: 4,
        totalPages: 2,
      });
      expect(res.body.items.map((g: { title: string }) => g.title)).toEqual([
        'Dizzy: Prince of the Yolkfolk',
        'Gran Turismo 3: A-Spec',
        'Super Mario 64',
      ]);
      expect(await titles('pageSize=3&page=2')).toEqual([
        'The Legend of Zelda: Ocarina of Time',
      ]);
    });

    it('puts missing values last regardless of direction', async () => {
      expect(await titles('sort=purchasePrice&order=desc')).toEqual([
        'The Legend of Zelda: Ocarina of Time',
        'Gran Turismo 3: A-Spec',
        'Dizzy: Prince of the Yolkfolk',
        'Super Mario 64',
      ]);
      expect(await titles('sort=releaseYear&order=asc')).toEqual([
        'Dizzy: Prince of the Yolkfolk',
        'Super Mario 64',
        'The Legend of Zelda: Ocarina of Time',
        'Gran Turismo 3: A-Spec',
      ]);
    });

    it.each([
      ['q=zelda%20ocarina', ['The Legend of Zelda: Ocarina of Time']],
      ['q=NUS-NZLP', ['The Legend of Zelda: Ocarina of Time']],
      ['q=cassette', ['Dizzy: Prince of the Yolkfolk']],
      ['q=mario%20zelda', []],
      [
        'platform=N64',
        ['Super Mario 64', 'The Legend of Zelda: Ocarina of Time'],
      ],
      [
        'platform=PS2,ZX_SPECTRUM',
        ['Dizzy: Prince of the Yolkfolk', 'Gran Turismo 3: A-Spec'],
      ],
      [
        'platform=PS2&platform=ZX_SPECTRUM',
        ['Dizzy: Prince of the Yolkfolk', 'Gran Turismo 3: A-Spec'],
      ],
      ['status=WISHLIST', ['Dizzy: Prince of the Yolkfolk']],
      ['format=DIGITAL', ['Super Mario 64']],
      [
        'completeness=CIB&completeness=LOOSE',
        ['Gran Turismo 3: A-Spec', 'The Legend of Zelda: Ocarina of Time'],
      ],
      ['genre=PLATFORMER', ['Dizzy: Prince of the Yolkfolk', 'Super Mario 64']],
      [
        'publisher=nintendo',
        ['Super Mario 64', 'The Legend of Zelda: Ocarina of Time'],
      ],
      ['storageLocation=shelf', ['The Legend of Zelda: Ocarina of Time']],
      [
        'favorite=true',
        ['Super Mario 64', 'The Legend of Zelda: Ocarina of Time'],
      ],
      [
        'favorite=false',
        ['Dizzy: Prince of the Yolkfolk', 'Gran Turismo 3: A-Spec'],
      ],
      ['hasCover=true', ['The Legend of Zelda: Ocarina of Time']],
      [
        'releaseYearFrom=1995&releaseYearTo=1999',
        ['Super Mario 64', 'The Legend of Zelda: Ocarina of Time'],
      ],
      ['purchaseDateFrom=2024-01-01', ['The Legend of Zelda: Ocarina of Time']],
      ['purchasePriceMin=100&purchasePriceMax=200', ['Gran Turismo 3: A-Spec']],
      ['estimatedValueMin=1000', ['The Legend of Zelda: Ocarina of Time']],
      ['ratingMin=8', ['The Legend of Zelda: Ocarina of Time']],
      ['platform=N64&favorite=true&q=mario', ['Super Mario 64']],
    ])('filters by %s', async (query, expected) => {
      expect(await titles(query)).toEqual(expected);
    });

    it('rejects a page beyond the limit', async () => {
      for (const page of ['1000001', '1e300']) {
        const res = await as(user).get(`/games?page=${page}`).expect(400);
        expect(res.body.code).toBe('VALIDATION_FAILED');
        expect(res.body.details.join()).toContain('page');
      }
      expect(await titles('page=1000000')).toEqual([]);
    });

    it('rejects invalid filters', async () => {
      const res = await as(user)
        .get('/games?platform=GAMEBOY&sort=price&pageSize=1000')
        .expect(400);
      expect(res.body.code).toBe('VALIDATION_FAILED');
      expect(res.body.details).toHaveLength(3);
    });

    it('returns facets of the collection', async () => {
      const res = await as(user).get('/games/facets').expect(200);
      expect(res.body).toEqual({
        totalItems: 4,
        platforms: [
          { value: 'N64', count: 2 },
          { value: 'PS2', count: 1 },
          { value: 'ZX_SPECTRUM', count: 1 },
        ],
        statuses: [
          { value: 'OWNED', count: 3 },
          { value: 'WISHLIST', count: 1 },
        ],
        genres: [
          { value: 'platformer', count: 2 },
          { value: 'Action-adventure', count: 1 },
          { value: 'Racing', count: 1 },
        ],
        publishers: [
          { value: 'Nintendo', count: 2 },
          { value: 'Codemasters', count: 1 },
          { value: 'Sony Computer Entertainment', count: 1 },
        ],
        developers: [{ value: 'Nintendo EAD', count: 1 }],
        storageLocations: [{ value: 'Shelf A', count: 1 }],
        releaseYearMin: 1991,
        releaseYearMax: 2001,
      });
    });
  });

  describe('text filters with LIKE wildcards', () => {
    beforeEach(async () => {
      await create({
        title: '100% Orange Juice',
        platform: 'PC',
        genre: 'Party',
        publisher: '50% Off Games',
      });
      await create({
        title: 'Snake_Pass',
        platform: 'PC',
        genre: 'R_G',
        developer: 'Sumo_Digital',
      });
      await create({
        title: 'C:\\Games\\Doom',
        platform: 'PC',
        genre: 'Shooter\\',
        storageLocation: 'Box\\1',
      });
      await create({ title: 'Plain', platform: 'PC', genre: 'RPG' });
    });

    const filter = async (query: Record<string, string>) => {
      const res = await as(user).get('/games').query(query).expect(200);
      return res.body.items.map((g: { title: string }) => g.title);
    };

    it.each([
      [{ q: '%' }, ['100% Orange Juice']],
      [{ q: '_' }, ['Snake_Pass']],
      [{ q: '\\' }, ['C:\\Games\\Doom']],
      [{ q: 'e_P' }, ['Snake_Pass']],
      [{ q: 's\\d' }, ['C:\\Games\\Doom']],
      [{ genre: 'R_G' }, ['Snake_Pass']],
      [{ genre: 'r%' }, []],
      [{ genre: 'rpg' }, ['Plain']],
      [{ genre: 'Shooter\\' }, ['C:\\Games\\Doom']],
      [{ publisher: '%' }, ['100% Orange Juice']],
      [{ developer: '_' }, ['Snake_Pass']],
      [{ storageLocation: '\\' }, ['C:\\Games\\Doom']],
    ])('matches %j literally', async (query, expected) => {
      expect(await filter(query)).toEqual(expected);
    });
  });
});
