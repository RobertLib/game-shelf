import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  IsBoolean,
  IsEnum,
  IsIn,
  IsInt,
  IsISO8601,
  IsNumber,
  IsOptional,
  IsString,
  Max,
  Min,
} from 'class-validator';
import { MaxChars } from '../../common/char-length.decorator.js';
import { ToArray, ToBoolean } from '../../common/transforms.js';
import {
  CollectionStatus,
  Completeness,
  Condition,
  GameFormat,
  Platform,
  PlayStatus,
  Region,
} from '../../generated/prisma/enums.js';

export const GameSortField = {
  title: 'title',
  platform: 'platform',
  releaseYear: 'releaseYear',
  purchaseDate: 'purchaseDate',
  purchasePrice: 'purchasePrice',
  estimatedValue: 'estimatedValue',
  rating: 'rating',
  createdAt: 'createdAt',
  updatedAt: 'updatedAt',
} as const;
export type GameSortField = (typeof GameSortField)[keyof typeof GameSortField];

export const SortOrder = { asc: 'asc', desc: 'desc' } as const;
export type SortOrder = (typeof SortOrder)[keyof typeof SortOrder];

export const MAX_PAGE_SIZE = 100;
/** Keeps the offset `(page - 1) * pageSize` well within a 32-bit integer. */
export const MAX_PAGE = 1_000_000;

const multi = (description: string) => ({
  isArray: true,
  description: `${description} Repeat the parameter or separate values with commas; values are OR-ed.`,
});

export class ListGamesQueryDto {
  @ApiPropertyOptional({
    description:
      'Full-text search in title, edition, developer, publisher, genre, product code, barcode ' +
      'and notes. Every whitespace-separated word must match (case-insensitive).',
    example: 'zelda ocarina',
  })
  @IsOptional()
  @IsString()
  @MaxChars(200)
  q?: string;

  @ApiPropertyOptional({
    enum: Platform,
    enumName: 'Platform',
    ...multi('Platforms.'),
  })
  @ToArray()
  @IsOptional()
  @IsEnum(Platform, { each: true })
  platform?: Platform[];

  @ApiPropertyOptional({
    enum: CollectionStatus,
    enumName: 'CollectionStatus',
    ...multi('Collection statuses.'),
  })
  @ToArray()
  @IsOptional()
  @IsEnum(CollectionStatus, { each: true })
  status?: CollectionStatus[];

  @ApiPropertyOptional({
    enum: GameFormat,
    enumName: 'GameFormat',
    ...multi('Formats.'),
  })
  @ToArray()
  @IsOptional()
  @IsEnum(GameFormat, { each: true })
  format?: GameFormat[];

  @ApiPropertyOptional({
    enum: Region,
    enumName: 'Region',
    ...multi('Regions.'),
  })
  @ToArray()
  @IsOptional()
  @IsEnum(Region, { each: true })
  region?: Region[];

  @ApiPropertyOptional({
    enum: Completeness,
    enumName: 'Completeness',
    ...multi('Completeness levels.'),
  })
  @ToArray()
  @IsOptional()
  @IsEnum(Completeness, { each: true })
  completeness?: Completeness[];

  @ApiPropertyOptional({
    enum: Condition,
    enumName: 'Condition',
    ...multi('Conditions.'),
  })
  @ToArray()
  @IsOptional()
  @IsEnum(Condition, { each: true })
  condition?: Condition[];

  @ApiPropertyOptional({
    enum: PlayStatus,
    enumName: 'PlayStatus',
    ...multi('Play statuses.'),
  })
  @ToArray()
  @IsOptional()
  @IsEnum(PlayStatus, { each: true })
  playStatus?: PlayStatus[];

  @ApiPropertyOptional({
    type: String,
    ...multi('Exact genres (case-insensitive).'),
  })
  @ToArray()
  @IsOptional()
  @IsString({ each: true })
  @MaxChars(100, { each: true })
  genre?: string[];

  @ApiPropertyOptional({
    description: 'Publisher contains (case-insensitive).',
  })
  @IsOptional()
  @IsString()
  @MaxChars(100)
  publisher?: string;

