import { ApiProperty, ApiSchema } from '@nestjs/swagger';
import type { Game } from '../../generated/prisma/client.js';
import {
  CollectionStatus,
  Completeness,
  Condition,
  GameFormat,
  Platform,
  PlayStatus,
  Region,
} from '../../generated/prisma/enums.js';

const nullableString = { type: String, nullable: true } as const;
const nullableNumber = { type: Number, nullable: true } as const;
const nullableInteger = { type: 'integer', nullable: true } as const;

@ApiSchema({ name: 'Game' })
export class GameDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty()
  title: string;

  @ApiProperty({ enum: Platform, enumName: 'Platform' })
  platform: Platform;

  @ApiProperty({ enum: CollectionStatus, enumName: 'CollectionStatus' })
  status: CollectionStatus;

  @ApiProperty({ enum: GameFormat, enumName: 'GameFormat' })
  format: GameFormat;

  @ApiProperty({ enum: Region, enumName: 'Region', nullable: true })
  region: Region | null;

  @ApiProperty(nullableString)
  edition: string | null;

  @ApiProperty({ enum: Completeness, enumName: 'Completeness', nullable: true })
  completeness: Completeness | null;

  @ApiProperty({ enum: Condition, enumName: 'Condition', nullable: true })
  condition: Condition | null;

  @ApiProperty({ enum: PlayStatus, enumName: 'PlayStatus', nullable: true })
  playStatus: PlayStatus | null;

  @ApiProperty(nullableString)
  genre: string | null;

  @ApiProperty(nullableString)
  developer: string | null;

  @ApiProperty(nullableString)
  publisher: string | null;

  @ApiProperty(nullableInteger)
  releaseYear: number | null;

  @ApiProperty(nullableString)
  barcode: string | null;

  @ApiProperty(nullableString)
  productCode: string | null;

  @ApiProperty({ type: 'integer' })
  quantity: number;

  @ApiProperty(nullableNumber)
  purchasePrice: number | null;

  @ApiProperty({
    type: String,
    format: 'date',
    nullable: true,
    example: '2024-05-17',
  })
  purchaseDate: string | null;

  @ApiProperty(nullableString)
  purchasePlace: string | null;

  @ApiProperty(nullableNumber)
  estimatedValue: number | null;

  @ApiProperty({ example: 'CZK' })
  currency: string;

  @ApiProperty(nullableString)
  storageLocation: string | null;

  @ApiProperty(nullableInteger)
  rating: number | null;

  @ApiProperty()
  favorite: boolean;

  @ApiProperty({ type: String, format: 'uri', nullable: true })
  coverImageUrl: string | null;

  @ApiProperty(nullableString)
  notes: string | null;

  @ApiProperty({ format: 'date-time' })
  createdAt: Date;

  @ApiProperty({ format: 'date-time' })
  updatedAt: Date;

  static from(game: Game): GameDto {
    const {
      userId: _userId,
      version: _version,
      deletedAt: _deletedAt,
      purchasePrice,
      estimatedValue,
      purchaseDate,
      ...rest
    } = game;
    return {
      ...rest,
      purchasePrice: purchasePrice?.toNumber() ?? null,
      estimatedValue: estimatedValue?.toNumber() ?? null,
      purchaseDate: purchaseDate?.toISOString().slice(0, 10) ?? null,
    };
  }
}

@ApiSchema({ name: 'GamePage' })
export class GamePageDto {
  @ApiProperty({ type: [GameDto] })
  items: GameDto[];

  @ApiProperty({ type: 'integer', example: 1 })
  page: number;

  @ApiProperty({ type: 'integer', example: 25 })
  pageSize: number;

  @ApiProperty({
    type: 'integer',
    description: 'Number of games matching the filters.',
    example: 132,
  })
  totalItems: number;

  @ApiProperty({ type: 'integer', example: 6 })
  totalPages: number;
}
