import type { Enums } from '@/lib/database.types';

export type GameRegion = Enums<'game_region'>;
export type GameFormat = Enums<'game_format'>;
export type GameCondition = Enums<'game_condition'>;
export type GameCompleteness = Enums<'game_completeness'>;
export type PlayStatus = Enums<'play_status'>;

export type Option<T extends string> = {
  value: T;
  label: string;
  /** Compact label for chips and badges. */
  short?: string;
  description?: string;
};

export const REGIONS: Option<GameRegion>[] = [
  { value: 'pal', label: 'PAL', description: 'Europe, Australia' },
  { value: 'ntsc_u', label: 'NTSC-U', description: 'North America' },
  { value: 'ntsc_j', label: 'NTSC-J', description: 'Japan' },
  { value: 'ntsc_k', label: 'NTSC-K', description: 'Korea' },
  { value: 'ntsc_c', label: 'NTSC-C', description: 'China' },
  { value: 'region_free', label: 'Region free' },
  { value: 'other', label: 'Other' },
];

export const FORMATS: Option<GameFormat>[] = [
  { value: 'physical', label: 'Physical' },
  { value: 'digital', label: 'Digital' },
];

export const CONDITIONS: Option<GameCondition>[] = [
  { value: 'sealed', label: 'Sealed', description: 'Factory sealed, never opened' },
  { value: 'mint', label: 'Mint', description: 'Like new, no visible wear' },
  { value: 'very_good', label: 'Very good', description: 'Light signs of use' },
  { value: 'good', label: 'Good', description: 'Normal wear, fully working' },
  { value: 'fair', label: 'Fair', description: 'Heavy wear or minor damage' },
  { value: 'poor', label: 'Poor', description: 'Significant damage' },
];

export const COMPLETENESS: Option<GameCompleteness>[] = [
  {
    value: 'complete',
    label: 'Complete in box',
    short: 'CIB',
    description: 'Game, box and manual',
  },
  { value: 'game_box', label: 'Game + box', short: 'Game + box', description: 'No manual' },
  { value: 'loose', label: 'Loose', short: 'Loose', description: 'Game only' },
  {
    value: 'box_only',
    label: 'Box only',
    short: 'Box only',
    description: 'Box and/or manual, no game',
  },
];

export const PLAY_STATUSES: Option<PlayStatus>[] = [
  { value: 'not_started', label: 'Not started' },
  { value: 'playing', label: 'Playing' },
  { value: 'completed', label: 'Completed' },
  { value: 'abandoned', label: 'Abandoned' },
];

export const GENRES = [
  'Action',
  'Action-Adventure',
  'Adventure',
  "Beat 'em up",
  'Compilation',
  'Educational',
  'Fighting',
  'Horror',
  'Metroidvania',
  'Music & Rhythm',
  'Party',
  'Platformer',
  'Puzzle',
  'Racing',
  'Roguelike',
  'RPG',
  'Sandbox',
  'Shooter',
  'Simulation',
  'Sports',
  'Stealth',
  'Strategy',
  'Survival',
  'Visual Novel',
  'Other',
] as const;

export const CURRENCIES = ['USD', 'EUR', 'GBP', 'CZK', 'PLN', 'CHF', 'SEK', 'JPY', 'CAD', 'AUD'];

export const MAX_GENRES_PER_GAME = 10;
export const MAX_IMAGES_PER_GAME = 12;

function labelLookup<T extends string>(options: Option<T>[]) {
  const map = new Map(options.map((o) => [o.value, o]));
  return (value: T | null | undefined, variant: 'label' | 'short' = 'label') => {
    if (!value) return undefined;
    const option = map.get(value);
    if (!option) return value;
    return variant === 'short' ? (option.short ?? option.label) : option.label;
  };
}

export const regionLabel = labelLookup(REGIONS);
export const formatLabel = labelLookup(FORMATS);
export const conditionLabel = labelLookup(CONDITIONS);
export const completenessLabel = labelLookup(COMPLETENESS);
export const playStatusLabel = labelLookup(PLAY_STATUSES);
