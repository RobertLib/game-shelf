import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Put,
  Query,
  Res,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiCreatedResponse,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiOperation,
  ApiParam,
  ApiTags,
} from '@nestjs/swagger';
import type { Response } from 'express';
import { ApiErrorResponses } from '../common/api-error-responses.decorator.js';
import {
  type AuthUser,
  CurrentUser,
} from '../common/current-user.decorator.js';
import { GameChangesDto, GameChangesQueryDto } from './dto/game-changes.dto.js';
import { GameFacetsDto } from './dto/game-facets.dto.js';
import { GameDto, GamePageDto } from './dto/game.dto.js';
import { ListGamesQueryDto } from './dto/list-games-query.dto.js';
import {
  CreateGameDto,
  SaveGameDto,
  UpdateGameDto,
} from './dto/save-game.dto.js';
import { GamesService } from './games.service.js';

const { BAD_REQUEST, UNAUTHORIZED, NOT_FOUND, CONFLICT, GONE } = HttpStatus;

const IdParam = () => ApiParam({ name: 'id', format: 'uuid' });

@ApiTags('games')
@ApiBearerAuth()
@ApiErrorResponses(UNAUTHORIZED)
@Controller('games')
export class GamesController {
  constructor(private readonly games: GamesService) {}

  @Get()
  @ApiOperation({
    operationId: 'listGames',
    summary: 'List the collection with filtering, sorting and pagination',
  })
  @ApiOkResponse({ type: GamePageDto })
  @ApiErrorResponses(BAD_REQUEST)
  list(
    @CurrentUser() user: AuthUser,
    @Query() query: ListGamesQueryDto,
  ): Promise<GamePageDto> {
    return this.games.list(user.id, query);
  }

  @Get('facets')
  @ApiOperation({
    operationId: 'getGameFacets',
    summary: 'Distinct values in the collection for building filters',
  })
  @ApiOkResponse({ type: GameFacetsDto })
  facets(@CurrentUser() user: AuthUser): Promise<GameFacetsDto> {
    return this.games.facets(user.id);
  }

  @Get('changes')
  @ApiOperation({
    operationId: 'listGameChanges',
    summary: 'Change feed for offline clients',
    description:
      'Games created, changed or deleted after `cursor`, oldest change first. Start without a ' +
      'cursor, then keep calling with the returned `cursor` while `hasMore` is true. ' +
      '410 SYNC_RESET_REQUIRED means the cursor cannot be continued: drop the synced data and ' +
      'start again without a cursor.',
  })
  @ApiOkResponse({ type: GameChangesDto })
  @ApiErrorResponses(BAD_REQUEST, GONE)
  changes(
    @CurrentUser() user: AuthUser,
    @Query() query: GameChangesQueryDto,
  ): Promise<GameChangesDto> {
    return this.games.changes(user.id, query);
  }

  @Get(':id')
  @IdParam()
  @ApiOperation({ operationId: 'getGame', summary: 'Get one game' })
  @ApiOkResponse({ type: GameDto })
  @ApiErrorResponses(BAD_REQUEST, NOT_FOUND)
  get(
    @CurrentUser() user: AuthUser,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<GameDto> {
    return this.games.get(user.id, id);
  }

  @Post()
  @ApiOperation({
    operationId: 'createGame',
    summary: 'Add a game to the collection',
    description:
      'With a client-generated `id` the call is idempotent: if the game already exists, it is ' +
      'returned unchanged with 200, or 404 when it has been deleted meanwhile.',
  })
  @ApiCreatedResponse({ type: GameDto, description: 'Created' })
  @ApiOkResponse({ type: GameDto, description: 'Already existed, unchanged' })
  @ApiErrorResponses(BAD_REQUEST, NOT_FOUND, CONFLICT)
  async create(
    @CurrentUser() user: AuthUser,
    @Body() dto: CreateGameDto,
    @Res({ passthrough: true }) res: Response,
  ): Promise<GameDto> {
    const { game, created } = await this.games.create(user.id, dto);
    if (!created) res.status(HttpStatus.OK);
    return game;
  }

  @Put(':id')
  @IdParam()
  @ApiOperation({
    operationId: 'updateGame',
    summary: 'Replace a game',
    description:
      'Full replacement: omitted optional fields are cleared, omitted defaults are reset.',
  })
  @ApiOkResponse({ type: GameDto })
  @ApiErrorResponses(BAD_REQUEST, NOT_FOUND)
  replace(
    @CurrentUser() user: AuthUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SaveGameDto,
  ): Promise<GameDto> {
    return this.games.replace(user.id, id, dto);
  }

  @Patch(':id')
  @IdParam()
  @ApiOperation({
    operationId: 'patchGame',
    summary: 'Change some fields of a game',
    description:
      'Only the fields present in the body change; `null` clears an optional field.',
  })
  @ApiOkResponse({ type: GameDto })
  @ApiErrorResponses(BAD_REQUEST, NOT_FOUND)
  patch(
    @CurrentUser() user: AuthUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateGameDto,
  ): Promise<GameDto> {
    return this.games.patch(user.id, id, dto);
  }

  @Delete(':id')
  @IdParam()
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({
    operationId: 'deleteGame',
    summary: 'Remove a game from the collection',
    description: 'Deleting a game that is already deleted also succeeds.',
  })
  @ApiNoContentResponse()
  @ApiErrorResponses(BAD_REQUEST, NOT_FOUND)
  remove(
    @CurrentUser() user: AuthUser,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    return this.games.remove(user.id, id);
  }
}
