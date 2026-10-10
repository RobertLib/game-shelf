import type { HttpStatus } from '@nestjs/common';
import type { ConfigService } from '@nestjs/config';
import { ApiException } from '../common/api-exception.js';
import type { Env } from '../config/env.js';
import { hasValidCheckDigit, normalizeBarcode } from './barcode.js';
import { BarcodeLookupService } from './barcode-lookup.service.js';
import type { IgdbClient } from './igdb.client.js';
import type { IgdbGameDetails } from './igdb-match.js';
import { LookupUnavailableError } from './lookup-unavailable.error.js';
import type { UpcItemDbClient, UpcProduct } from './upcitemdb.client.js';

const MARIO_KART: UpcProduct = {
  title: 'Mario Kart 8 Deluxe - Nintendo Switch',
  brand: 'Nintendo',
  category: 'Video Games',
  description: null,
  images: [
    'http://example.com/insecure.jpg',
    'https://example.com/mario-kart.jpg',
  ],
};

const MARIO_KART_IGDB: IgdbGameDetails = {
  title: 'Mario Kart 8 Deluxe',
  platforms: ['SWITCH'],
  genre: 'Racing',
  developer: 'Nintendo EPD',
  publisher: 'Nintendo',
  releaseYear: 2017,
  coverImageUrl: 'https://images.igdb.com/cover.jpg',
};

const USER = 'user-1';

function setup(
  products: Record<string, UpcProduct | Error>,
  games: Record<string, IgdbGameDetails | Error> = {},
  dailyLimitPerUser = 20,
) {
  const upcItemDb = {
    lookup: vi.fn((code: string) => {
      const product = products[code];
      return product instanceof Error
        ? Promise.reject(product)
        : Promise.resolve(product ?? null);
    }),
  };
  const igdb = {
    findGame: vi.fn((title: string) => {
      const game = games[title];
      return game instanceof Error
        ? Promise.reject(game)
        : Promise.resolve(game ?? null);
    }),
  };
  const config = { get: () => dailyLimitPerUser };
  const service = new BarcodeLookupService(
    upcItemDb as unknown as UpcItemDbClient,
    igdb as unknown as IgdbClient,
    config as unknown as ConfigService<Env, true>,
  );
  return { service, upcItemDb, igdb };
}

const errorOf = async (promise: Promise<unknown>) => {
  const error = await promise.then(
    () => null,
    (e: unknown) => e,
  );
  expect(error).toBeInstanceOf(ApiException);
  const apiError = error as ApiException;
  return { status: apiError.getStatus() as HttpStatus, code: apiError.code };
};

