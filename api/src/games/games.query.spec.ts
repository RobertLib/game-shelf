import { plainToInstance } from 'class-transformer';
import { validateSync } from 'class-validator';
import { ListGamesQueryDto } from './dto/list-games-query.dto.js';
import { SaveGameDto } from './dto/save-game.dto.js';
import { buildGameOrderBy, buildGameWhere, toGameData } from './games.query.js';
import { mergeCaseVariants } from './games.service.js';

const parseQuery = (query: Record<string, unknown>) => {
  const dto = plainToInstance(ListGamesQueryDto, query);
  expect(validateSync(dto)).toEqual([]);
  return dto;
};

describe('buildGameWhere', () => {
  it('always scopes to the user', () => {
    expect(buildGameWhere('u1', parseQuery({}))).toEqual({ userId: 'u1' });
  });

  it('requires every search word to match some field', () => {
    const where = buildGameWhere('u1', parseQuery({ q: '  zelda   ocarina ' }));
    expect(where.AND).toHaveLength(2);
    expect(where.AND).toContainEqual({
      OR: expect.arrayContaining([
        { title: { contains: 'zelda', mode: 'insensitive' } },
      ]),
    });
  });

  it('accepts repeated and comma-separated multi-values', () => {
    const where = buildGameWhere(
      'u1',
      parseQuery({ platform: ['PS2,PS3', 'N64'], status: 'OWNED' }),
    );
    expect(where.platform).toEqual({ in: ['PS2', 'PS3', 'N64'] });
    expect(where.status).toEqual({ in: ['OWNED'] });
  });

  it('builds open and closed ranges', () => {
    const where = buildGameWhere(
      'u1',
      parseQuery({
        releaseYearFrom: '1990',
        purchasePriceMax: '200.5',
        ratingMin: '7',
        ratingMax: '9',
      }),
    );
    expect(where.releaseYear).toEqual({ gte: 1990 });
    expect(where.purchasePrice).toEqual({ lte: 200.5 });
    expect(where.rating).toEqual({ gte: 7, lte: 9 });
  });

  it('parses booleans from query strings', () => {
    expect(
      buildGameWhere('u1', parseQuery({ favorite: 'false' })).favorite,
    ).toBe(false);
    expect(buildGameWhere('u1', parseQuery({ hasCover: 'false' })).AND).toEqual(
      [{ coverImageUrl: null }],
    );
  });
});

describe('buildGameOrderBy', () => {
  it('sorts nullable fields with nulls last and keeps pagination stable', () => {
    expect(buildGameOrderBy({ sort: 'rating', order: 'desc' })).toEqual([
      { rating: { sort: 'desc', nulls: 'last' } },
      { title: 'asc' },
      { id: 'asc' },
    ]);
  });

  it('does not repeat the title tiebreaker', () => {
    expect(buildGameOrderBy({ sort: 'title', order: 'desc' })).toEqual([
      { title: 'desc' },
      { id: 'asc' },
    ]);
  });
});

describe('toGameData', () => {
  it('fills defaults and clears omitted optional fields', () => {
    const data = toGameData(
      plainToInstance(SaveGameDto, { title: 'Doom', platform: 'PC' }),
    );
    expect(data).toMatchObject({
      status: 'OWNED',
      format: 'PHYSICAL',
      quantity: 1,
      currency: 'CZK',
      favorite: false,
      region: null,
      purchaseDate: null,
      notes: null,
    });
  });

  it('turns blank strings into null', () => {
    const dto = plainToInstance(SaveGameDto, {
      title: 'Doom',
      platform: 'PC',
      notes: '   ',
    });
    expect(validateSync(dto)).toEqual([]);
    expect(toGameData(dto).notes).toBeNull();
  });
});

describe('mergeCaseVariants', () => {
  it('merges values differing only in case under the most common spelling', () => {
    expect(
      mergeCaseVariants([
        { value: 'rpg', count: 1 },
        { value: 'RPG', count: 3 },
        { value: 'Racing', count: 2 },
      ]),
    ).toEqual([
      { value: 'RPG', count: 4 },
      { value: 'Racing', count: 2 },
    ]);
  });
});
