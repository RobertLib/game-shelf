import type { INestApplication } from '@nestjs/common';
import request from 'supertest';
import type { App } from 'supertest/types.js';
import { IgdbClient } from '../src/lookup/igdb.client.js';
import {
  LookupUnavailableError,
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
  lookup: (code: string) => {
    const product = products[code];
    return product instanceof Error
      ? Promise.reject(product)
      : Promise.resolve(product ?? null);
  },
};
const igdb = {
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

describe('Barcode lookup (e2e)', () => {
  let app: INestApplication<App>;
  let user: Session;

  const lookup = (barcode: string, session: Session | null = user) => {
    const req = request(app.getHttpServer()).get(
      `${API}/lookup/barcode/${barcode}`,
    );
    return session ? req.auth(session.accessToken, { type: 'bearer' }) : req;
  };

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
});
