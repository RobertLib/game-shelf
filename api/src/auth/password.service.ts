import { Injectable } from '@nestjs/common';
import {
  randomBytes,
  scrypt,
  timingSafeEqual,
  type ScryptOptions,
} from 'node:crypto';

// OWASP-recommended scrypt parameters (N=2^15, r=8, p=3 → ~32 MiB per hash).
const PARAMS = { N: 2 ** 15, r: 8, p: 3 };
const KEY_LENGTH = 64;
const SALT_LENGTH = 16;

function derive(
  password: string,
  salt: Buffer,
  params: typeof PARAMS,
): Promise<Buffer> {
  const options: ScryptOptions = {
    ...params,
    maxmem: 128 * params.N * params.r * 2,
  };
  return new Promise((resolve, reject) =>
    scrypt(password, salt, KEY_LENGTH, options, (err, key) =>
      err ? reject(err) : resolve(key),
    ),
  );
}

/**
 * Hashes passwords with scrypt from Node's standard library, so there is no
 * native dependency to build. Hashes are stored as
 * `scrypt$N$r$p$<salt base64>$<key base64>` to allow tuning parameters later.
 */
@Injectable()
export class PasswordService {
  async hash(password: string): Promise<string> {
    const salt = randomBytes(SALT_LENGTH);
    const key = await derive(password, salt, PARAMS);
    return [
      'scrypt',
      PARAMS.N,
      PARAMS.r,
      PARAMS.p,
      salt.toString('base64'),
      key.toString('base64'),
    ].join('$');
  }

  async verify(password: string, stored: string): Promise<boolean> {
    const [algorithm, n, r, p, salt, key] = stored.split('$');
    if (algorithm !== 'scrypt' || !key) return false;
    const expected = Buffer.from(key, 'base64');
    const actual = await derive(password, Buffer.from(salt, 'base64'), {
      N: Number(n),
      r: Number(r),
      p: Number(p),
    });
    return (
      actual.length === expected.length && timingSafeEqual(actual, expected)
    );
  }
}
