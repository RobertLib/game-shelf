import type { HttpStatus } from '@nestjs/common';
import { ApiException } from '../common/api-exception.js';
import { normalizeBarcode } from './barcode.js';
import { BarcodeLookupService } from './barcode-lookup.service.js';
import type { IgdbClient } from './igdb.client.js';
import type { IgdbGameDetails } from './igdb-match.js';
import {
  LookupUnavailableError,
  type UpcItemDbClient,
  type UpcProduct,
} from './upcitemdb.client.js';

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

function setup(
  products: Record<string, UpcProduct | Error>,
  games: Record<string, IgdbGameDetails> = {},
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
    findGame: vi.fn((title: string) => Promise.resolve(games[title] ?? null)),
  };
  const service = new BarcodeLookupService(
    upcItemDb as unknown as UpcItemDbClient,
    igdb as unknown as IgdbClient,
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

    await expect(service.lookup('045496420055')).resolves.toEqual({
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
      '5030917077713': {
        title: 'Obscure Game (PS4) [UK IMPORT]',
        brand: 'Unbranded',
        category: null,
        description: null,
        images: ['http://example.com/insecure.jpg'],
      },
    });

    await expect(service.lookup('5030917077713')).resolves.toEqual({
      barcode: '5030917077713',
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
    await expect(service.lookup('045496420055')).resolves.toMatchObject({
      publisher: 'Nintendo',
      coverImageUrl: 'https://example.com/mario-kart.jpg',
    });
  });

  it('takes the platform from IGDB when the listing does not name it', async () => {
    const { service } = setup(
      { '045496420055': { ...MARIO_KART, title: 'Mario Kart 8 Deluxe' } },
      { 'Mario Kart 8 Deluxe': MARIO_KART_IGDB },
    );
    await expect(service.lookup('045496420055')).resolves.toMatchObject({
      platform: 'SWITCH',
    });
  });

  it('looks up a UPC-A scanned as EAN-13 by its 12 digits', async () => {
    const { service, upcItemDb } = setup({ '045496420055': MARIO_KART });
    await expect(service.lookup('0045496420055')).resolves.toMatchObject({
      barcode: '045496420055',
    });
    expect(upcItemDb.lookup).toHaveBeenCalledWith('045496420055');
  });

  it('reports unknown codes as BARCODE_NOT_FOUND', async () => {
    const { service } = setup({});
    expect(await errorOf(service.lookup('12345678'))).toEqual({
      status: 404,
      code: 'BARCODE_NOT_FOUND',
    });
  });

  it('reports an unavailable database as LOOKUP_UNAVAILABLE and does not cache it', async () => {
    const products: Record<string, UpcProduct | Error> = {
      '045496420055': new LookupUnavailableError('429'),
    };
    const { service } = setup(products);
    expect(await errorOf(service.lookup('045496420055'))).toEqual({
      status: 503,
      code: 'LOOKUP_UNAVAILABLE',
    });

    products['045496420055'] = MARIO_KART;
    await expect(service.lookup('045496420055')).resolves.toMatchObject({
      title: 'Mario Kart 8 Deluxe',
    });
  });

  it('caches results and misses, and shares concurrent lookups', async () => {
    const { service, upcItemDb } = setup({ '045496420055': MARIO_KART });

    await Promise.all([
      service.lookup('045496420055'),
      service.lookup('0045496420055'),
    ]);
    await service.lookup('045496420055');
    await service.lookup('12345678').catch(() => undefined);
    await service.lookup('12345678').catch(() => undefined);

    expect(upcItemDb.lookup).toHaveBeenCalledTimes(2);
  });
});

describe('normalizeBarcode', () => {
  it('drops the zeros that pad UPC-A to EAN-13 or GTIN-14', () => {
    expect(normalizeBarcode('0045496420055')).toBe('045496420055');
    expect(normalizeBarcode('00045496420055')).toBe('045496420055');
    expect(normalizeBarcode('045496420055')).toBe('045496420055');
    expect(normalizeBarcode('5030917077713')).toBe('5030917077713');
    expect(normalizeBarcode('96385074')).toBe('96385074');
  });
});
