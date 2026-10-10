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
      'Opaque single-use token for POST /auth/refresh. Every refresh returns a new one. ' +
      'Sending the previous token again shortly after (a retry when the response was lost) ' +
      'returns a new pair as long as its replacement is unused; any other reuse of an old ' +
      'token revokes all sessions of the user.',
  })
  refreshToken: string;

  @ApiProperty({ type: UserDto })
  user: UserDto;
}
