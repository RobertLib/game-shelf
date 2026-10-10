import { ApiProperty, ApiPropertyOptional, ApiSchema } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsInt, IsOptional, Matches, Max, Min } from 'class-validator';
import { CURSOR_REGEX } from '../sync-cursor.js';
import { GameDto } from './game.dto.js';

export const MAX_CHANGES_LIMIT = 1000;

export class GameChangesQueryDto {
  @ApiPropertyOptional({
    description:
      'Opaque `cursor` from the previous response, sent back exactly as received. Omit it to ' +
      'get every game from the beginning. A cursor from before a restore of the server from a ' +
      'backup is answered with 410 SYNC_RESET_REQUIRED.',
    example: '5f1d3c9a2b.42',
  })
  @IsOptional()
  @Matches(CURSOR_REGEX, { message: 'cursor is invalid' })
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
      'Opaque position in the feed (do not parse it). Send it with the next call; store it ' +
      'together with the data of this page.',
    example: '5f1d3c9a2b.42',
  })
  cursor: string;

  @ApiProperty({
    description:
      'More changes are waiting; call again right away with the new cursor.',
  })
  hasMore: boolean;
}
