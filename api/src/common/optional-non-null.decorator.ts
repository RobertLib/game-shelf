import { ValidateIf } from 'class-validator';

/**
 * Like `@IsOptional()`, but only skips validation when the property is absent.
 * An explicit `null` still has to pass the remaining validators, so it is
 * rejected for columns that cannot be null.
 */
export const IsOptionalNonNull = () =>
  ValidateIf((_object: object, value: unknown) => value !== undefined);
