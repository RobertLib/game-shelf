import { ApiProperty, ApiPropertyOptional, ApiSchema } from '@nestjs/swagger';
import { ErrorCode } from './error-codes.js';

@ApiSchema({ name: 'ErrorResponse' })
export class ErrorResponseDto {
  @ApiProperty({ type: 'integer', example: 400 })
  statusCode: number;

  @ApiProperty({ enum: ErrorCode, enumName: 'ErrorCode' })
  code: ErrorCode;

  @ApiProperty({ example: 'Validation failed' })
  message: string;

  @ApiPropertyOptional({
    type: [String],
    description:
      'Individual validation messages, present for VALIDATION_FAILED.',
    example: ['email must be an email'],
  })
  details?: string[];
}
