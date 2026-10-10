import type { INestApplication } from '@nestjs/common';
import request from 'supertest';
import type { App } from 'supertest/types.js';
import { IgdbClient } from '../src/lookup/igdb.client.js';
import type { IgdbGame } from '../src/lookup/igdb-match.js';
import { LookupUnavailableError } from '../src/lookup/lookup-unavailable.error.js';
import {
  UpcItemDbClient,
  type UpcProduct,
} from '../src/lookup/upcitemdb.client.js';
import {
  API,
  createTestApp,
  registerUser,
  resetDatabase,
  type Session,
} from './test-app.js';

/** Stand-ins for the external databases, keyed by barcode / title. */
const products: Record<string, UpcProduct | Error> = {
  '045496420055': {
    title: 'Mario Kart 8 Deluxe - Nintendo Switch',
    brand: 'Nintendo',
    category: 'Video Games',
    description: null,
    images: [],
  },
  '711719541028': new LookupUnavailableError('EXCEED_LIMIT'),
};
const upcItemDb = {
  lookup: vi.fn((code: string) => {
    const product = products[code];
    return product instanceof Error
      ? Promise.reject(product)
      : Promise.resolve(product ?? null);
  }),
};
/** IGDB search results by query; the search itself is not stood in for. */
const igdbGames: Record<string, IgdbGame[] | Error> = {
  zelda: [
    {
      id: 1029,
      name: 'The Legend of Zelda: Ocarina of Time',
      first_release_date: 911606400,
      game_type: 0,
      genres: [{ id: 31, name: 'Adventure' }],
      platforms: [{ id: 4, name: 'Nintendo 64' }],
      cover: { id: 1, image_id: 'co3nnx' },
      involved_companies: [
        {
          id: 1,
          developer: true,
          publisher: true,
          company: { id: 70, name: 'Nintendo' },
        },
      ],
    },
  ],
  offline: new LookupUnavailableError('IGDB responded with 500'),
};
const igdb = {
  searchGames: (text: string) => {
    const games = igdbGames[text] ?? [];
    return games instanceof Error
      ? Promise.reject(games)
      : Promise.resolve(games);
  },
  findGame: (title: string) =>
    Promise.resolve(
      title === 'Mario Kart 8 Deluxe'
        ? {
            title: 'Mario Kart 8 Deluxe',
            platforms: ['SWITCH'],
            genre: 'Racing',
            developer: 'Nintendo EPD',
            publisher: 'Nintendo',
            releaseYear: 2017,
            coverImageUrl:
              'https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg',
          }
        : null,
    ),
};

describe('Lookup (e2e)', () => {
  let app: INestApplication<App>;
  let user: Session;

  const lookup = (barcode: string, session: Session | null = user) => {
    const req = request(app.getHttpServer()).get(
      `${API}/lookup/barcode/${barcode}`,
    );
    return session ? req.auth(session.accessToken, { type: 'bearer' }) : req;
  };

  const search = (query: Record<string, string>, session: Session = user) =>
    request(app.getHttpServer())
      .get(`${API}/lookup/games`)
      .query(query)
      .auth(session.accessToken, { type: 'bearer' });

  beforeAll(async () => {
    app = await createTestApp((builder) =>
      builder
        .overrideProvider(UpcItemDbClient)
        .useValue(upcItemDb)
        .overrideProvider(IgdbClient)
        .useValue(igdb),
    );
    await resetDatabase(app);
    user = await registerUser(app);
  });

  afterAll(() => app.close());

  it('returns the details of the game', async () => {
    const res = await lookup('0045496420055').expect(200);
    expect(res.body).toEqual({
      barcode: '045496420055',
      title: 'Mario Kart 8 Deluxe',
      platform: 'SWITCH',
      region: null,
      edition: null,
      genre: 'Racing',
      developer: 'Nintendo EPD',
      publisher: 'Nintendo',
      releaseYear: 2017,
      coverImageUrl:
        'https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg',
      sources: ['UPCitemdb', 'IGDB'],
    });
  });

  it('requires a signed-in user', async () => {
    const res = await lookup('045496420055', null).expect(401);
    expect(res.body.code).toBe('UNAUTHORIZED');
  });

  it('validates the barcode', async () => {
    const res = await lookup('12-34').expect(400);
    expect(res.body).toMatchObject({
      code: 'VALIDATION_FAILED',
      details: ['barcode must contain 8–14 digits'],
    });
  });

  it('reports unknown barcodes', async () => {
    const res = await lookup('12345670').expect(404);
    expect(res.body.code).toBe('BARCODE_NOT_FOUND');
  });

  it('reports an unavailable database', async () => {
    const res = await lookup('711719541028').expect(503);
    expect(res.body.code).toBe('LOOKUP_UNAVAILABLE');
  });

  it('reports a code with a wrong check digit as unknown without asking', async () => {
    upcItemDb.lookup.mockClear();
    const res = await lookup('045496420056').expect(404);
    expect(res.body.code).toBe('BARCODE_NOT_FOUND');
    expect(upcItemDb.lookup).not.toHaveBeenCalled();
  });

  it('limits the lookups in the barcode database per user and day', async () => {
    const collector = await registerUser(app);
    await lookup('045496420055').expect(200);
    // 20 by default; unknown codes count too, they were asked for.
    for (let i = 0; i < 20; i++) {
      await lookup(String(20_000_000 + i), collector).expect(404);
    }
    const res = await lookup('20000020', collector).expect(429);
    expect(res.body.code).toBe('TOO_MANY_REQUESTS');

    // Results found before are free, and other users have their own limit.
    await lookup('20000000', collector).expect(404);
    await lookup('045496420055', collector).expect(200);
    await lookup('20000020', user).expect(404);
  });

  it('searches games by title', async () => {
    const res = await search({ q: '  zelda ', platform: 'N64' }).expect(200);
    expect(res.body).toEqual({
      items: [
        {
          igdbId: 1029,
          title: 'The Legend of Zelda: Ocarina of Time',
          platforms: ['N64'],
          genre: 'Adventure',
          developer: 'Nintendo',
          publisher: 'Nintendo',
          releaseYear: 1998,
          coverImageUrl:
            'https://images.igdb.com/igdb/image/upload/t_cover_big/co3nnx.jpg',
        },
      ],
      sources: ['IGDB'],
    });
  });

  it('finds nothing without an error', async () => {
    const res = await search({ q: 'no such game' }).expect(200);
    expect(res.body).toEqual({ items: [], sources: ['IGDB'] });
  });

  it('validates the search query', async () => {
    const short = await search({ q: ' z ' }).expect(400);
    expect(short.body.code).toBe('VALIDATION_FAILED');
    const platform = await search({ q: 'zelda', platform: 'GAMEBOY' }).expect(
      400,
    );
    expect(platform.body.code).toBe('VALIDATION_FAILED');
  });

  it('reports an unavailable game database', async () => {
    const res = await search({ q: 'offline' }).expect(503);
    expect(res.body.code).toBe('LOOKUP_UNAVAILABLE');
  });
});
