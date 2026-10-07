/**
 * One product can be scanned as UPC-A (12 digits), as EAN-13 with a leading
 * zero (iOS reports UPC-A that way) or as a zero-padded GTIN-14. Dropping the
 * padding zeros makes all forms of one code equal.
 */
export function normalizeBarcode(code: string): string {
  let normalized = code.trim();
  while (normalized.length > 12 && normalized.startsWith('0')) {
    normalized = normalized.slice(1);
  }
  return normalized;
}
