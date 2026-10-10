import {
  buildMessage,
  ValidateBy,
  type ValidationOptions,
} from 'class-validator';

/**
 * A calendar date as the API writes it. Checked together with
 * `@IsISO8601({ strict: true })`, which alone also accepts week dates
 * (`2024-W01`), ordinal dates (`2024-100`), the basic format (`20240101`)
 * and date-times – `new Date()` reads the first three as Invalid Date (a 500
 * from the database) and a date-time in the server's time zone.
 */
export const LOCAL_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

/**
 * A `YYYY-MM-DD` date from `min` to `max`, both inclusive and written the
 * same way (dates in this format compare correctly as strings).
 */
export const LocalDateRange = (
  min: string,
  max: string,
  options?: ValidationOptions,
) =>
  ValidateBy(
    {
      name: 'localDateRange',
      constraints: [min, max],
      validator: {
        validate: (value: unknown) =>
          typeof value === 'string' && value >= min && value <= max,
        defaultMessage: buildMessage(
          (each) =>
            `${each}$property must be between $constraint1 and $constraint2`,
          options,
        ),
      },
    },
    options,
  );
