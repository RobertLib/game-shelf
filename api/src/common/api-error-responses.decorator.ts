import { applyDecorators, HttpStatus } from '@nestjs/common';
import { ApiResponse } from '@nestjs/swagger';
import { ErrorResponseDto } from './error-response.dto.js';

const descriptions: Partial<Record<HttpStatus, string>> = {
  [HttpStatus.BAD_REQUEST]: 'Invalid input (see `code` and `details`)',
  [HttpStatus.UNAUTHORIZED]: 'Missing, invalid or expired credentials',
  [HttpStatus.NOT_FOUND]: 'Resource not found',
  [HttpStatus.CONFLICT]: 'Conflict with existing data',
  [HttpStatus.GONE]:
    'The sync cursor can no longer be continued; start again without it',
  [HttpStatus.TOO_MANY_REQUESTS]: 'Rate limit exceeded',
  [HttpStatus.SERVICE_UNAVAILABLE]:
    'An external service the endpoint relies on is unavailable; try again later',
};

/** Documents the error responses an endpoint can produce. */
export const ApiErrorResponses = (...statuses: HttpStatus[]) =>
  applyDecorators(
    ...statuses.map((status) =>
      ApiResponse({
        status,
        description: descriptions[status],
        type: ErrorResponseDto,
      }),
    ),
  );
