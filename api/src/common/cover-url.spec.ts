import { COVER_URL_REGEX, isCoverUrl } from './cover-url.js';

/** The table in docs/mobile-spec.md ("Cover URL pattern"), shared by the apps. */
const VALID = [
  'https://example.com/a.png',
  'HTTPS://Example.COM/A.png',
  'http://localhost/x.png',
  'https://nas/cover.jpg',
  'https://example.com:8443/a?b=c#d',
  'https://example.com?x=1',
  'https://img.example.co.uk/a_b/%C3%A9.jpg',
];
const INVALID = [
  'ftp://example.com/a.png',
  'example.com/a.png',
  'https://',
  'https://-example.com/a.png',
  'https://example.com./a.png',
  'https://exa mple.com/a.png',
  'https://example.com/a b.png',
  'https://example.com/é.png',
  'https://user:pw@example.com/a.png',
  'https://my_host/a.png',
  'https://[::1]/a.png',
  'https://example.com:123456/a.png',
];

describe('cover URL pattern', () => {
  it.each(VALID)('accepts %s', (url) => {
    expect(COVER_URL_REGEX.test(url)).toBe(true);
    expect(isCoverUrl(url)).toBe(true);
  });

  it.each(INVALID)('rejects %s', (url) => {
    expect(COVER_URL_REGEX.test(url)).toBe(false);
    expect(isCoverUrl(url)).toBe(false);
  });

  it('rejects a trailing line break and URLs over 2048 characters', () => {
    expect(isCoverUrl('https://example.com/a.png\n')).toBe(false);
    const url = 'https://example.com/';
    expect(isCoverUrl(url + 'a'.repeat(2048 - url.length))).toBe(true);
    expect(isCoverUrl(url + 'a'.repeat(2049 - url.length))).toBe(false);
  });
});
