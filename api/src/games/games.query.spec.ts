import { plainToInstance } from 'class-transformer';
import { validateSync } from 'class-validator';
import { ListGamesQueryDto } from './dto/list-games-query.dto.js';
import { SaveGameDto, UpdateGameDto } from './dto/save-game.dto.js';
import {
  buildGameOrderBy,
  buildGameWhere,
  toGameData,
  toGamePatchData,
} from './games.query.js';
import { mergeCaseVariants } from './games.service.js';

const parseQuery = (query: Record<string, unknown>) => {
  const dto = plainToInstance(ListGamesQueryDto, query);
  expect(validateSync(dto)).toEqual([]);
  return dto;
};

describe('buildGameWhere', () => {
  it('always scopes to the user and skips deleted games', () => {
    expect(buildGameWhere('u1', parseQuery({}))).toEqual({
      userId: 'u1',
      deletedAt: null,
    });
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

describe('toGamePatchData', () => {
  const parsePatch = (body: Record<string, unknown>) => {
    const dto = plainToInstance(UpdateGameDto, body);
    return { dto, errors: validateSync(dto) };
  };

  it('changes only the fields present in the body', () => {
    const { dto, errors } = parsePatch({
      rating: 9,
      region: null,
      notes: '  ',
      purchaseDate: '2024-05-17',
    });
    expect(errors).toEqual([]);
    expect(toGamePatchData(dto)).toEqual({
      rating: 9,
      region: null,
      notes: null,
      purchaseDate: new Date('2024-05-17'),
    });
  });

  it('accepts an empty body', () => {
    const { dto, errors } = parsePatch({});
    expect(errors).toEqual([]);
    expect(toGamePatchData(dto)).toEqual({});
  });

  it('rejects null for fields that cannot be empty', () => {
    const { errors } = parsePatch({
      title: null,
      platform: null,
      status: null,
      quantity: null,
      favorite: null,
    });
    expect(errors.map((error) => error.property).sort()).toEqual([
      'favorite',
      'platform',
      'quantity',
      'status',
      'title',
    ]);
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
