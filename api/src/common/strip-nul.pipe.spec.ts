import { StripNulPipe, stripNul } from './strip-nul.pipe.js';

describe('stripNul', () => {
  it('removes U+0000 from strings, also nested ones', () => {
    expect(
      stripNul({
        title: 'Ze\u0000lda',
        tags: ['a\u0000', 1],
        deep: { q: '\u0000' },
      }),
    ).toEqual({ title: 'Zelda', tags: ['a', 1], deep: { q: '' } });
  });

  it('leaves everything else alone', () => {
    expect(stripNul({ n: 1, b: false, none: null })).toEqual({
      n: 1,
      b: false,
      none: null,
    });
    expect(stripNul('a\tb\u0001')).toBe('a\tb\u0001');
  });
});

describe('StripNulPipe', () => {
  it('cleans the body, query and params but not custom parameters', () => {
    const pipe = new StripNulPipe();
    expect(pipe.transform('a\u0000', { type: 'body' })).toBe('a');
    expect(pipe.transform('a\u0000', { type: 'query' })).toBe('a');
    const user = { id: 'x\u0000' };
    expect(pipe.transform(user, { type: 'custom' })).toBe(user);
  });
});
