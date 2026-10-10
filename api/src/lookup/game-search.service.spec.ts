import type { HttpStatus } from '@nestjs/common';
import { plainToInstance } from 'class-transformer';
import { validateSync } from 'class-validator';
import { ApiException } from '../common/api-exception.js';
import { GameSearchQueryDto } from './dto/game-search.dto.js';
import { GameSearchService } from './game-search.service.js';
import type { IgdbClient } from './igdb.client.js';
import type { IgdbGame } from './igdb-match.js';
import { LookupUnavailableError } from './lookup-unavailable.error.js';

const MARIO_KART_8: IgdbGame = {
  id: 2350,
  name: 'Mario Kart 8',
  first_release_date: 1401321600,
  game_type: 0,
  platforms: [{ id: 41, name: 'Wii U' }],
};

const MARIO_KART_8_DELUXE: IgdbGame = {
  id: 26758,
  name: 'Mario Kart 8 Deluxe',
  first_release_date: 1493337600,
  game_type: 0,
  genres: [{ id: 10, name: 'Racing' }],
  platforms: [{ id: 130, name: 'Nintendo Switch' }],
  cover: { id: 1, image_id: 'co213p' },
  involved_companies: [
    {
      id: 1,
      developer: true,
      publisher: false,
      company: { id: 1, name: 'Nintendo EPD' },
    },
    {
      id: 2,
      developer: false,
      publisher: true,
      company: { id: 2, name: 'Nintendo' },
    },
  ],
};

const BOOSTER_COURSE_PASS: IgdbGame = {
  id: 197001,
  name: 'Mario Kart 8 Deluxe: Booster Course Pass',
  game_type: 1,
  platforms: [{ id: 130, name: 'Nintendo Switch' }],
};

function setup(games: IgdbGame[] | Error) {
  const igdb = {
    searchGames: vi.fn(() =>
      games instanceof Error ? Promise.reject(games) : Promise.resolve(games),
    ),
  };
  const service = new GameSearchService(igdb as unknown as IgdbClient);
  return { service, igdb };
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

const titles = (response: { items: { title: string }[] }) =>
  response.items.map((item) => item.title);

describe('GameSearchService', () => {
  it('returns the details of every game found', async () => {
    const { service } = setup([MARIO_KART_8_DELUXE]);

    await expect(service.search('mario kart', null)).resolves.toEqual({
      items: [
        {
          igdbId: 26758,
          title: 'Mario Kart 8 Deluxe',
          platforms: ['SWITCH'],
          genre: 'Racing',
          developer: 'Nintendo EPD',
          publisher: 'Nintendo',
          releaseYear: 2017,
          coverImageUrl:
            'https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg',
        },
      ],
      sources: ['IGDB'],
    });
  });

  it('leaves out DLC and other add-ons', async () => {
    const { service } = setup([BOOSTER_COURSE_PASS, MARIO_KART_8_DELUXE]);
    expect(titles(await service.search('mario kart 8', null))).toEqual([
      'Mario Kart 8 Deluxe',
    ]);
  });

  it('keeps the order of IGDB, but puts the exact title first', async () => {
    const { service } = setup([MARIO_KART_8_DELUXE, MARIO_KART_8]);

    expect(titles(await service.search('mario kart', null))).toEqual([
      'Mario Kart 8 Deluxe',
      'Mario Kart 8',
    ]);
    expect(titles(await service.search('Mario Kart 8', null))).toEqual([
      'Mario Kart 8',
      'Mario Kart 8 Deluxe',
    ]);
  });

  it('puts games on the chosen platform first', async () => {
    const { service } = setup([MARIO_KART_8, MARIO_KART_8_DELUXE]);
    expect(titles(await service.search('Mario Kart 8', 'SWITCH'))).toEqual([
      'Mario Kart 8 Deluxe',
      'Mario Kart 8',
    ]);
  });

  it('cuts long values to the limits of a saved game', async () => {
    const { service } = setup([
      {
        id: 1,
        name: ` ${'A'.repeat(250)} `,
        genres: [{ id: 1, name: 'G'.repeat(150) }],
      },
      { id: 2, name: '  ' },
    ]);
    const { items } = await service.search('aaa', null);
    expect(items).toHaveLength(1);
    expect(items[0].title).toHaveLength(200);
    expect(items[0].genre).toHaveLength(100);
    expect(items[0].platforms).toEqual([]);
  });

  it('caches results by query regardless of letter case', async () => {
    const { service, igdb } = setup([MARIO_KART_8, MARIO_KART_8_DELUXE]);

    await service.search('Mario Kart', null);
    const onSwitch = await service.search('mario kart', 'SWITCH');

    expect(igdb.searchGames).toHaveBeenCalledTimes(1);
    expect(igdb.searchGames).toHaveBeenCalledWith('Mario Kart', 30);
    expect(titles(onSwitch)[0]).toBe('Mario Kart 8 Deluxe');
  });

  it('reports an unavailable database as LOOKUP_UNAVAILABLE', async () => {
    const { service } = setup(new LookupUnavailableError('not configured'));
    expect(await errorOf(service.search('zelda', null))).toEqual({
      status: 503,
      code: 'LOOKUP_UNAVAILABLE',
    });
  });
});

describe('GameSearchQueryDto', () => {
  it('counts the query length in code points', () => {
    const valid = (q: string) =>
      validateSync(plainToInstance(GameSearchQueryDto, { q })).length === 0;
    expect(valid('e\u0301')).toBe(true);
    expect(valid(' 😀 ')).toBe(false);
    expect(valid('😀'.repeat(100))).toBe(true);
    expect(valid('❤️'.repeat(51))).toBe(false);
  });
});
