import {
  ApiProperty,
  ApiPropertyOptional,
  ApiSchema,
  PartialType,
} from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import {
  IsBoolean,
  IsEnum,
  IsInt,
  IsISO8601,
  IsNotEmpty,
  IsNumber,
  IsOptional,
  IsString,
  IsUUID,
  Matches,
  Max,
  Min,
} from 'class-validator';
import { MaxChars } from '../../common/char-length.decorator.js';
import {
  COVER_URL_MAX_LENGTH,
  COVER_URL_PATTERN,
  COVER_URL_REGEX,
} from '../../common/cover-url.js';
import { IsOptionalNonNull } from '../../common/optional-non-null.decorator.js';
import { TrimToNull } from '../../common/transforms.js';
import {
  CollectionStatus,
  Completeness,
  Condition,
  GameFormat,
  Platform,
  PlayStatus,
  Region,
} from '../../generated/prisma/enums.js';

export const MAX_PRICE = 9_999_999_999.99;

const CURRENCY_PATTERN = '^[A-Za-z]{3}$';

/**
 * Body of POST /games and PUT /games/{id}. PUT replaces the whole record:
 * omitted optional fields are cleared and omitted defaults are reset.
 * Text lengths are counted in Unicode code points, as the apps count them.
 */
@ApiSchema({ name: 'SaveGameRequest' })
export class SaveGameDto {
  @ApiProperty({
    maxLength: 200,
    example: 'The Legend of Zelda: Ocarina of Time',
  })
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @IsNotEmpty()
  @MaxChars(200)
  title: string;

  @ApiProperty({ enum: Platform, enumName: 'Platform', example: Platform.N64 })
  @IsEnum(Platform)
  platform: Platform;

  @ApiPropertyOptional({
    enum: CollectionStatus,
    enumName: 'CollectionStatus',
    default: CollectionStatus.OWNED,
  })
  @IsOptionalNonNull()
  @IsEnum(CollectionStatus)
  status?: CollectionStatus;

  @ApiPropertyOptional({
    enum: GameFormat,
    enumName: 'GameFormat',
    default: GameFormat.PHYSICAL,
  })
  @IsOptionalNonNull()
  @IsEnum(GameFormat)
  format?: GameFormat;

  @ApiPropertyOptional({ enum: Region, enumName: 'Region', nullable: true })
  @IsOptional()
  @IsEnum(Region)
  region?: Region | null;

  @ApiPropertyOptional({
    type: String,
    nullable: true,
    maxLength: 100,
    example: "Collector's Edition",
  })
  @TrimToNull()
  @IsOptional()
  @IsString()
  @MaxChars(100)
  edition?: string | null;

  @ApiPropertyOptional({
    enum: Completeness,
    enumName: 'Completeness',
    nullable: true,
  })
  @IsOptional()
  @IsEnum(Completeness)
  completeness?: Completeness | null;

  @ApiPropertyOptional({
    enum: Condition,
    enumName: 'Condition',
    nullable: true,
  })
  @IsOptional()
  @IsEnum(Condition)
  condition?: Condition | null;

  @ApiPropertyOptional({
    enum: PlayStatus,
    enumName: 'PlayStatus',
    nullable: true,
  })
  @IsOptional()
  @IsEnum(PlayStatus)
  playStatus?: PlayStatus | null;

  @ApiPropertyOptional({
    type: String,
    nullable: true,
    maxLength: 100,
    example: 'Action-adventure',
  })
  @TrimToNull()
  @IsOptional()
  @IsString()
  @MaxChars(100)
  genre?: string | null;

  @ApiPropertyOptional({
    type: String,
    nullable: true,
    maxLength: 100,
    example: 'Nintendo EAD',
  })
  @TrimToNull()
  @IsOptional()
  @IsString()
  @MaxChars(100)
  developer?: string | null;

  @ApiPropertyOptional({
    type: String,
    nullable: true,
    maxLength: 100,
    example: 'Nintendo',
  })
  @TrimToNull()
  @IsOptional()
  @IsString()
  @MaxChars(100)
  publisher?: string | null;

  @ApiPropertyOptional({
    type: 'integer',
    nullable: true,
    minimum: 1950,
    maximum: 2100,
    example: 1998,
  })
  @IsOptional()
  @IsInt()
  @Min(1950)
  @Max(2100)
  releaseYear?: number | null;

  @ApiPropertyOptional({
    type: String,
    nullable: true,
    pattern: '^[0-9]{8,14}$',
    description: 'EAN / UPC (8–14 digits).',
    example: '045496870058',
  })
  @TrimToNull()
  @IsOptional()
  @Matches(/^\d{8,14}$/, { message: 'barcode must contain 8–14 digits' })
  barcode?: string | null;

