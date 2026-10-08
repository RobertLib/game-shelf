import { ApiProperty, ApiPropertyOptional, ApiSchema } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import {
  IsEnum,
  IsOptional,
  IsString,
  MaxLength,
  MinLength,
} from 'class-validator';
import { Platform } from '../../generated/prisma/enums.js';

const nullableString = (maxLength: number, example: string) =>
  ({ type: String, nullable: true, maxLength, example }) as const;

export class GameSearchQueryDto {
  @ApiProperty({
    minLength: 2,
    maxLength: 100,
    description: 'The title or words from it, as the user types them.',
    example: 'mario kart',
  })
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim().replace(/\s+/g, ' ') : value,
  )
  @IsString()
  @MinLength(2)
  @MaxLength(100)
  q: string;

  @ApiPropertyOptional({
    enum: Platform,
    enumName: 'Platform',
    description:
      'Platform already chosen in the form: games released on it are listed first.',
  })
  @IsOptional()
  @IsEnum(Platform)
  platform?: Platform;
}

/**
 * A game from the database, ready to prefill a new game. Every value fits the
 * limits of `SaveGameRequest`.
 */
@ApiSchema({ name: 'GameSearchResult' })
export class GameSearchResultDto {
  @ApiProperty({
    type: 'integer',
    description: 'ID of the game in IGDB; identifies the result in the list.',
    example: 26758,
  })
  igdbId: number;

  @ApiProperty({ maxLength: 200, example: 'Mario Kart 8 Deluxe' })
  title: string;

  @ApiProperty({
    enum: Platform,
    enumName: 'Platform',
    isArray: true,
    description:
      'Platforms the game came out on, in the order of the Platform enum. Only platforms ' +
      'the collection knows are listed, so it can be empty.',
    example: [Platform.SWITCH],
  })
  platforms: Platform[];

  @ApiProperty(nullableString(100, 'Racing'))
  genre: string | null;

  @ApiProperty(nullableString(100, 'Nintendo EPD'))
  developer: string | null;

  @ApiProperty(nullableString(100, 'Nintendo'))
  publisher: string | null;

  @ApiProperty({ type: 'integer', nullable: true, example: 2017 })
  releaseYear: number | null;

  @ApiProperty({ type: String, format: 'uri', nullable: true })
  coverImageUrl: string | null;
}

@ApiSchema({ name: 'GameSearchResponse' })
export class GameSearchResponseDto {
  @ApiProperty({
    type: [GameSearchResultDto],
    description:
      'At most 20 games, the most likely first; empty when nothing matches.',
  })
  items: GameSearchResultDto[];

  @ApiProperty({
    type: [String],
    description: 'Databases the games come from, to be shown as attribution.',
    example: ['IGDB'],
  })
  sources: string[];
}
