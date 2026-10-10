import { plainToInstance } from 'class-transformer';
import { validateSync } from 'class-validator';
import { RefreshTokenDto, RegisterDto } from './credentials.dto.js';

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
