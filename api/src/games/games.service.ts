import { HttpStatus, Injectable } from '@nestjs/common';
import { ApiException } from '../common/api-exception.js';
import { ErrorCode } from '../common/error-codes.js';
import { Prisma } from '../generated/prisma/client.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { FacetValueDto, GameFacetsDto } from './dto/game-facets.dto.js';
import { GameDto, GamePageDto } from './dto/game.dto.js';
import { ListGamesQueryDto } from './dto/list-games-query.dto.js';
import { SaveGameDto } from './dto/save-game.dto.js';
import { buildGameOrderBy, buildGameWhere, toGameData } from './games.query.js';

const notFound = () =>
  new ApiException(
    HttpStatus.NOT_FOUND,
    ErrorCode.GAME_NOT_FOUND,
    'Game not found',
  );

const isRecordNotFound = (e: unknown) =>
  e instanceof Prisma.PrismaClientKnownRequestError && e.code === 'P2025';

type FacetField =
  | 'platform'
  | 'status'
  | 'genre'
  | 'publisher'
  | 'developer'
  | 'storageLocation';
const NULLABLE_FACETS = new Set<FacetField>([
  'genre',
  'publisher',
  'developer',
  'storageLocation',
]);

/** Every query is scoped to the owner, so other users' games look non-existent. */
@Injectable()
export class GamesService {
  constructor(private readonly prisma: PrismaService) {}

  async list(userId: string, query: ListGamesQueryDto): Promise<GamePageDto> {
    const where = buildGameWhere(userId, query);
    const [games, totalItems] = await this.prisma.$transaction([
      this.prisma.game.findMany({
        where,
        orderBy: buildGameOrderBy(query),
        skip: (query.page - 1) * query.pageSize,
        take: query.pageSize,
      }),
      this.prisma.game.count({ where }),
    ]);
    return {
      items: games.map((game) => GameDto.from(game)),
      page: query.page,
      pageSize: query.pageSize,
      totalItems,
      totalPages: Math.ceil(totalItems / query.pageSize),
    };
  }

  async get(userId: string, id: string): Promise<GameDto> {
    const game = await this.prisma.game.findFirst({ where: { id, userId } });
    if (!game) throw notFound();
    return GameDto.from(game);
  }

  async create(userId: string, dto: SaveGameDto): Promise<GameDto> {
    const game = await this.prisma.game.create({
      data: { ...toGameData(dto), userId },
    });
    return GameDto.from(game);
  }

  async replace(
    userId: string,
    id: string,
    dto: SaveGameDto,
  ): Promise<GameDto> {
    try {
      const game = await this.prisma.game.update({
        where: { id, userId },
        data: toGameData(dto),
      });
      return GameDto.from(game);
    } catch (e) {
      if (isRecordNotFound(e)) throw notFound();
      throw e;
    }
  }

  async remove(userId: string, id: string): Promise<void> {
    try {
      await this.prisma.game.delete({ where: { id, userId } });
    } catch (e) {
      if (isRecordNotFound(e)) throw notFound();
      throw e;
    }
  }

  async facets(userId: string): Promise<GameFacetsDto> {
    const [
      platforms,
      statuses,
      genres,
      publishers,
      developers,
      storageLocations,
      aggregate,
    ] = await Promise.all([
      this.facet(userId, 'platform'),
      this.facet(userId, 'status'),
      this.facet(userId, 'genre'),
      this.facet(userId, 'publisher'),
      this.facet(userId, 'developer'),
      this.facet(userId, 'storageLocation'),
      this.prisma.game.aggregate({
        where: { userId },
        _count: { _all: true },
        _min: { releaseYear: true },
        _max: { releaseYear: true },
      }),
    ]);

    return {
      totalItems: aggregate._count._all,
      platforms,
      statuses,
      genres,
      publishers,
      developers,
      storageLocations,
      releaseYearMin: aggregate._min.releaseYear,
      releaseYearMax: aggregate._max.releaseYear,
    };
  }

  private async facet(
    userId: string,
    field: FacetField,
  ): Promise<FacetValueDto[]> {
    const groups = await this.prisma.game.groupBy({
      by: [field],
      where: {
        userId,
        ...(NULLABLE_FACETS.has(field) && { [field]: { not: null } }),
      },
      _count: { _all: true },
    });
    const facets = groups.map((group) => ({
      value: String(group[field]),
      count: group._count._all,
    }));
    return mergeCaseVariants(facets).sort(
      (a, b) => b.count - a.count || a.value.localeCompare(b.value),
    );
  }
}

/**
 * "RPG" and "rpg" are one value for filtering (genre matches case-insensitively),
 * so they are one facet, labelled with the most common spelling.
 */
export function mergeCaseVariants(facets: FacetValueDto[]): FacetValueDto[] {
  const merged = new Map<string, FacetValueDto & { best: number }>();
  for (const { value, count } of facets) {
    const key = value.toLocaleLowerCase();
    const entry = merged.get(key);
    if (!entry) {
      merged.set(key, { value, count, best: count });
    } else {
      entry.count += count;
      if (count > entry.best) Object.assign(entry, { value, best: count });
    }
  }
  return [...merged.values()].map(({ value, count }) => ({ value, count }));
}
