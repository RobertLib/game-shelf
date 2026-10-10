import {
  type ArgumentsHost,
  BadRequestException,
  HttpException,
  HttpStatus,
  ImATeapotException,
  InternalServerErrorException,
  Logger,
  MethodNotAllowedException,
  NotFoundException,
  PayloadTooLargeException,
  ServiceUnavailableException,
  UnsupportedMediaTypeException,
} from '@nestjs/common';
import { ApiException } from './api-exception.js';
import { ErrorCode } from './error-codes.js';
import { HttpExceptionFilter } from './http-exception.filter.js';

function render(exception: unknown) {
  const response = { status: vi.fn(), json: vi.fn() };
  response.status.mockReturnValue(response);
  const host = {
    switchToHttp: () => ({ getResponse: () => response }),
  } as unknown as ArgumentsHost;
  new HttpExceptionFilter().catch(exception, host);
  return response.json.mock.calls[0][0] as { statusCode: number; code: string };
}

describe('HttpExceptionFilter', () => {
  it('keeps the code of an API error', () => {
    expect(
      render(
        new ApiException(
          HttpStatus.NOT_FOUND,
          ErrorCode.GAME_NOT_FOUND,
          'Game not found',
        ),
      ),
    ).toMatchObject({ statusCode: 404, code: 'GAME_NOT_FOUND' });
  });

  it.each([
    [new BadRequestException(), 400, 'BAD_REQUEST'],
    [new NotFoundException(), 404, 'NOT_FOUND'],
    [new MethodNotAllowedException(), 405, 'NOT_FOUND'],
    [new PayloadTooLargeException(), 413, 'VALIDATION_FAILED'],
    [new UnsupportedMediaTypeException(), 415, 'VALIDATION_FAILED'],
    [new ImATeapotException(), 418, 'BAD_REQUEST'],
    [new HttpException('Request Timeout', 408), 408, 'BAD_REQUEST'],
  ])(
    'gives other client errors an existing code: %s',
    (exception, statusCode, code) => {
      expect(render(exception)).toMatchObject({ statusCode, code });
    },
  );

  it('takes the status of client errors from Express middleware', () => {
    const tooLarge = Object.assign(new Error('request entity too large'), {
      status: 413,
      statusCode: 413,
      expose: true,
      type: 'entity.too.large',
    });
    expect(render(tooLarge)).toEqual({
      statusCode: 413,
      code: 'VALIDATION_FAILED',
      message: 'request entity too large',
    });
  });

  it.each([
    [new InternalServerErrorException(), 500],
    [new ServiceUnavailableException(), 503],
  ])('labels server errors INTERNAL_ERROR: %s', (exception, statusCode) => {
    expect(render(exception)).toMatchObject({
      statusCode,
      code: 'INTERNAL_ERROR',
    });
  });

  it('hides unexpected errors behind a 500', () => {
    const log = vi
      .spyOn(Logger.prototype, 'error')
      .mockImplementation(() => undefined);
    const hidden = Object.assign(new Error('secret'), { status: 400 });
    for (const exception of [new Error('boom'), hidden]) {
      expect(render(exception)).toEqual({
        statusCode: 500,
        code: 'INTERNAL_ERROR',
        message: 'Internal server error',
      });
    }
    expect(log).toHaveBeenCalled();
    log.mockRestore();
  });
});
