/**
 * Cover image URLs the API accepts – the same pattern the apps validate with
 * (docs/mobile-spec.md, "Cover URL pattern"): http(s), a host name of ASCII
 * letters, digits, dots and hyphens, an optional port, then printable ASCII.
 * The character classes are written out because `\d` or `\S` mean different
 * things in the apps' regex engines.
 */
export const COVER_URL_PATTERN =
  '^[Hh][Tt][Tt][Pp][Ss]?://[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?(:[0-9]{1,5})?([/?#][!-~]*)?$';
export const COVER_URL_REGEX = new RegExp(COVER_URL_PATTERN);
export const COVER_URL_MAX_LENGTH = 2048;

/** Whether a game can be saved with this cover URL. */
export function isCoverUrl(url: string): boolean {
  return url.length <= COVER_URL_MAX_LENGTH && COVER_URL_REGEX.test(url);
}
