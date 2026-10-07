import { ApiProperty, ApiSchema } from '@nestjs/swagger';
import type { User } from '../../generated/prisma/client.js';

@ApiSchema({ name: 'User' })
export class UserDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ format: 'email', example: 'collector@example.com' })
  email: string;

  @ApiProperty({ type: String, nullable: true, example: 'Retro Rob' })
  displayName: string | null;

  @ApiProperty({ format: 'date-time' })
  createdAt: Date;

  static from(user: User): UserDto {
    return {
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      createdAt: user.createdAt,
    };
  }
}
