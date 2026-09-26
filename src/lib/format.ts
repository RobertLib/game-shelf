export function formatMoney(amount: number | null | undefined, currency: string) {
  if (amount == null) return undefined;
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency}`;
  }
}

/** "2024-03-18" → "Mar 18, 2024" */
export function formatDate(isoDate: string | null | undefined) {
  if (!isoDate) return undefined;
  const date = parseIsoDate(isoDate);
  if (!date) return isoDate;
  return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
}

/** Parses "YYYY-MM-DD" as a local calendar date (no timezone shift). */
export function parseIsoDate(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return null;
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return Number.isNaN(date.getTime()) ? null : date;
}

export function toIsoDate(date: Date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function pluralize(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

/** Accepts "12.5", "12,50" or " 12 " and returns a number, or null for blank input. */
export function parseDecimal(value: string) {
  const normalized = value.trim().replace(/\s/g, '').replace(',', '.');
  if (normalized === '') return null;
  const number = Number(normalized);
  return Number.isFinite(number) ? number : Number.NaN;
}