  @ApiPropertyOptional({
    type: String,
    nullable: true,
    maxLength: 50,
    description: 'Catalogue / serial number from the media or spine.',
    example: 'NUS-NZLP-EUR',
  })
  @TrimToNull()
  @IsOptional()
  @IsString()
  @MaxChars(50)
  productCode?: string | null;

  @ApiPropertyOptional({
    type: 'integer',
    minimum: 1,
    maximum: 999,
    default: 1,
  })
  @IsOptionalNonNull()
  @IsInt()
  @Min(1)
  @Max(999)
  quantity?: number;

  @ApiPropertyOptional({
    type: Number,
    nullable: true,
    minimum: 0,
    maximum: MAX_PRICE,
    description: 'At most 2 decimal places.',
    example: 1299.9,
  })
  @IsOptional()
  @IsNumber({ maxDecimalPlaces: 2, allowNaN: false, allowInfinity: false })
  @Min(0)
  @Max(MAX_PRICE)
  purchasePrice?: number | null;

  @ApiPropertyOptional({
    type: String,
    format: 'date',
    nullable: true,
    example: '2024-05-17',
  })
  @TrimToNull()
  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/, {
    message: 'purchaseDate must be in YYYY-MM-DD format',
  })
  @IsISO8601({ strict: true }, { message: 'purchaseDate must be a valid date' })
  purchaseDate?: string | null;

  @ApiPropertyOptional({
    type: String,
    nullable: true,
    maxLength: 100,
    example: 'Flea market',
  })
  @TrimToNull()
  @IsOptional()
  @IsString()
  @MaxChars(100)
  purchasePlace?: string | null;

  @ApiPropertyOptional({
    type: Number,
    nullable: true,
    minimum: 0,
    maximum: MAX_PRICE,
    description: 'At most 2 decimal places.',
    example: 2500,
  })
  @IsOptional()
  @IsNumber({ maxDecimalPlaces: 2, allowNaN: false, allowInfinity: false })
  @Min(0)
  @Max(MAX_PRICE)
  estimatedValue?: number | null;

  @ApiPropertyOptional({
    minLength: 3,
    maxLength: 3,
    pattern: CURRENCY_PATTERN,
    default: 'CZK',
    description:
      'Currency of purchasePrice and estimatedValue: 3 letters A–Z, case-insensitive, stored ' +
      'upper-case. Usually an ISO 4217 code; historic codes such as DEM or SKK are accepted too.',
    example: 'CZK',
  })
  // Only ASCII letters are upper-cased, so that e.g. "ı" cannot turn into "I".
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string'
      ? value.trim().replace(/[a-z]/g, (letter) => letter.toUpperCase())
      : value,
  )
  @IsOptionalNonNull()
  @Matches(/^[A-Z]{3}$/, { message: 'currency must be 3 letters A–Z' })
  currency?: string;

  @ApiPropertyOptional({
    type: String,
    nullable: true,
    maxLength: 100,
    example: 'Shelf A, row 3',
  })
  @TrimToNull()
  @IsOptional()
  @IsString()
  @MaxChars(100)
  storageLocation?: string | null;

  @ApiPropertyOptional({
    type: 'integer',
    nullable: true,
    minimum: 1,
    maximum: 10,
    example: 9,
  })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(10)
  rating?: number | null;

  @ApiPropertyOptional({ default: false })
  @IsOptionalNonNull()
  @IsBoolean()
  favorite?: boolean;

  @ApiPropertyOptional({
    type: String,
    format: 'uri',
    nullable: true,
    maxLength: COVER_URL_MAX_LENGTH,
    pattern: COVER_URL_PATTERN,
    description: 'http(s) URL of ASCII characters; see `pattern`.',
  })
  @TrimToNull()
  @IsOptional()
  @Matches(COVER_URL_REGEX, {
    message: 'coverImageUrl must be an http(s) URL',
  })
  @MaxChars(COVER_URL_MAX_LENGTH)
  coverImageUrl?: string | null;

  @ApiPropertyOptional({ type: String, nullable: true, maxLength: 5000 })
  @TrimToNull()
  @IsOptional()
  @IsString()
  @MaxChars(5000)
  notes?: string | null;
}

/**
 * Body of POST /games. Offline clients generate the id themselves, which also
 * makes the call idempotent: repeating it returns the existing game unchanged.
 */
@ApiSchema({ name: 'CreateGameRequest' })
export class CreateGameDto extends SaveGameDto {
  @ApiPropertyOptional({
    format: 'uuid',
    description:
      'Client-generated id. When a game with this id already exists, it is returned unchanged (200).',
  })
  @IsOptional()
  @IsUUID()
  id?: string;
}

/**
 * Body of PATCH /games/{id}: only the fields present change. `null` clears an
 * optional field; fields that cannot be empty reject `null`.
 */
@ApiSchema({ name: 'UpdateGameRequest' })
export class UpdateGameDto extends PartialType(SaveGameDto, {
  skipNullProperties: false,
}) {}
