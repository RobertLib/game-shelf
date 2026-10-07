import { HttpStatus, Injectable, Logger } from '@nestjs/common';
import { ApiException } from '../common/api-exception.js';
import { ErrorCode } from '../common/error-codes.js';
import { normalizeBarcode } from './barcode.js';
import { BarcodeLookupDto } from './dto/barcode-lookup.dto.js';
import { IgdbClient } from './igdb.client.js';
import type { IgdbGameDetails } from './igdb-match.js';
import { type ParsedProductTitle, parseProductTitle } from './product-title.js';
import {
  LookupUnavailableError,
  UpcItemDbClient,
  type UpcProduct,
} from './upcitemdb.client.js';

const FOUND_TTL_MS = 7 * 24 * 60 * 60_000;
/** Unknown codes are asked again sooner: the database keeps growing. */
const NOT_FOUND_TTL_MS = 6 * 60 * 60_000;
const MAX_CACHED_CODES = 2_000;

const MEANINGLESS_BRANDS = /^(?:unbranded|generic|unknown|none|n\/?a)$/i;

/**
 * Finds a game by its barcode: UPCitemdb turns the code into a product
 * listing, IGDB (when configured) adds the game's details. Results are cached
 * in memory, because the free UPCitemdb plan allows only 100 lookups a day.
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

  constructor(
    private readonly upcItemDb: UpcItemDbClient,
    private readonly igdb: IgdbClient,
  ) {}

  async lookup(barcode: string): Promise<BarcodeLookupDto> {
    const result = await this.cachedLookup(normalizeBarcode(barcode));
    if (!result) {
      throw new ApiException(
        HttpStatus.NOT_FOUND,
        ErrorCode.BARCODE_NOT_FOUND,
        'No game found for this barcode',
      );
    }
    return result;
  }

  private cachedLookup(code: string): Promise<BarcodeLookupDto | null> {
    const cached = this.cache.get(code);
    if (cached && cached.expiresAt > Date.now()) {
      return Promise.resolve(cached.result);
    }
    let pending = this.pending.get(code);
    if (!pending) {
      pending = this.fetch(code)
        .then((result) => {
          this.remember(code, result);
          return result;
        })
        .finally(() => this.pending.delete(code));
      this.pending.set(code, pending);
    }
    return pending;
  }

  private remember(code: string, result: BarcodeLookupDto | null) {
    this.cache.delete(code);
    if (this.cache.size >= MAX_CACHED_CODES) {
      const oldest = this.cache.keys().next();
      if (!oldest.done) this.cache.delete(oldest.value);
    }
    const ttl = result ? FOUND_TTL_MS : NOT_FOUND_TTL_MS;
    this.cache.set(code, { result, expiresAt: Date.now() + ttl });
  }

  private async fetch(code: string): Promise<BarcodeLookupDto | null> {
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
    if (!product) return null;

    const parsed = parseProductTitle(product.title, [
      product.category,
      product.description,
    ]);
    const game = await this.igdb.findGame(parsed.title, parsed.platform);
    return mergeLookup(code, product, parsed, game);
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
    coverImageUrl:
      game?.coverImageUrl ??
      product.images.find(
        (url) => url.startsWith('https://') && url.length <= 2048,
      ) ??
      null,
    sources: game ? ['UPCitemdb', 'IGDB'] : ['UPCitemdb'],
  };
}

function limit(
  text: string | null | undefined,
  maxLength: number,
): string | null {
  const trimmed = text?.trim();
  return trimmed ? trimmed.slice(0, maxLength).trim() : null;
}
