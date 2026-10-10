import { limit } from './text-limit.js';

describe('limit', () => {
  it('trims and turns blank text into null', () => {
    expect(limit('  Doom  ', 10)).toBe('Doom');
    expect(limit('   ', 10)).toBeNull();
    expect(limit(null, 10)).toBeNull();
  });

  it('cuts to the limit in code points without splitting a character', () => {
    expect(limit('😀😀😀', 2)).toBe('😀😀');
    expect(limit('ab 😀', 3)).toBe('ab');
  });
});
