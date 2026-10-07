import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  Put,
  Query,
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
import { ApiErrorResponses } from '../common/api-error-responses.decorator.js';
import {
  type AuthUser,
  CurrentUser,
} from '../common/current-user.decorator.js';
import { GameFacetsDto } from './dto/game-facets.dto.js';
import { GameDto, GamePageDto } from './dto/game.dto.js';
import { ListGamesQueryDto } from './dto/list-games-query.dto.js';
import { SaveGameDto } from './dto/save-game.dto.js';
import { GamesService } from './games.service.js';

const { BAD_REQUEST, UNAUTHORIZED, NOT_FOUND } = HttpStatus;

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
  })
  @ApiCreatedResponse({ type: GameDto })
  @ApiErrorResponses(BAD_REQUEST)
  create(
    @CurrentUser() user: AuthUser,
    @Body() dto: SaveGameDto,
  ): Promise<GameDto> {
    return this.games.create(user.id, dto);
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

  @Delete(':id')
  @IdParam()
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({
    operationId: 'deleteGame',
    summary: 'Remove a game from the collection',
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
