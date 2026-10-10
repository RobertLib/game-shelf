import 'reflect-metadata';
import { EXAMPLE_JWT_SECRET, validateEnv } from './env.js';

const BASE = {
  DATABASE_URL: 'postgresql://localhost/game_shelf',
  JWT_SECRET: 'x'.repeat(32),
};

describe('validateEnv', () => {
  it('fills in the defaults', () => {
    expect(validateEnv(BASE)).toMatchObject({
      ACCESS_TOKEN_TTL_SECONDS: 900,
      REFRESH_TOKEN_TTL_DAYS: 30,
      REFRESH_TOKEN_REUSE_GRACE_SECONDS: 120,
      UPCITEMDB_DAILY_LIMIT_PER_USER: 20,
      THROTTLE_ENABLED: true,
      SWAGGER_ENABLED: true,
    });
  });

  it('parses the new settings', () => {
    expect(
      validateEnv({
        ...BASE,
        REFRESH_TOKEN_REUSE_GRACE_SECONDS: '0',
        UPCITEMDB_DAILY_LIMIT_PER_USER: '50',
      }),
    ).toMatchObject({
      REFRESH_TOKEN_REUSE_GRACE_SECONDS: 0,
      UPCITEMDB_DAILY_LIMIT_PER_USER: 50,
    });
    expect(() =>
      validateEnv({ ...BASE, UPCITEMDB_DAILY_LIMIT_PER_USER: '0' }),
    ).toThrow(/UPCITEMDB_DAILY_LIMIT_PER_USER/);
  });

  it('serves the API docs by default except in production', () => {
    const swagger = (env: Record<string, string>) =>
      validateEnv({ ...BASE, ...env }).SWAGGER_ENABLED;
    expect(swagger({ NODE_ENV: 'development' })).toBe(true);
    expect(swagger({ NODE_ENV: 'production' })).toBe(false);
    expect(swagger({ NODE_ENV: 'production', SWAGGER_ENABLED: 'true' })).toBe(
      true,
    );
    expect(swagger({ SWAGGER_ENABLED: 'false' })).toBe(false);
    expect(() => swagger({ SWAGGER_ENABLED: 'maybe' })).toThrow(
      /SWAGGER_ENABLED/,
    );
  });

  it('refuses the example JWT secret in production only', () => {
    const example = { ...BASE, JWT_SECRET: EXAMPLE_JWT_SECRET };
    expect(() => validateEnv({ ...example, NODE_ENV: 'production' })).toThrow(
      /JWT_SECRET must not be the example value/,
    );
    expect(validateEnv(example).JWT_SECRET).toBe(EXAMPLE_JWT_SECRET);
    expect(validateEnv({ ...BASE, NODE_ENV: 'production' }).JWT_SECRET).toBe(
      BASE.JWT_SECRET,
    );
  });
});
