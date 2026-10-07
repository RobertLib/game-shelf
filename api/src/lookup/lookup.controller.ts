import { Controller, Get, HttpStatus, Param } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { ApiErrorResponses } from '../common/api-error-responses.decorator.js';
import { BarcodeLookupService } from './barcode-lookup.service.js';
import { BarcodeLookupDto, BarcodeParamDto } from './dto/barcode-lookup.dto.js';

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
  constructor(private readonly lookup: BarcodeLookupService) {}

  /** Each lookup can use up the daily quota of an external database. */
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  @Get('barcode/:barcode')
  @ApiOperation({
    operationId: 'lookupBarcode',
    summary: 'Find a game by the barcode on its box',
    description:
      'Looks the EAN / UPC up in external databases and returns details to prefill a new game ' +
      'with; nothing is saved. 404 `BARCODE_NOT_FOUND`: the code is not known. ' +
      '503 `LOOKUP_UNAVAILABLE`: the database cannot be reached or its daily limit is used up.',
  })
  @ApiOkResponse({ type: BarcodeLookupDto })
  @ApiErrorResponses(
    BAD_REQUEST,
    NOT_FOUND,
    TOO_MANY_REQUESTS,
    SERVICE_UNAVAILABLE,
  )
  barcode(@Param() { barcode }: BarcodeParamDto): Promise<BarcodeLookupDto> {
    return this.lookup.lookup(barcode);
  }
}