  @ApiPropertyOptional({
    description: 'Developer contains (case-insensitive).',
  })
  @IsOptional()
  @IsString()
  @MaxChars(100)
  developer?: string;

  @ApiPropertyOptional({
    description: 'Storage location contains (case-insensitive).',
  })
  @IsOptional()
  @IsString()
  @MaxChars(100)
  storageLocation?: string;

  @ApiPropertyOptional({
    description: 'Only favourites (true) or only non-favourites (false).',
  })
  @ToBoolean()
  @IsOptional()
  @IsBoolean()
  favorite?: boolean;

  @ApiPropertyOptional({
    description: 'Only games with (true) or without (false) a cover image.',
  })
  @ToBoolean()
  @IsOptional()
  @IsBoolean()
  hasCover?: boolean;

  @ApiPropertyOptional({ type: 'integer', minimum: 1950, maximum: 2100 })
  @Type(() => Number)
  @IsOptional()
  @IsInt()
  @Min(1950)
  @Max(2100)
  releaseYearFrom?: number;

  @ApiPropertyOptional({ type: 'integer', minimum: 1950, maximum: 2100 })
  @Type(() => Number)
  @IsOptional()
  @IsInt()
  @Min(1950)
  @Max(2100)
  releaseYearTo?: number;

  @ApiPropertyOptional({ format: 'date', example: '2024-01-01' })
  @IsOptional()
  @IsISO8601({ strict: true })
  purchaseDateFrom?: string;

  @ApiPropertyOptional({ format: 'date', example: '2024-12-31' })
  @IsOptional()
  @IsISO8601({ strict: true })
  purchaseDateTo?: string;

  @ApiPropertyOptional({ type: Number, minimum: 0 })
  @Type(() => Number)
  @IsOptional()
  @IsNumber()
  @Min(0)
  purchasePriceMin?: number;

  @ApiPropertyOptional({ type: Number, minimum: 0 })
  @Type(() => Number)
  @IsOptional()
  @IsNumber()
  @Min(0)
  purchasePriceMax?: number;

  @ApiPropertyOptional({ type: Number, minimum: 0 })
  @Type(() => Number)
  @IsOptional()
  @IsNumber()
  @Min(0)
  estimatedValueMin?: number;

  @ApiPropertyOptional({ type: Number, minimum: 0 })
  @Type(() => Number)
  @IsOptional()
  @IsNumber()
  @Min(0)
  estimatedValueMax?: number;

  @ApiPropertyOptional({ type: 'integer', minimum: 1, maximum: 10 })
  @Type(() => Number)
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(10)
  ratingMin?: number;

  @ApiPropertyOptional({ type: 'integer', minimum: 1, maximum: 10 })
  @Type(() => Number)
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(10)
  ratingMax?: number;

  @ApiPropertyOptional({
    type: String,
    enum: GameSortField,
    enumName: 'GameSortField',
    default: GameSortField.title,
    description:
      'Games without a value in the sorted field always come last. `platform` sorts by ' +
      'manufacturer and generation (the order of the Platform enum), not alphabetically.',
  })
  @IsOptional()
  @IsIn(Object.values(GameSortField))
  sort: GameSortField = GameSortField.title;

  @ApiPropertyOptional({
    type: String,
    enum: SortOrder,
    enumName: 'SortOrder',
    default: SortOrder.asc,
  })
  @IsOptional()
  @IsIn(Object.values(SortOrder))
  order: SortOrder = SortOrder.asc;

  @ApiPropertyOptional({
    type: 'integer',
    minimum: 1,
    maximum: MAX_PAGE,
    default: 1,
  })
  @Type(() => Number)
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(MAX_PAGE)
  page: number = 1;

  @ApiPropertyOptional({
    type: 'integer',
    minimum: 1,
    maximum: MAX_PAGE_SIZE,
    default: 25,
  })
  @Type(() => Number)
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(MAX_PAGE_SIZE)
  pageSize: number = 25;
}
