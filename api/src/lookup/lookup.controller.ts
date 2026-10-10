import { Controller, Get, HttpStatus, Param, Query } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { ApiErrorResponses } from '../common/api-error-responses.decorator.js';
import {
  type AuthUser,
  CurrentUser,
} from '../common/current-user.decorator.js';
import { BarcodeLookupService } from './barcode-lookup.service.js';
import { BarcodeLookupDto, BarcodeParamDto } from './dto/barcode-lookup.dto.js';
import {
  GameSearchQueryDto,
  GameSearchResponseDto,
} from './dto/game-search.dto.js';
import { GameSearchService } from './game-search.service.js';

const {
  BAD_REQUEST,
  UNAUTHORIZED,
  NOT_FOUND,
  TOO_MANY_REQUESTS,
  SERVICE_UNAVAILABLE,
} = HttpStatus;

@ApiTags('lookup')
@ApiBearerAuth()
@ApiErrorResponses(UNAUTHORIZED)
@Controller('lookup')
export class LookupController {
  constructor(
    private readonly lookup: BarcodeLookupService,
    private readonly gameSearch: GameSearchService,
  ) {}

  /** Each lookup can use up the daily quota of an external database. */
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  @Get('barcode/:barcode')
  @ApiOperation({
    operationId: 'lookupBarcode',
    summary: 'Find a game by the barcode on its box',
    description:
      'Looks the EAN / UPC up in external databases and returns details to prefill a new game ' +
      'with; nothing is saved. 404 `BARCODE_NOT_FOUND`: the code is not known (also when the ' +
      'check digit of a 12–14 digit code is wrong). 429 `TOO_MANY_REQUESTS`: too many calls, ' +
      'or the user has used up their daily number of lookups in the barcode database (results ' +
      'found before do not count). 503 `LOOKUP_UNAVAILABLE`: the database cannot be reached or ' +
      'its daily limit is used up.',
  })
  @ApiOkResponse({ type: BarcodeLookupDto })
  @ApiErrorResponses(
    BAD_REQUEST,
    NOT_FOUND,
    TOO_MANY_REQUESTS,
    SERVICE_UNAVAILABLE,
  )
  barcode(
    @CurrentUser() user: AuthUser,
    @Param() { barcode }: BarcodeParamDto,
  ): Promise<BarcodeLookupDto> {
    return this.lookup.lookup(barcode, user.id);
  }

  /** The apps search as the user types (debounced), so the limit is higher. */
  @Throttle({ default: { limit: 60, ttl: 60_000 } })
  @Get('games')
  @ApiOperation({
    operationId: 'searchGames',
    summary: 'Find games by title',
    description:
      'Searches the game database for games to prefill a new game with; nothing is saved. ' +
      'An empty `items` list means nothing matches. ' +
      '503 `LOOKUP_UNAVAILABLE`: the database cannot be reached or is not configured.',
  })
  @ApiOkResponse({ type: GameSearchResponseDto })
  @ApiErrorResponses(BAD_REQUEST, TOO_MANY_REQUESTS, SERVICE_UNAVAILABLE)
  games(
    @Query() { q, platform }: GameSearchQueryDto,
  ): Promise<GameSearchResponseDto> {
    return this.gameSearch.search(q, platform ?? null);
  }
}
