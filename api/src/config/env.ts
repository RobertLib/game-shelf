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

export class Env {
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
}

export function validateEnv(config: Record<string, unknown>): Env {
  const env = plainToInstance(Env, config);
  const errors = validateSync(env);
  if (errors.length > 0) {
    const messages = errors.flatMap((e) => Object.values(e.constraints ?? {}));
    throw new Error(`Invalid environment:\n  ${messages.join('\n  ')}`);
  }
  return env;
}
