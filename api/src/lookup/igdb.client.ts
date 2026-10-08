import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Env } from '../config/env.js';
import type { Platform } from '../generated/prisma/enums.js';
import {
  IGDB_GAME_FIELDS,
  type IgdbGame,
  type IgdbGameDetails,
  pickBestMatch,
  toGameDetails,
} from './igdb-match.js';
import { LookupUnavailableError } from './lookup-unavailable.error.js';

const TOKEN_URL = 'https://id.twitch.tv/oauth2/token';
const GAMES_URL = 'https://api.igdb.com/v4/games';
const TIMEOUT_MS = 8_000;
/** Renew the token this long before Twitch says it expires. */
const TOKEN_EXPIRY_MARGIN_MS = 5 * 60_000;

/**
 * Client of the IGDB game database (https://api-docs.igdb.com), authenticated
 * with a Twitch app access token. Optional: without credentials barcode lookups
 * get no details from it and searching by title is unavailable.
 */
@Injectable()
export class IgdbClient {
  private readonly logger = new Logger(IgdbClient.name);
  private readonly credentials: { id: string; secret: string } | null;
  private token: { value: string; expiresAt: number } | null = null;
  private tokenRequest: Promise<string> | null = null;

  constructor(config: ConfigService<Env, true>) {
    const id = config.get('IGDB_CLIENT_ID', { infer: true });
    const secret = config.get('IGDB_CLIENT_SECRET', { infer: true });
    this.credentials = id && secret ? { id, secret } : null;
  }

  /**
   * Details of the game best matching a title, or `null` when there is no
   * convincing match. Failures are logged, not thrown: IGDB only enriches lookups.
   */
  async findGame(
    title: string,
    platform: Platform | null,
  ): Promise<IgdbGameDetails | null> {
    if (!this.credentials) return null;
    try {
      const candidates = await this.query(
        `${searchClause(title)} fields ${IGDB_GAME_FIELDS}; limit 10;`,
      );
      const game = pickBestMatch(candidates, title, platform);
      return game ? toGameDetails(game) : null;
    } catch (e) {
      this.logger.warn(`IGDB search for "${title}" failed: ${String(e)}`);
      return null;
    }
  }

  /**
   * Games matching a text typed by the user, in IGDB's order of relevance;
   * editions of a game (versions) are left out, the game itself stays.
   * @throws LookupUnavailableError when IGDB is not configured or cannot answer.
   */
  async searchGames(text: string, limit: number): Promise<IgdbGame[]> {
    if (!this.credentials) {
      throw new LookupUnavailableError(
        'IGDB_CLIENT_ID and IGDB_CLIENT_SECRET are not set',
      );
    }
    try {
      return await this.query(
        `${searchClause(text)} fields ${IGDB_GAME_FIELDS}; ` +
          `where version_parent = null; limit ${limit};`,
      );
    } catch (e) {
      throw new LookupUnavailableError(
        `IGDB search for "${text}" failed: ${String(e)}`,
      );
    }
  }

  private async query(query: string): Promise<IgdbGame[]> {
    let response = await this.post(query, await this.accessToken());
    if (response.status === 401) {
      this.token = null;
      response = await this.post(query, await this.accessToken());
    }
    if (!response.ok) throw new Error(`IGDB responded with ${response.status}`);
    return (await response.json()) as IgdbGame[];
  }

  private post(query: string, token: string): Promise<Response> {
    return fetch(GAMES_URL, {
      method: 'POST',
      headers: {
        'Client-ID': this.credentials?.id ?? '',
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
        'Content-Type': 'text/plain',
      },
      body: query,
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  }

  /** The cached app access token; concurrent callers share one renewal. */
  private accessToken(): Promise<string> {
    if (this.token && this.token.expiresAt > Date.now()) {
      return Promise.resolve(this.token.value);
    }
    this.tokenRequest ??= this.requestToken().finally(() => {
      this.tokenRequest = null;
    });
    return this.tokenRequest;
  }

  private async requestToken(): Promise<string> {
    const response = await fetch(TOKEN_URL, {
      method: 'POST',
      body: new URLSearchParams({
        client_id: this.credentials?.id ?? '',
        client_secret: this.credentials?.secret ?? '',
        grant_type: 'client_credentials',
      }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!response.ok) {
      throw new Error(`Twitch token request failed with ${response.status}`);
    }
    const { access_token, expires_in } = (await response.json()) as {
      access_token: string;
      expires_in: number;
    };
    this.token = {
      value: access_token,
      expiresAt: Date.now() + expires_in * 1000 - TOKEN_EXPIRY_MARGIN_MS,
    };
    return access_token;
  }
}

function searchClause(text: string): string {
  return `search "${text.replace(/["\\]/g, ' ')}";`;
}
