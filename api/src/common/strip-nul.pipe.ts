import {
  type ArgumentMetadata,
  Injectable,
  type PipeTransform,
} from '@nestjs/common';

/** Removes U+0000 from every string in `value`, also in nested objects and arrays. */
export function stripNul(value: unknown): unknown {
  if (typeof value === 'string') return value.replaceAll('\u0000', '');
  if (Array.isArray(value)) return value.map(stripNul);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, stripNul(item)]),
    );
  }
  return value;
}

/**
 * Removes U+0000 from the body, query and route params before validation.
 * PostgreSQL cannot store it in text, so a NUL pasted into a title or typed
 * into a search would otherwise end in a 500 (and a 5xx keeps an app's sync
 * retrying forever). The apps remove it too (docs/mobile-spec.md#validation).
 */
@Injectable()
export class StripNulPipe implements PipeTransform {
  transform(value: unknown, { type }: ArgumentMetadata): unknown {
    return type === 'custom' ? value : stripNul(value);
  }
}
