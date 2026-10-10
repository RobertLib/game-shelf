import { plainToInstance } from 'class-transformer';
import { validateSync } from 'class-validator';
import { MAX_PRICE, SaveGameDto, UpdateGameDto } from './save-game.dto.js';

const parse = (body: Record<string, unknown>) => {
  const dto = plainToInstance(SaveGameDto, {
    title: 'Doom',
    platform: 'PC',
    ...body,
  });
  return { dto, invalid: validateSync(dto).map((error) => error.property) };
};

describe('SaveGameDto', () => {
  it.each([
    ['DEM', 'DEM'],
    ['skk', 'SKK'],
    [' eur ', 'EUR'],
    ['XyZ', 'XYZ'],
  ])('accepts currency %j as %s', (currency, stored) => {
    const { dto, invalid } = parse({ currency });
    expect(invalid).toEqual([]);
    expect(dto.currency).toBe(stored);
  });

  it.each(['EU', 'EURO', 'E1R', 'ÄBC', 'ıab', 'ſek', '', null])(
    'rejects currency %j',
    (currency) => {
      expect(parse({ currency }).invalid).toEqual(['currency']);
    },
  );

  it('validates the cover URL with the pattern the apps use', () => {
    expect(
      parse({ coverImageUrl: 'HTTPS://Example.COM/A.png' }).invalid,
    ).toEqual([]);
    expect(parse({ coverImageUrl: '  ' }).dto.coverImageUrl).toBeNull();
    for (const coverImageUrl of [
      'https://example.com/é.png',
      'https://user:pw@example.com/a.png',
      'ftp://example.com/a.png',
      `https://example.com/${'a'.repeat(2030)}`,
    ]) {
      expect(parse({ coverImageUrl }).invalid).toEqual(['coverImageUrl']);
    }
  });

  it('counts text lengths in code points', () => {
    expect(parse({ title: '😀'.repeat(200) }).invalid).toEqual([]);
    expect(parse({ title: '😀'.repeat(201) }).invalid).toEqual(['title']);
    // Two code points each, although validator.js would count one.
    expect(parse({ title: '❤️'.repeat(100) }).invalid).toEqual([]);
    expect(parse({ title: '❤️'.repeat(101) }).invalid).toEqual(['title']);
    expect(parse({ productCode: 'e\u0301'.repeat(25) }).invalid).toEqual([]);
    expect(parse({ productCode: 'e\u0301'.repeat(26) }).invalid).toEqual([
      'productCode',
    ]);
    expect(parse({ notes: '🇨🇿'.repeat(2500) }).invalid).toEqual([]);
    expect(parse({ notes: '🇨🇿'.repeat(2500) + 'x' }).invalid).toEqual([
      'notes',
    ]);
    for (const field of [
      'edition',
      'genre',
      'developer',
      'publisher',
      'purchasePlace',
      'storageLocation',
    ]) {
      expect(parse({ [field]: '😀'.repeat(100) }).invalid).toEqual([]);
      expect(parse({ [field]: '😀'.repeat(101) }).invalid).toEqual([field]);
    }
  });

  it('accepts prices up to MAX_PRICE with at most 2 decimal places', () => {
    expect(
      parse({ purchasePrice: MAX_PRICE, estimatedValue: 0.5 }).invalid,
    ).toEqual([]);
    expect(
      parse({ purchasePrice: 10_000_000_000, estimatedValue: 1.005 }).invalid,
    ).toEqual(['purchasePrice', 'estimatedValue']);
  });
});

describe('UpdateGameDto', () => {
  it('applies the same rules to the fields present', () => {
    const errors = validateSync(
      plainToInstance(UpdateGameDto, { currency: 'DM', title: '😀' }),
    );
    expect(errors.map((error) => error.property)).toEqual(['currency']);
  });
});
