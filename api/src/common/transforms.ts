import { Transform } from 'class-transformer';

/**
 * Accepts both repeated query params (`?platform=PS2&platform=PS5`) and a
 * comma-separated list (`?platform=PS2,PS5`).
 */
export const ToArray = () =>
  Transform(({ value }: { value: unknown }) => {
    if (value === undefined || value === null || value === '') return undefined;
    const values = Array.isArray(value) ? value : [value];
    return values
      .flatMap((v) => String(v).split(','))
      .map((v) => v.trim())
      .filter((v) => v.length > 0);
  });

/** Query strings are always text; turn "true"/"false" into real booleans. */
export const ToBoolean = () =>
  Transform(({ value }: { value: unknown }) => {
    if (value === 'true' || value === true) return true;
    if (value === 'false' || value === false) return false;
    return value;
  });

/** Trims strings and turns blank ones into null so they clear the field. */
export const TrimToNull = () =>
  Transform(({ value }: { value: unknown }) => {
    if (typeof value !== 'string') return value;
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  });
