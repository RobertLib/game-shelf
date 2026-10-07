import { ApiProperty, ApiPropertyOptional, ApiSchema } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsInt, IsOptional, Matches, Max, Min } from 'class-validator';
import { GameDto } from './game.dto.js';

export const MAX_CHANGES_LIMIT = 1000;

export class GameChangesQueryDto {
  @ApiPropertyOptional({
    description:
      '`cursor` from the previous response. Omit it to get every game from the beginning.',
    example: '42',
  })
  @IsOptional()
  @Matches(/^\d{1,10}$/, { message: 'cursor is invalid' })
  cursor?: string;

  @ApiPropertyOptional({
    type: 'integer',
    minimum: 1,
    maximum: MAX_CHANGES_LIMIT,
    default: 500,
  })
  @Type(() => Number)
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(MAX_CHANGES_LIMIT)
  limit: number = 500;
}

/** One page of the change feed of the user's games, oldest change first. */
@ApiSchema({ name: 'GameChanges' })
export class GameChangesDto {
  @ApiProperty({
    type: [GameDto],
    description: 'Games created or changed after the cursor.',
  })
  games: GameDto[];

  @ApiProperty({
    type: String,
    format: 'uuid',
    isArray: true,
    description: 'Ids of games deleted after the cursor.',
  })
  deletedIds: string[];

  @ApiProperty({
    description:
      'Opaque position in the feed. Send it with the next call; store it together with the data of this page.',
    example: '42',
  })
  cursor: string;

  @ApiProperty({
    description:
      'More changes are waiting; call again right away with the new cursor.',
  })
  hasMore: boolean;
}
