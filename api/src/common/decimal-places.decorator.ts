import {
  buildMessage,
  ValidateBy,
  type ValidationOptions,
} from 'class-validator';

/**
 * Decimal places of a number as JavaScript writes it, exponent notation
 * included: `1299.9` has 1, `1e-7` has 7, `1.5e-7` has 8.
 */
export function decimalPlaces(value: number): number {
  const [mantissa, exponent = '0'] = String(value).toLowerCase().split('e');
  const decimals = mantissa.split('.')[1]?.length ?? 0;
  return Math.max(0, decimals - Number(exponent));
}

/**
 * A finite number with at most `max` decimal places. Replaces `@IsNumber()`'s
 * `maxDecimalPlaces`, which throws on numbers JavaScript writes in exponent
 * notation (`1e-7`) and so turned them into a 500 instead of a 400.
 */
export const MaxDecimalPlaces = (max: number, options?: ValidationOptions) =>
  ValidateBy(
    {
      name: 'maxDecimalPlaces',
      constraints: [max],
      validator: {
        validate: (value: unknown) =>
          typeof value === 'number' &&
          Number.isFinite(value) &&
          decimalPlaces(value) <= max,
        defaultMessage: buildMessage(
          (each) =>
            `${each}$property must be a number with at most $constraint1 decimal places`,
          options,
        ),
      },
    },
    options,
  );
