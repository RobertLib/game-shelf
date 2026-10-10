import {
  buildMessage,
  ValidateBy,
  type ValidationOptions,
} from 'class-validator';

/**
 * Length in Unicode code points, as the apps count it (Kotlin `codePointCount`,
 * Swift `unicodeScalars.count`): "😀" is 1, "🇨🇿" is 2, a decomposed "é" is 2.
 * validator.js `MaxLength` / `MinLength` count differently (they skip
 * variation selectors, for example), so they are not used for text.
 */
export const charLength = (value: string): number =>
  // oxlint-disable-next-line typescript/no-misused-spread -- code points are the unit
  [...value].length;

/** Like `@MaxLength()`, counting code points (see {@link charLength}). */
export const MaxChars = (max: number, options?: ValidationOptions) =>
  ValidateBy(
    {
      name: 'maxChars',
      constraints: [max],
      validator: {
        validate: (value: unknown) =>
          typeof value === 'string' && charLength(value) <= max,
        defaultMessage: buildMessage(
          (each) =>
            `${each}$property must be shorter than or equal to $constraint1 characters`,
          options,
        ),
      },
    },
    options,
  );

/** Like `@MinLength()`, counting code points (see {@link charLength}). */
export const MinChars = (min: number, options?: ValidationOptions) =>
  ValidateBy(
    {
      name: 'minChars',
      constraints: [min],
      validator: {
        validate: (value: unknown) =>
          typeof value === 'string' && charLength(value) >= min,
        defaultMessage: buildMessage(
          (each) =>
            `${each}$property must be longer than or equal to $constraint1 characters`,
          options,
        ),
      },
    },
    options,
  );
