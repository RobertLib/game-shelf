import { ApiProperty, ApiSchema } from '@nestjs/swagger';
import { UserDto } from './user.dto.js';

@ApiSchema({ name: 'AuthResponse' })
export class AuthResponseDto {
  @ApiProperty({
    description: 'Short-lived JWT for the `Authorization: Bearer` header.',
  })
  accessToken: string;

  @ApiProperty({
    type: 'integer',
    description: 'Expiry of the access token in seconds.',
    example: 900,
  })
  expiresIn: number;

  @ApiProperty({
    description:
      'Opaque single-use token for POST /auth/refresh. Every refresh returns a new one; ' +
      'reusing an old token revokes all sessions of the user.',
  })
  refreshToken: string;

  @ApiProperty({ type: UserDto })
  user: UserDto;
}
