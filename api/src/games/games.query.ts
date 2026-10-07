import { CollectionStatus, GameFormat } from '../generated/prisma/enums.js';
import type { Prisma } from '../generated/prisma/client.js';
import type { SaveGameDto } from './dto/save-game.dto.js';
import {
  type GameSortField,
  type ListGamesQueryDto,
} from './dto/list-games-query.dto.js';

type GameWhere = Prisma.GameWhereInput;
type GameOrderBy = Prisma.GameOrderByWithRelationInput;

const SEARCHABLE_FIELDS = [
  'title',
  'edition',
  'developer',
  'publisher',
  'genre',
  'productCode',
  'barcode',
  'notes',
] as const satisfies readonly (keyof GameWhere)[];

const NULLABLE_SORT_FIELDS = new Set<GameSortField>([
  'releaseYear',
  'purchaseDate',
  'purchasePrice',
  'estimatedValue',
  'rating',
]);

const contains = (value: string) => ({
  contains: value,
  mode: 'insensitive' as const,
});

const range = <T>(from: T | undefined, to: T | undefined) =>
  from === undefined && to === undefined
    ? undefined
    : {
        ...(from !== undefined && { gte: from }),
        ...(to !== undefined && { lte: to }),
      };

const toDate = (value: string | undefined) =>
  value === undefined ? undefined : new Date(value);

const nonEmpty = <T>(values: T[] | undefined) =>
  values?.length ? values : undefined;

/** Translates the list query into a Prisma filter scoped to one user. */
export function buildGameWhere(
  userId: string,
  query: ListGamesQueryDto,
): GameWhere {
  const and: GameWhere[] = [];

  const words = query.q?.trim().split(/\s+/).filter(Boolean) ?? [];
  for (const word of words) {
    and.push({
      OR: SEARCHABLE_FIELDS.map((field) => ({ [field]: contains(word) })),
    });
  }

  const genres = nonEmpty(query.genre);
  if (genres) {
    and.push({
      OR: genres.map((genre) => ({
        genre: { equals: genre, mode: 'insensitive' },
      })),
    });
  }

  if (query.hasCover !== undefined) {
    and.push({ coverImageUrl: query.hasCover ? { not: null } : null });
  }

  const enumIn = <T>(values: T[] | undefined) => {
    const list = nonEmpty(values);
    return list && { in: list };
  };

  const where: GameWhere = {
    userId,
    platform: enumIn(query.platform),
    status: enumIn(query.status),
    format: enumIn(query.format),
    region: enumIn(query.region),
    completeness: enumIn(query.completeness),
    condition: enumIn(query.condition),
    playStatus: enumIn(query.playStatus),
    publisher: query.publisher ? contains(query.publisher) : undefined,
    developer: query.developer ? contains(query.developer) : undefined,
    storageLocation: query.storageLocation
      ? contains(query.storageLocation)
      : undefined,
    favorite: query.favorite,
    releaseYear: range(query.releaseYearFrom, query.releaseYearTo),
    purchaseDate: range(
      toDate(query.purchaseDateFrom),
      toDate(query.purchaseDateTo),
    ),
    purchasePrice: range(query.purchasePriceMin, query.purchasePriceMax),
    estimatedValue: range(query.estimatedValueMin, query.estimatedValueMax),
    rating: range(query.ratingMin, query.ratingMax),
    ...(and.length > 0 && { AND: and }),
  };

  // Prisma treats `undefined` as "no condition", but dropping the keys keeps logs readable.
  return Object.fromEntries(
    Object.entries(where).filter(([, value]) => value !== undefined),
  ) as GameWhere;
}

/** Sort with nulls last, then by title and id so pagination is stable. */
export function buildGameOrderBy(
  query: Pick<ListGamesQueryDto, 'sort' | 'order'>,
): GameOrderBy[] {
  const { sort, order } = query;
  const primary: GameOrderBy = NULLABLE_SORT_FIELDS.has(sort)
    ? { [sort]: { sort: order, nulls: 'last' } }
    : { [sort]: order };

  return [
    primary,
    ...(sort === 'title' ? [] : [{ title: 'asc' as const }]),
    { id: 'asc' },
  ];
}

/**
 * Maps the request body to column values. Every column is set explicitly so
 * that PUT fully replaces the record.
 */
export function toGameData(dto: SaveGameDto) {
  return {
    title: dto.title,
    platform: dto.platform,
    status: dto.status ?? CollectionStatus.OWNED,
    format: dto.format ?? GameFormat.PHYSICAL,
    region: dto.region ?? null,
    edition: dto.edition ?? null,
    completeness: dto.completeness ?? null,
    condition: dto.condition ?? null,
    playStatus: dto.playStatus ?? null,
    genre: dto.genre ?? null,
    developer: dto.developer ?? null,
    publisher: dto.publisher ?? null,
    releaseYear: dto.releaseYear ?? null,
    barcode: dto.barcode ?? null,
    productCode: dto.productCode ?? null,
    quantity: dto.quantity ?? 1,
    purchasePrice: dto.purchasePrice ?? null,
    purchaseDate: dto.purchaseDate ? new Date(dto.purchaseDate) : null,
    purchasePlace: dto.purchasePlace ?? null,
    estimatedValue: dto.estimatedValue ?? null,
    currency: dto.currency ?? 'CZK',
    storageLocation: dto.storageLocation ?? null,
    rating: dto.rating ?? null,
    favorite: dto.favorite ?? false,
    coverImageUrl: dto.coverImageUrl ?? null,
    notes: dto.notes ?? null,
  } satisfies Omit<Prisma.GameUncheckedCreateInput, 'userId'>;
}
