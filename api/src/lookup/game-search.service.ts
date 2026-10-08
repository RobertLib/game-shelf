import { HttpStatus, Injectable, Logger } from '@nestjs/common';
import { ApiException } from '../common/api-exception.js';
import { ErrorCode } from '../common/error-codes.js';
import type { Platform } from '../generated/prisma/enums.js';
import {
  GameSearchResponseDto,
  GameSearchResultDto,
} from './dto/game-search.dto.js';
import { IgdbClient } from './igdb.client.js';
import {
  type IgdbGame,
  isAddOn,
  titleSimilarity,
  toGameDetails,
} from './igdb-match.js';
import { LookupUnavailableError } from './lookup-unavailable.error.js';
import { limit } from './text-limit.js';

const CACHE_TTL_MS = 24 * 60 * 60_000;
const MAX_CACHED_QUERIES = 500;
/** Asked for more than returned, because add-ons are dropped. */
const IGDB_LIMIT = 30;
const MAX_RESULTS = 20;

/**
 * Finds games by title in IGDB, to prefill a game typed in by hand. The apps
 * search as the user types, so results are cached in memory by query.
 */
@Injectable()
export class GameSearchService {
  private readonly logger = new Logger(GameSearchService.name);
  /** Results by lowercased query, oldest first. */
  private readonly cache = new Map<
    string,
    { results: GameSearchResultDto[]; expiresAt: number }
  >();

  constructor(private readonly igdb: IgdbClient) {}

  async search(
    query: string,
    platform: Platform | null,
  ): Promise<GameSearchResponseDto> {
    const results = await this.cachedSearch(query);
    return {
      items: rankResults(results, query, platform).slice(0, MAX_RESULTS),
      sources: ['IGDB'],
    };
  }

  private async cachedSearch(query: string): Promise<GameSearchResultDto[]> {
    const key = query.toLowerCase();
    const cached = this.cache.get(key);
    if (cached && cached.expiresAt > Date.now()) return cached.results;

    let games: IgdbGame[];
    try {
      games = await this.igdb.searchGames(query, IGDB_LIMIT);
    } catch (e) {
      if (!(e instanceof LookupUnavailableError)) throw e;
      this.logger.warn(e.message);
      throw new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorCode.LOOKUP_UNAVAILABLE,
        'The game database is unavailable right now',
      );
    }
    const results = games
      .filter((game) => !isAddOn(game))
      .map(toSearchResult)
      .filter((result) => result !== null);

    this.cache.delete(key);
    if (this.cache.size >= MAX_CACHED_QUERIES) {
      const oldest = this.cache.keys().next();
      if (!oldest.done) this.cache.delete(oldest.value);
    }
    this.cache.set(key, { results, expiresAt: Date.now() + CACHE_TTL_MS });
    return results;
  }
}

export function toSearchResult(game: IgdbGame): GameSearchResultDto | null {
  const details = toGameDetails(game);
  const title = limit(details.title, 200);
  if (!title) return null;
  return {
    igdbId: game.id,
    title,
    platforms: details.platforms,
    genre: limit(details.genre, 100),
    developer: limit(details.developer, 100),
    publisher: limit(details.publisher, 100),
    releaseYear: details.releaseYear,
    coverImageUrl: details.coverImageUrl,
  };
}

/**
 * Games on the chosen platform first, then the one with exactly the typed
 * title; otherwise IGDB's order of relevance is kept.
 */
export function rankResults(
  results: GameSearchResultDto[],
  query: string,
  platform: Platform | null,
): GameSearchResultDto[] {
  const rank = (result: GameSearchResultDto) =>
    (platform && result.platforms.includes(platform) ? 0 : 2) +
    (titleSimilarity(query, result.title) === 1 ? 0 : 1);
  return results
    .map((result) => ({ result, rank: rank(result) }))
    .sort((a, b) => a.rank - b.rank)
    .map(({ result }) => result);
}
