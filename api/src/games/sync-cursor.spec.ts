import { CURSOR_REGEX, formatCursor, parseCursor } from './sync-cursor.js';

describe('sync cursor', () => {
  it('round-trips epoch and version', () => {
    const cursor = formatCursor('5f1d3c9a2b', 42);
    expect(cursor).toBe('5f1d3c9a2b.42');
    expect(CURSOR_REGEX.test(cursor)).toBe(true);
    expect(parseCursor(cursor)).toEqual({ epoch: '5f1d3c9a2b', version: 42 });
  });

  it('reads plain versions of older app versions without an epoch', () => {
    expect(parseCursor('42')).toEqual({ epoch: null, version: 42 });
  });

  it('starts at the beginning without a cursor', () => {
    expect(parseCursor(undefined)).toEqual({ epoch: null, version: 0 });
  });

  it.each(['', 'abc', '-1', '.5', 'x.', 'a.b', 'a.b.1', 'é.1', '1.2.3'])(
    'does not match %j',
    (cursor) => {
      expect(CURSOR_REGEX.test(cursor)).toBe(false);
    },
  );
});
