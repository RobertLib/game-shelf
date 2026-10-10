import { HttpStatus, Injectable } from '@nestjs/common';
import { accountDeleted, ApiException } from '../common/api-exception.js';
import { ErrorCode } from '../common/error-codes.js';
import { type Game, Prisma } from '../generated/prisma/client.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { GameChangesDto, GameChangesQueryDto } from './dto/game-changes.dto.js';
import { FacetValueDto, GameFacetsDto } from './dto/game-facets.dto.js';
import { GameDto, GamePageDto } from './dto/game.dto.js';
import { ListGamesQueryDto } from './dto/list-games-query.dto.js';
import {
  CreateGameDto,
  SaveGameDto,
  UpdateGameDto,
} from './dto/save-game.dto.js';
import {
  buildGameOrderBy,
  buildGameWhere,
  TOMBSTONE_DATA,
  toGameData,
  toGamePatchData,
} from './games.query.js';
import { formatCursor, parseCursor } from './sync-cursor.js';

const notFound = () =>
  new ApiException(
    HttpStatus.NOT_FOUND,
    ErrorCode.GAME_NOT_FOUND,
    'Game not found',
  );

/** Not even a tombstone: the apps push such a game again as a new one. */
const unknown = () =>
  new ApiException(
    HttpStatus.NOT_FOUND,
    ErrorCode.GAME_UNKNOWN,
    'Game not found; the server has no record of it',
  );

const isPrismaError = (e: unknown, code: string) =>
  e instanceof Prisma.PrismaClientKnownRequestError && e.code === code;
const isRecordNotFound = (e: unknown) => isPrismaError(e, 'P2025');
const isUniqueViolation = (e: unknown) => isPrismaError(e, 'P2002');

/** Returns a game found by the id of a repeated create, if the caller may see it. */
function replayedCreate(userId: string, game: Game): GameDto {
  if (game.userId !== userId) {
    throw new ApiException(
      HttpStatus.CONFLICT,
      ErrorCode.CONFLICT,
      'Game id is already taken',
    );
  }
  if (game.deletedAt) throw notFound();
  return GameDto.from(game);
}

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

