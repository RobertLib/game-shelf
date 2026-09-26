import { z } from 'zod';

import {
  COMPLETENESS,
  CONDITIONS,
  FORMATS,
  MAX_GENRES_PER_GAME,
  MAX_IMAGES_PER_GAME,
  PLAY_STATUSES,
  REGIONS,
  type Option,
} from '@/constants/game-options';
import { parseDecimal } from '@/lib/format';

import type { FormImage, GameDetail, GameInput } from './types';

function enumOf<T extends string>(options: Option<T>[]) {
  return z.enum(options.map((o) => o.value) as [T, ...T[]]);
}

const maxYear = new Date().getFullYear() + 2;

const optionalText = (max: number, label: string) =>
  z.string().max(max, `${label} can be at most ${max} characters`);

const money = z.string().refine((value) => {
  const number = parseDecimal(value);
  return number === null || (number >= 0 && number < 100_000_000);
}, 'Enter an amount like 24.99');

export const gameFormSchema = z.object({
  title: z.string().trim().min(1, 'Enter the game title').max(200, 'The title is too long'),
  platformId: z.string().min(1, 'Choose a platform'),
  edition: optionalText(100, 'Edition'),
  format: enumOf(FORMATS),
  region: enumOf(REGIONS).nullable(),
  condition: enumOf(CONDITIONS).nullable(),
  completeness: enumOf(COMPLETENESS).nullable(),
  genres: z
    .array(z.string())
    .max(MAX_GENRES_PER_GAME, `Pick at most ${MAX_GENRES_PER_GAME} genres`),
  developer: optionalText(100, 'Developer'),
  publisher: optionalText(100, 'Publisher'),
  releaseYear: z.string().refine((value) => {
    const trimmed = value.trim();
    if (trimmed === '') return true;
    const year = Number(trimmed);
    return /^\d{4}$/.test(trimmed) && year >= 1950 && year <= maxYear;
  }, `Enter a year between 1950 and ${maxYear}`),
  serialNumber: optionalText(100, 'Serial number'),
  purchaseDate: z.string().nullable(),
  purchasePrice: money,
  currentValue: money,
  currency: z.string().regex(/^[A-Z]{3}$/),
  purchasedFrom: optionalText(100, 'Store'),
  playStatus: enumOf(PLAY_STATUSES),
  rating: z.number().int().min(1).max(5).nullable(),
  isFavorite: z.boolean(),
  notes: optionalText(5000, 'Notes'),
  images: z
    .array(z.custom<FormImage>())
    .max(MAX_IMAGES_PER_GAME, `You can add up to ${MAX_IMAGES_PER_GAME} photos`),
});

export type GameFormValues = z.infer<typeof gameFormSchema>;

export function emptyGameForm(defaults?: Partial<GameFormValues>): GameFormValues {
  return {
    title: '',
    platformId: '',
    edition: '',
    format: 'physical',
    region: null,
    condition: null,
    completeness: null,
    genres: [],
    developer: '',
    publisher: '',
    releaseYear: '',
    serialNumber: '',
    purchaseDate: null,
    purchasePrice: '',
    currentValue: '',
    currency: 'USD',
    purchasedFrom: '',
    playStatus: 'not_started',
    rating: null,
    isFavorite: false,
    notes: '',
    images: [],
    ...defaults,
  };
}

const moneyToText = (value: number | null) => (value == null ? '' : String(value));

export function gameToFormValues(game: GameDetail): GameFormValues {
  return {
    title: game.title,
    platformId: game.platform_id,
    edition: game.edition ?? '',
    format: game.format,
    region: game.region,
    condition: game.condition,
    completeness: game.completeness,
    genres: game.genres,
    developer: game.developer ?? '',
    publisher: game.publisher ?? '',
    releaseYear: game.release_year ? String(game.release_year) : '',
    serialNumber: game.serial_number ?? '',
    purchaseDate: game.purchase_date,
    purchasePrice: moneyToText(game.purchase_price),
    currentValue: moneyToText(game.current_value),
    currency: game.currency,
    purchasedFrom: game.purchased_from ?? '',
    playStatus: game.play_status,
    rating: game.rating,
    isFavorite: game.is_favorite,
    notes: game.notes ?? '',
    images: game.images.map((img) => ({
      key: img.id,
      kind: 'existing',
      id: img.id,
      storagePath: img.storage_path,
      thumbPath: img.thumb_path,
      previewUrl: img.thumbUrl,
    })),
  };
}

const blankToNull = (value: string) => (value.trim() === '' ? null : value.trim());

export function formValuesToGameInput(values: GameFormValues): GameInput {
  const isDigital = values.format === 'digital';
  return {
    title: values.title.trim(),
    platform_id: values.platformId,
    edition: blankToNull(values.edition),
    format: values.format,
    region: values.region,
    // Box and manual don't apply to digital copies.
    condition: isDigital ? null : values.condition,
    completeness: isDigital ? null : values.completeness,
    genres: values.genres,
    developer: blankToNull(values.developer),
    publisher: blankToNull(values.publisher),
    release_year: values.releaseYear.trim() ? Number(values.releaseYear.trim()) : null,
    serial_number: blankToNull(values.serialNumber),
    purchase_date: values.purchaseDate,
    purchase_price: parseDecimal(values.purchasePrice),
    current_value: parseDecimal(values.currentValue),
    currency: values.currency,
    purchased_from: blankToNull(values.purchasedFrom),
    play_status: values.playStatus,
    rating: values.rating,
    is_favorite: values.isFavorite,
    notes: blankToNull(values.notes),
  };
}
