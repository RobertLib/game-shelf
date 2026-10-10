import { Controller, Get, HttpStatus, Logger } from '@nestjs/common';
import { ApiOkResponse, ApiOperation, ApiTags } from '@nestjs/swagger';
import { ApiErrorResponses } from '../common/api-error-responses.decorator.js';
import { ApiException } from '../common/api-exception.js';
import { ErrorCode } from '../common/error-codes.js';
import { Public } from '../common/public.decorator.js';
import { PrismaService } from '../prisma/prisma.service.js';

const STATUS_OK = {
  schema: {
    type: 'object',
    properties: { status: { type: 'string', example: 'ok' } },
  },
};

@ApiTags('health')
@Public()
@Controller('health')
export class HealthController {
  private readonly logger = new Logger(HealthController.name);

  constructor(private readonly prisma: PrismaService) {}

  @Get()
  @ApiOperation({ operationId: 'getHealth', summary: 'Liveness probe' })
  @ApiOkResponse(STATUS_OK)
  check() {
    return { status: 'ok' };
  }

  /** The database connects lazily, so only a query tells that it can be reached. */
  @Get('ready')
  @ApiOperation({
    operationId: 'getReadiness',
    summary: 'Readiness probe: the API can reach its database',
  })
  @ApiOkResponse(STATUS_OK)
  @ApiErrorResponses(HttpStatus.SERVICE_UNAVAILABLE)
  async ready() {
    try {
      await this.prisma.$queryRaw`SELECT 1`;
    } catch (e) {
      this.logger.error(e);
      throw new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorCode.INTERNAL_ERROR,
        'The database cannot be reached',
      );
    }
    return { status: 'ok' };
  }
}