describe('BarcodeLookupService', () => {
  it('combines the product listing with the IGDB game', async () => {
    const { service, igdb } = setup(
      { '045496420055': MARIO_KART },
      { 'Mario Kart 8 Deluxe': MARIO_KART_IGDB },
    );

    await expect(service.lookup('045496420055', USER)).resolves.toEqual({
      barcode: '045496420055',
      title: 'Mario Kart 8 Deluxe',
      platform: 'SWITCH',
      region: null,
      edition: null,
      genre: 'Racing',
      developer: 'Nintendo EPD',
      publisher: 'Nintendo',
      releaseYear: 2017,
      coverImageUrl: 'https://images.igdb.com/cover.jpg',
      sources: ['UPCitemdb', 'IGDB'],
    });
    expect(igdb.findGame).toHaveBeenCalledWith('Mario Kart 8 Deluxe', 'SWITCH');
  });

  it('falls back to the listing when IGDB does not know the game', async () => {
    const { service } = setup({
      '5030917077715': {
        title: 'Obscure Game (PS4) [UK IMPORT]',
        brand: 'Unbranded',
        category: null,
        description: null,
        images: ['http://example.com/insecure.jpg'],
      },
    });

    await expect(service.lookup('5030917077715', USER)).resolves.toEqual({
      barcode: '5030917077715',
      title: 'Obscure Game',
      platform: 'PS4',
      region: 'PAL',
      edition: null,
      genre: null,
      developer: null,
      publisher: null,
      releaseYear: null,
      coverImageUrl: null,
      sources: ['UPCitemdb'],
    });
  });

  it('uses the listing brand and the https image as fallbacks', async () => {
    const { service } = setup({ '045496420055': MARIO_KART });
    await expect(service.lookup('045496420055', USER)).resolves.toMatchObject({
      publisher: 'Nintendo',
      coverImageUrl: 'https://example.com/mario-kart.jpg',
    });
  });

  it('takes the platform from IGDB when the listing does not name it', async () => {
    const { service } = setup(
      { '045496420055': { ...MARIO_KART, title: 'Mario Kart 8 Deluxe' } },
      { 'Mario Kart 8 Deluxe': MARIO_KART_IGDB },
    );
    await expect(service.lookup('045496420055', USER)).resolves.toMatchObject({
      platform: 'SWITCH',
    });
  });

  it('looks up a UPC-A scanned as EAN-13 by its 12 digits', async () => {
    const { service, upcItemDb } = setup({ '045496420055': MARIO_KART });
    await expect(service.lookup('0045496420055', USER)).resolves.toMatchObject({
      barcode: '045496420055',
    });
    expect(upcItemDb.lookup).toHaveBeenCalledWith('045496420055');
  });

  it('reports unknown codes as BARCODE_NOT_FOUND', async () => {
    const { service } = setup({});
    expect(await errorOf(service.lookup('12345678', USER))).toEqual({
      status: 404,
      code: 'BARCODE_NOT_FOUND',
    });
  });

  it('reports an unavailable database as LOOKUP_UNAVAILABLE and does not cache it', async () => {
    const products: Record<string, UpcProduct | Error> = {
      '045496420055': new LookupUnavailableError('429'),
    };
    const { service } = setup(products);
    expect(await errorOf(service.lookup('045496420055', USER))).toEqual({
      status: 503,
      code: 'LOOKUP_UNAVAILABLE',
    });

    products['045496420055'] = MARIO_KART;
    await expect(service.lookup('045496420055', USER)).resolves.toMatchObject({
      title: 'Mario Kart 8 Deluxe',
    });
  });

  it('caches results and misses, and shares concurrent lookups', async () => {
    const { service, upcItemDb } = setup({ '045496420055': MARIO_KART });

    await Promise.all([
      service.lookup('045496420055', USER),
      service.lookup('0045496420055', USER),
    ]);
    await service.lookup('045496420055', USER);
    await service.lookup('12345678', USER).catch(() => undefined);
    await service.lookup('12345678', USER).catch(() => undefined);

    expect(upcItemDb.lookup).toHaveBeenCalledTimes(2);
  });

  it('answers a code with a wrong check digit as unknown without asking', async () => {
    const { service, upcItemDb } = setup({ '045496420056': MARIO_KART });
    for (const code of ['045496420056', '0045496420056', '5030917077713']) {
      expect(await errorOf(service.lookup(code, USER))).toEqual({
        status: 404,
        code: 'BARCODE_NOT_FOUND',
      });
    }
    expect(upcItemDb.lookup).not.toHaveBeenCalled();
  });

  describe('with fake time', () => {
    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date('2026-10-10T08:00:00Z'));
    });
    afterEach(() => vi.useRealTimers());

    const later = (ms: number) => vi.setSystemTime(Date.now() + ms);

    it('limits the UPCitemdb lookups of every user per day', async () => {
      const { service, upcItemDb } = setup(
        { '045496420055': MARIO_KART },
        {},
        2,
      );
      await service.lookup('045496420055', USER);
      await service.lookup('0045496420055', USER); // cached
      await service.lookup('12345670', USER).catch(() => undefined); // unknown, but asked
      expect(await errorOf(service.lookup('96385074', USER))).toEqual({
        status: 429,
        code: 'TOO_MANY_REQUESTS',
      });
      expect(upcItemDb.lookup).toHaveBeenCalledTimes(2);

      // Cached results are still answered, and other users have their own limit.
      await expect(service.lookup('045496420055', USER)).resolves.toMatchObject(
        { title: 'Mario Kart 8 Deluxe' },
      );
      expect(await errorOf(service.lookup('96385074', 'user-2'))).toEqual({
        status: 404,
        code: 'BARCODE_NOT_FOUND',
      });

      // A new day (UTC) starts with a new limit.
      vi.setSystemTime(new Date('2026-10-11T00:00:01Z'));
      expect(await errorOf(service.lookup('11111115', USER))).toEqual({
        status: 404,
        code: 'BARCODE_NOT_FOUND',
      });
      expect(upcItemDb.lookup).toHaveBeenCalledTimes(4);
    });

    it('does not count joining a lookup in progress', async () => {
      const { service, upcItemDb } = setup(
        { '045496420055': MARIO_KART },
        {},
        1,
      );
      await Promise.all([
        service.lookup('045496420055', USER),
        service.lookup('045496420055', 'user-2'),
      ]);
      expect(await errorOf(service.lookup('96385074', 'user-2'))).toEqual({
        status: 404,
        code: 'BARCODE_NOT_FOUND',
      });
      expect(await errorOf(service.lookup('12345670', USER))).toEqual({
        status: 429,
        code: 'TOO_MANY_REQUESTS',
      });
      expect(upcItemDb.lookup).toHaveBeenCalledTimes(2);
    });

    it('caches a result IGDB failed to complete only for a few minutes', async () => {
      const games: Record<string, IgdbGameDetails | Error> = {
        'Mario Kart 8 Deluxe': new LookupUnavailableError('IGDB is down'),
      };
      const { service, upcItemDb } = setup(
        { '045496420055': MARIO_KART },
        games,
      );
      await expect(service.lookup('045496420055', USER)).resolves.toMatchObject(
        { genre: null, sources: ['UPCitemdb'] },
      );

      games['Mario Kart 8 Deluxe'] = MARIO_KART_IGDB;
      later(4 * 60_000);
      await expect(service.lookup('045496420055', USER)).resolves.toMatchObject(
        { genre: null },
      );
      later(2 * 60_000);
      await expect(service.lookup('045496420055', USER)).resolves.toMatchObject(
        {
          genre: 'Racing',
          sources: ['UPCitemdb', 'IGDB'],
        },
      );
      expect(upcItemDb.lookup).toHaveBeenCalledTimes(2);
    });

    it('caches a result IGDB has no match for like any other', async () => {
      const { service, upcItemDb } = setup({ '045496420055': MARIO_KART });
      await service.lookup('045496420055', USER);
      later(24 * 60 * 60_000);
      await expect(service.lookup('045496420055', USER)).resolves.toMatchObject(
        { sources: ['UPCitemdb'] },
      );
      expect(upcItemDb.lookup).toHaveBeenCalledTimes(1);
    });
  });

  it('returns only cover URLs a game can be saved with', async () => {
    const { service } = setup(
      {
        '045496420055': {
          ...MARIO_KART,
          images: [
            'https://exa mple.com/a.jpg',
            'https://example.com/é.jpg',
            `https://example.com/${'a'.repeat(2048)}.jpg`,
            'https://example.com/ok.jpg',
          ],
        },
      },
      {
        'Mario Kart 8 Deluxe': {
          ...MARIO_KART_IGDB,
          coverImageUrl: 'https://images.igdb.com/a b.jpg',
        },
      },
    );
    await expect(service.lookup('045496420055', USER)).resolves.toMatchObject({
      coverImageUrl: 'https://example.com/ok.jpg',
    });
  });
});

describe('hasValidCheckDigit', () => {
  it('checks the GTIN check digit of 12–14 digit codes', () => {
    for (const code of [
      '045496420055',
      '0045496420055',
      '00045496420055',
      '5030917077715',
      '3391892017625',
    ]) {
      expect(hasValidCheckDigit(code)).toBe(true);
    }
    for (const code of ['045496420056', '5030917077713', '10045496420055']) {
      expect(hasValidCheckDigit(code)).toBe(false);
    }
  });

  it('does not check 8-digit codes, which can be EAN-8 or UPC-E', () => {
    expect(hasValidCheckDigit('12345678')).toBe(true);
    expect(hasValidCheckDigit('96385074')).toBe(true);
  });
});

describe('normalizeBarcode', () => {
  it('drops the zeros that pad UPC-A to EAN-13 or GTIN-14', () => {
    expect(normalizeBarcode('0045496420055')).toBe('045496420055');
    expect(normalizeBarcode('00045496420055')).toBe('045496420055');
    expect(normalizeBarcode('045496420055')).toBe('045496420055');
    expect(normalizeBarcode('5030917077715')).toBe('5030917077715');
    expect(normalizeBarcode('96385074')).toBe('96385074');
  });
});
