import { plainToInstance } from 'class-transformer';
import { validateSync } from 'class-validator';
import { readFileSync } from 'node:fs';
import { charLength } from '../../common/char-length.decorator.js';
import { LoginDto, RefreshTokenDto, RegisterDto } from './credentials.dto.js';

const invalid = (body: Record<string, unknown>) =>
  validateSync(
    plainToInstance(RegisterDto, { email: 'rob@example.com', ...body }),
  ).map((error) => error.property);

describe('RegisterDto', () => {
  it('counts the password length in code points', () => {
    expect(invalid({ password: '😀'.repeat(8) })).toEqual([]);
    expect(invalid({ password: 'e\u0301'.repeat(4) })).toEqual([]);
    expect(invalid({ password: '😀'.repeat(7) })).toEqual(['password']);
    expect(invalid({ password: '🇨🇿'.repeat(64) })).toEqual([]);
    expect(invalid({ password: '🇨🇿'.repeat(64) + 'x' })).toEqual(['password']);
  });

  it('counts the display name and e-mail length in code points', () => {
    const password = 'super-secret';
    expect(invalid({ password, displayName: '😀'.repeat(100) })).toEqual([]);
    expect(invalid({ password, displayName: '❤️'.repeat(51) })).toEqual([
      'displayName',
    ]);
    expect(
      invalid({ password, email: `${'a'.repeat(64)}@${'b'.repeat(185)}.com` }),
    ).toEqual(['email']);
  });
});

describe('RefreshTokenDto', () => {
  it('limits the token length', () => {
    const errors = (refreshToken: string) =>
      validateSync(plainToInstance(RefreshTokenDto, { refreshToken })).length;
    expect(errors('a'.repeat(200))).toBe(0);
    expect(errors('a'.repeat(201))).toBe(1);
  });
});

describe('LoginDto', () => {
  it('does not check the format of the e-mail, so an unknown one gets INVALID_CREDENTIALS', () => {
    const invalid = (email: unknown) =>
      validateSync(
        plainToInstance(LoginDto, { email, password: 'whatever' }),
      ).map((error) => error.property);
    expect(invalid('a..b@example.com')).toEqual([]);
    expect(invalid('not-an-email')).toEqual([]);
    expect(invalid('  ')).toEqual(['email']);
    expect(invalid(42)).toEqual(['email']);
    expect(invalid(`${'a'.repeat(250)}@b.cz`)).toEqual(['email']);
  });
});

/**
 * The apps check an e-mail when creating an account with the rule in
 * docs/mobile-spec.md ("Email pattern"); the API checks it with validator.js.
 * Everything the apps let through must pass here, or registering ends in a
 * generic validation error.
 */
describe('the apps’ e-mail rule (docs/mobile-spec.md)', () => {
  const spec = readFileSync(
    new URL('../../../../docs/mobile-spec.md', import.meta.url),
    'utf8',
  );
  const section = spec.slice(
    spec.indexOf('### Email pattern'),
    spec.indexOf('### Cover URL pattern'),
  );
  const pattern = new RegExp(/```\n(.+)\n```/.exec(section)?.[1] ?? '$^', 'u');
  const utf8Bytes = (text: string) => Buffer.byteLength(text, 'utf8');
  const appAccepts = (email: string) => {
    const at = email.lastIndexOf('@');
    return (
      charLength(email) <= 254 &&
      // Outside the BMP a code point takes two UTF-16 code units.
      charLength(email) === email.length &&
      utf8Bytes(email.slice(0, at)) <= 64 &&
      utf8Bytes(email.slice(at + 1)) <= 254 &&
      pattern.test(email)
    );
  };
  const apiAccepts = (email: string) =>
    validateSync(
      plainToInstance(RegisterDto, { email, password: 'super-secret' }),
    ).length === 0;
  /** The table's cells: each holds one or more addresses in backticks. */
  const examples = (column: 0 | 1) =>
    section
      .split('\n')
      .filter((line) => line.startsWith('| `') || line.startsWith('| |'))
      .flatMap((line) => line.split('|')[column + 1].match(/`[^`]+`/g) ?? [])
      .map((cell) => cell.slice(1, -1));

  it('is found in the spec', () => {
    expect(pattern.source).toMatch(/^\^\(\?!/);
    expect(examples(0).length).toBeGreaterThan(5);
    expect(examples(1).length).toBeGreaterThan(5);
  });

  it('accepts the valid examples, and so does the API', () => {
    for (const email of examples(0)) {
      expect([email, appAccepts(email)]).toEqual([email, true]);
      expect([email, apiAccepts(email)]).toEqual([email, true]);
    }
  });

  it('rejects the invalid examples', () => {
    for (const email of examples(1)) {
      expect([email, appAccepts(email)]).toEqual([email, false]);
    }
  });

  it('accepts nothing the API rejects', () => {
    const alphabet = [
      ...'abZ09.-_@+\'()! ~"'.split(''),
      '\u00a0',
      'é',
      'ř',
      'п',
      'ф',
      'ß',
      '\u0301',
      '\u3000',
      '\u2003',
      '\ufeff',
      '\u00a9',
      '\uff41',
      '\ufb01',
      '😀',
      '𝐀',
    ];
    const domains = [
      'example.com',
      'xn--p1ai.xn--p1ai',
      'пример.рф',
      'é.fr',
      'a.b.c.de',
    ];
    // A fixed seed keeps the test deterministic (mulberry32).
    let seed = 42;
    const random = () => {
      seed = (seed + 0x6d2b79f5) | 0;
      let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
    const pick = <T>(items: T[]) => items[Math.floor(random() * items.length)];
    let accepted = 0;
    for (let i = 0; i < 200_000; i++) {
      let email = '';
      const length = 1 + Math.floor(random() * 12);
      for (let j = 0; j < length; j++) email += pick(alphabet);
      if (random() < 0.8)
        email = `${email.replaceAll('@', '')}@${pick(domains)}`;
      if (random() < 0.3) email += pick(alphabet);
      // The apps check the trimmed address, the DTO trims before it validates.
      if (!appAccepts(email.trim())) continue;
      accepted++;
      expect([email, apiAccepts(email)]).toEqual([email, true]);
    }
    expect(accepted).toBeGreaterThan(1000);
  });
});
