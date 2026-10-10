/**
 * Trimmed text cut to the API's length limit, counted in code points like the
 * API counts it (so a surrogate pair is never split); `null` when there is none.
 */
export function limit(
  text: string | null | undefined,
  maxLength: number,
): string | null {
  const trimmed = text?.trim();
  if (!trimmed) return null;
  // oxlint-disable-next-line typescript/no-misused-spread -- code points are the unit
  return [...trimmed].slice(0, maxLength).join('').trim();
}
