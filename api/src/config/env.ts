import { plainToInstance, Transform, Type } from 'class-transformer';
import {
  IsBoolean,
  IsInt,
  IsOptional,
  IsString,
  Min,
  MinLength,
  validateSync,
} from 'class-validator';
import { ToBoolean } from '../common/transforms.js';

/** The value from `.env.example`, refused in production. */
export const EXAMPLE_JWT_SECRET =
  'change-me-to-a-long-random-string-at-least-32-chars';

export class Env {
  /**
   * `production` hides the API docs unless SWAGGER_ENABLED says otherwise,
   * and refuses the example JWT_SECRET.
   */
  @IsOptional()
  @IsString()
  NODE_ENV?: string;

  @IsString()
  DATABASE_URL: string;

  @IsString()
  @MinLength(32)
  JWT_SECRET: string;

  @Type(() => Number)
  @IsInt()
  @Min(60)
  ACCESS_TOKEN_TTL_SECONDS = 900;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  REFRESH_TOKEN_TTL_DAYS = 30;

  /**
   * Presenting a refresh token again within this many seconds after it was
   * rotated, while its successor is unused, is taken as a retry of a refresh
   * whose response was lost: it gets a new pair instead of revoking every
   * session. 0 turns this off.
   */
  @Type(() => Number)
  @IsInt()
  @Min(0)
  REFRESH_TOKEN_REUSE_GRACE_SECONDS = 120;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  PORT = 3000;

  /**
   * Express `trust proxy` value when running behind a reverse proxy, e.g. `1`
   * (one hop) or `loopback`. Needed for correct client IPs in rate limiting.
   */
  @IsOptional()
  @IsString()
  TRUST_PROXY?: string;

  /** Rate limiting; switched off only by the e2e tests. */
  @IsOptional()
  @Transform(
    ({ value }: { value: unknown }) => value !== 'false' && value !== false,
  )
  @IsBoolean()
  THROTTLE_ENABLED = true;

  /**
   * Serves Swagger UI and the OpenAPI document at /docs. Defaults to true,
   * except with NODE_ENV=production.
   */
  @IsOptional()
  @ToBoolean()
  @IsBoolean()
  SWAGGER_ENABLED: boolean;

  /**
   * UPCitemdb key of a paid plan. Without it, barcode lookups use the free
   * trial endpoint (100 lookups a day per server IP address).
   */
  @IsOptional()
  @IsString()
  UPCITEMDB_USER_KEY?: string;

  /**
   * UPCitemdb lookups one user can cause per day (UTC); cached results do not
   * count. Keeps one account from using up the shared daily quota.
   */
  @Type(() => Number)
  @IsInt()
  @Min(1)
  UPCITEMDB_DAILY_LIMIT_PER_USER = 20;

  /**
   * Twitch application credentials for IGDB, which adds genre, developer,
   * publisher, release year and cover to barcode lookups and is needed for
   * searching games by title. Optional.
   */
  @IsOptional()
  @IsString()
  IGDB_CLIENT_ID?: string;

  @IsOptional()
  @IsString()
  IGDB_CLIENT_SECRET?: string;
}

export function validateEnv(config: Record<string, unknown>): Env {
  const env = plainToInstance(Env, config);
  const messages = validateSync(env).flatMap((e) =>
    Object.values(e.constraints ?? {}),
  );
  const production = env.NODE_ENV === 'production';
  if (production && env.JWT_SECRET === EXAMPLE_JWT_SECRET) {
    messages.push(
      'JWT_SECRET must not be the example value from .env.example in production',
    );
  }
  if (messages.length > 0) {
    throw new Error(`Invalid environment:\n  ${messages.join('\n  ')}`);
  }
  env.SWAGGER_ENABLED ??= !production;
  return env;
}
