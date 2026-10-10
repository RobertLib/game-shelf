import { plainToInstance } from 'class-transformer';
import { validateSync } from 'class-validator';
import { charLength, MaxChars, MinChars } from './char-length.decorator.js';

const EMOJI = '😀';
const FLAG = '🇨🇿';
const DECOMPOSED_E = 'e\u0301';

class Text {
  @MinChars(2)
  @MaxChars(3)
  value: string;

  @MaxChars(1, { each: true })
  list: string[] = [];
}

const errors = (value: string, list: string[] = []) =>
  validateSync(plainToInstance(Text, { value, list })).flatMap((error) =>
    Object.values(error.constraints ?? {}),
  );

describe('charLength', () => {
  it('counts Unicode code points like the apps', () => {
    expect(charLength(EMOJI)).toBe(1);
    expect(charLength(FLAG)).toBe(2);
    expect(charLength(DECOMPOSED_E)).toBe(2);
    expect(charLength('é')).toBe(1);
    expect(charLength('❤️')).toBe(2);
  });
});

describe('MaxChars / MinChars', () => {
  it('limit the length in code points', () => {
    expect(errors(EMOJI.repeat(3))).toEqual([]);
    expect(errors(FLAG)).toEqual([]);
    expect(errors(DECOMPOSED_E)).toEqual([]);
    expect(errors(FLAG + EMOJI)).toEqual([]);
    expect(errors(FLAG + FLAG)).toEqual([
      'value must be shorter than or equal to 3 characters',
    ]);
    expect(errors(DECOMPOSED_E + DECOMPOSED_E)).toEqual([
      'value must be shorter than or equal to 3 characters',
    ]);
    expect(errors(EMOJI)).toEqual([
      'value must be longer than or equal to 2 characters',
    ]);
  });

  it('count a variation selector, unlike validator.js', () => {
    // validator.js MaxLength(3) would accept three hearts as 3 characters.
    expect(errors('❤️❤️❤️')).toEqual([
      'value must be shorter than or equal to 3 characters',
    ]);
  });

  it('check every item with `each`', () => {
    expect(errors('ab', [EMOJI, 'x'])).toEqual([]);
    expect(errors('ab', [EMOJI, DECOMPOSED_E])).toEqual([
      'each value in list must be shorter than or equal to 1 characters',
    ]);
  });

  it('reject values that are not text', () => {
    expect(
      validateSync(plainToInstance(Text, { value: 42 })).map((e) => e.property),
    ).toEqual(['value']);
  });
});
