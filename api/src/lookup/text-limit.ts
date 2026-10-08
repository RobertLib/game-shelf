/** Trimmed text cut to the API's length limit; `null` when there is none. */
export function limit(
  text: string | null | undefined,
  maxLength: number,
): string | null {
  const trimmed = text?.trim();
  return trimmed ? trimmed.slice(0, maxLength).trim() : null;
}
