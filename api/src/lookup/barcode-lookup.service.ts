import { HttpStatus, Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiException } from '../common/api-exception.js';
import { isCoverUrl } from '../common/cover-url.js';
import { ErrorCode } from '../common/error-codes.js';
import type { Env } from '../config/env.js';
import { hasValidCheckDigit, normalizeBarcode } from './barcode.js';
import { BarcodeLookupDto } from './dto/barcode-lookup.dto.js';
import { IgdbClient } from './igdb.client.js';
import type { IgdbGameDetails } from './igdb-match.js';
import { LookupUnavailableError } from './lookup-unavailable.error.js';
import { type ParsedProductTitle, parseProductTitle } from './product-title.js';
import { limit } from './text-limit.js';
import { UpcItemDbClient, type UpcProduct } from './upcitemdb.client.js';

const FOUND_TTL_MS = 7 * 24 * 60 * 60_000;
/** Unknown codes are asked again sooner: the database keeps growing. */
const NOT_FOUND_TTL_MS = 6 * 60 * 60_000;
/** A result IGDB failed to add its details to is completed soon. */
const INCOMPLETE_TTL_MS = 5 * 60_000;
const MAX_CACHED_CODES = 2_000;

const MEANINGLESS_BRANDS = /^(?:unbranded|generic|unknown|none|n\/?a)$/i;

/**
 * Finds a game by its barcode: UPCitemdb turns the code into a product
 * listing, IGDB (when configured) adds the game's details. The free UPCitemdb
 * plan allows only 100 lookups a day per server, so results are cached in
 * memory, codes with a wrong check digit are not looked up at all and every
 * user may cause only a few lookups a day.
 */
@Injectable()
export class BarcodeLookupService {
  private readonly logger = new Logger(BarcodeLookupService.name);
  /** Results by normalized code, oldest first; `null` = not found. */
  private readonly cache = new Map<
    string,
    { result: BarcodeLookupDto | null; expiresAt: number }
  >();
  /** Lookups in progress, so that repeated scans of one code share one request. */
  private readonly pending = new Map<
    string,
    Promise<BarcodeLookupDto | null>
  >();
  private readonly dailyLimitPerUser: number;
  /** UPCitemdb lookups per user on `day` (UTC, YYYY-MM-DD). */
  private upstreamCalls = { day: '', byUser: new Map<string, number>() };

  constructor(
    private readonly upcItemDb: UpcItemDbClient,
    private readonly igdb: IgdbClient,
    config: ConfigService<Env, true>,
  ) {
    this.dailyLimitPerUser = config.get('UPCITEMDB_DAILY_LIMIT_PER_USER', {
      infer: true,
    });
  }

  async lookup(barcode: string, userId: string): Promise<BarcodeLookupDto> {
    // A misread or mistyped code cannot be found; asking would only use up the quota.
    const result = hasValidCheckDigit(barcode)
      ? await this.cachedLookup(normalizeBarcode(barcode), userId)
      : null;
    if (!result) {
      throw new ApiException(
        HttpStatus.NOT_FOUND,
        ErrorCode.BARCODE_NOT_FOUND,
        'No game found for this barcode',
      );
    }
    return result;
  }

  private cachedLookup(
    code: string,
    userId: string,
  ): Promise<BarcodeLookupDto | null> {
    const cached = this.cache.get(code);
    if (cached && cached.expiresAt > Date.now()) {
      return Promise.resolve(cached.result);
    }
    let pending = this.pending.get(code);
    if (!pending) {
      this.countUpstreamCall(userId);
      pending = this.fetch(code)
        .then(({ result, ttl }) => {
          this.remember(code, result, ttl);
          return result;
        })
        .finally(() => this.pending.delete(code));
      this.pending.set(code, pending);
    }
    return pending;
  }

  /**
   * Counts a UPCitemdb lookup against the user's daily limit, so that one
   * account cannot use up the quota shared by everybody.
   */
  private countUpstreamCall(userId: string) {
    const day = new Date().toISOString().slice(0, 10);
    if (this.upstreamCalls.day !== day) {
      this.upstreamCalls = { day, byUser: new Map() };
    }
    const calls = this.upstreamCalls.byUser.get(userId) ?? 0;
    if (calls >= this.dailyLimitPerUser) {
      throw new ApiException(
        HttpStatus.TOO_MANY_REQUESTS,
        ErrorCode.TOO_MANY_REQUESTS,
        'Daily limit of barcode lookups reached; try again tomorrow',
      );
    }
    this.upstreamCalls.byUser.set(userId, calls + 1);
  }

  private remember(code: string, result: BarcodeLookupDto | null, ttl: number) {
    this.cache.delete(code);
    if (this.cache.size >= MAX_CACHED_CODES) {
      const oldest = this.cache.keys().next();
      if (!oldest.done) this.cache.delete(oldest.value);
    }
    this.cache.set(code, { result, expiresAt: Date.now() + ttl });
  }

  /** The result and how long it may be cached. */
  private async fetch(
    code: string,
  ): Promise<{ result: BarcodeLookupDto | null; ttl: number }> {
    let product: UpcProduct | null;
    try {
      product = await this.upcItemDb.lookup(code);
    } catch (e) {
      if (!(e instanceof LookupUnavailableError)) throw e;
      this.logger.warn(e.message);
      throw new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorCode.LOOKUP_UNAVAILABLE,
        'The barcode database is unavailable right now',
      );
    }
    if (!product) return { result: null, ttl: NOT_FOUND_TTL_MS };

    const parsed = parseProductTitle(product.title, [
      product.category,
      product.description,
    ]);
    let game: IgdbGameDetails | null;
    try {
      game = await this.igdb.findGame(parsed.title, parsed.platform);
    } catch (e) {
      if (!(e instanceof LookupUnavailableError)) throw e;
      // The listing alone is still worth answering with, but not for a week.
      this.logger.warn(e.message);
      return {
        result: mergeLookup(code, product, parsed, null),
        ttl: INCOMPLETE_TTL_MS,
      };
    }
    return {
      result: mergeLookup(code, product, parsed, game),
      ttl: FOUND_TTL_MS,
    };
  }
}

/** Combines the product listing with the matching IGDB game, which knows the game better. */
export function mergeLookup(
  barcode: string,
  product: UpcProduct,
  parsed: ParsedProductTitle,
  game: IgdbGameDetails | null,
): BarcodeLookupDto {
  const brand =
    product.brand && !MEANINGLESS_BRANDS.test(product.brand)
      ? product.brand
      : null;
  return {
    barcode,
    title: limit(game?.title, 200) ?? limit(parsed.title, 200) ?? '',
    platform:
      parsed.platform ??
      (game?.platforms.length === 1 ? game.platforms[0] : null),
    region: parsed.region,
    edition: limit(parsed.edition, 100),
    genre: limit(game?.genre, 100),
    developer: limit(game?.developer, 100),
    publisher: limit(game?.publisher ?? brand, 100),
    releaseYear: game?.releaseYear ?? null,
    // Only a URL the game can be saved with; listing images only over https.
    coverImageUrl:
      [
        game?.coverImageUrl,
        ...product.images.filter((url) => url.startsWith('https://')),
      ].find((url) => url != null && isCoverUrl(url)) ?? null,
    sources: game ? ['UPCitemdb', 'IGDB'] : ['UPCitemdb'],
  };
}
