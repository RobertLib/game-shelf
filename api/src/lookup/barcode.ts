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

/**
 * False for a 12-, 13- or 14-digit code (UPC-A, EAN-13, GTIN-14) whose GTIN
 * mod-10 check digit is wrong – a misread or mistyped code. 8-digit codes are
 * not checked: they can be EAN-8 or UPC-E, whose check digit is computed from
 * the expanded code.
 */
export function hasValidCheckDigit(code: string): boolean {
  const digits = code.trim().split('').map(Number);
  if (digits.length < 12 || digits.length > 14) return true;
  const check = digits.pop();
  // Weights 3, 1, 3, … from the digit next to the check digit leftwards.
  const sum = digits
    .reverse()
    .reduce((total, digit, i) => total + digit * (i % 2 === 0 ? 3 : 1), 0);
  return (10 - (sum % 10)) % 10 === check;
}