/**
 * Every query is scoped to the owner, so other users' games look non-existent.
 * Deleted games stay as tombstones that only the change feed sees.
 */
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

  /**
   * A deleted game (or someone else's) is `GAME_NOT_FOUND`; an id the server
   * has no record of is `GAME_UNKNOWN`, as for `update`: an app undoing a
   * rejected change must not remove a game the server merely lost.
   */
  async get(userId: string, id: string): Promise<GameDto> {
    const game = await this.prisma.game.findUnique({ where: { id } });
    if (!game) throw unknown();
    if (game.userId !== userId || game.deletedAt) throw notFound();
    return GameDto.from(game);
  }

  /**
   * Creates a game. With a client-generated id the call is idempotent: if the
   * game already exists, it is returned unchanged and `created` is false.
   */
  async create(
    userId: string,
    dto: CreateGameDto,
  ): Promise<{ game: GameDto; created: boolean }> {
    if (dto.id) {
      const existing = await this.prisma.game.findUnique({
        where: { id: dto.id },
      });
      if (existing) {
        return { game: replayedCreate(userId, existing), created: false };
      }
    }
    try {
      const game = await this.write(userId, (tx, version) =>
        tx.game.create({
          data: { ...toGameData(dto), id: dto.id, userId, version },
        }),
      );
      return { game: GameDto.from(game), created: true };
    } catch (e) {
      // A concurrent request with the same id got there first.
      if (!dto.id || !isUniqueViolation(e)) throw e;
      const existing = await this.prisma.game.findUnique({
        where: { id: dto.id },
      });
      if (!existing) throw e;
      return { game: replayedCreate(userId, existing), created: false };
    }
  }

  replace(userId: string, id: string, dto: SaveGameDto): Promise<GameDto> {
    return this.update(userId, id, toGameData(dto));
  }

  patch(userId: string, id: string, dto: UpdateGameDto): Promise<GameDto> {
    return this.update(userId, id, toGamePatchData(dto));
  }

  /**
   * Deleting a game that is already deleted succeeds, so clients can retry
   * safely. So does deleting an id the server has never seen: it stores a
   * tombstone, because a create with that id may still be on its way (a retry
   * of a request that timed out on the device) and must not bring the game
   * back when it arrives.
   */
  async remove(userId: string, id: string): Promise<void> {
    try {
      await this.write(userId, (tx, version) =>
        tx.game.update({
          where: { id, userId, deletedAt: null },
          data: { ...TOMBSTONE_DATA, deletedAt: new Date(), version },
        }),
      );
    } catch (e) {
      if (!isRecordNotFound(e)) throw e;
      const existing = await this.prisma.game.findUnique({
        where: { id },
        select: { userId: true },
      });
      if (!existing) await this.storeTombstone(userId, id);
      // Someone else's game looks non-existent; the user's own is a tombstone already.
      else if (existing.userId !== userId) throw notFound();
    }
  }

  private async storeTombstone(userId: string, id: string): Promise<void> {
    try {
      await this.write(userId, (tx, version) =>
        tx.game.create({
          data: {
            ...TOMBSTONE_DATA,
            id,
            userId,
            deletedAt: new Date(),
            version,
          },
        }),
      );
    } catch (e) {
      // The create arrived at the same moment: delete what it stored.
      if (!isUniqueViolation(e)) throw e;
      await this.remove(userId, id);
    }
  }

  /** The change feed: changes of the user's games after `cursor`, oldest first. */
  async changes(
    userId: string,
    query: GameChangesQueryDto,
  ): Promise<GameChangesDto> {
    const cursor = parseCursor(query.cursor);
    const since = cursor.version;
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { gamesVersion: true, syncEpoch: true },
    });
    if (!user) throw accountDeleted();
    const { gamesVersion, syncEpoch } = user;
    if (
      (query.cursor !== undefined && cursor.epoch !== syncEpoch) ||
      since > gamesVersion
    ) {
      // The cursor is from another state of the database, e.g. from before a restore from a
      // backup (the epoch is replaced then) or from a newer state than the restored one. A
      // cursor without an epoch (older app versions) cannot be checked, so it is not trusted.
      throw new ApiException(
        HttpStatus.GONE,
        ErrorCode.SYNC_RESET_REQUIRED,
        'Sync cursor can no longer be continued',
      );
    }

    const rows = await this.prisma.game.findMany({
      where: { userId, version: { gt: since } },
      orderBy: { version: 'asc' },
      take: query.limit + 1,
    });
    const page = rows.slice(0, query.limit);
    return {
      games: page
        .filter((game) => !game.deletedAt)
        .map((game) => GameDto.from(game)),
      deletedIds: page.filter((game) => game.deletedAt).map((game) => game.id),
      cursor: formatCursor(syncEpoch, page.at(-1)?.version ?? since),
      hasMore: rows.length > query.limit,
    };
  }

  private async update(
    userId: string,
    id: string,
    data: Prisma.GameUncheckedUpdateInput,
  ): Promise<GameDto> {
    try {
      const game = await this.write(userId, (tx, version) =>
        tx.game.update({
          where: { id, userId, deletedAt: null },
          data: { ...data, version },
        }),
      );
      return GameDto.from(game);
    } catch (e) {
      if (!isRecordNotFound(e)) throw e;
      const existing = await this.prisma.game.findUnique({
        where: { id },
        select: { id: true },
      });
      // Deleted (or someone else's), or never seen – e.g. created after the backup the
      // server was restored from.
      throw existing ? notFound() : unknown();
    }
  }

  /**
   * Runs a write to the user's games in a transaction and hands it the next
   * change number. Taking the number locks the user's row until commit, so the
   * user's changes commit in version order and the change feed cannot skip one
   * that is still in flight. (Raw SQL keeps the user's `updatedAt` untouched.)
   */
  private write<T>(
    userId: string,
    run: (tx: Prisma.TransactionClient, version: number) => Promise<T>,
  ): Promise<T> {
    return this.prisma.$transaction(async (tx) => {
      const [user] = await tx.$queryRaw<{ gamesVersion: number }[]>`
        UPDATE "users" SET "gamesVersion" = "gamesVersion" + 1
        WHERE "id" = ${userId}::uuid
        RETURNING "gamesVersion"`;
      if (!user) throw accountDeleted();
      return run(tx, user.gamesVersion);
    });
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
        where: { userId, deletedAt: null },
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
        deletedAt: null,
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
