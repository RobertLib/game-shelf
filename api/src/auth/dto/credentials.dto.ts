import { ApiProperty, ApiPropertyOptional, ApiSchema } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import { IsEmail, IsNotEmpty, IsOptional, IsString } from 'class-validator';
import { MaxChars, MinChars } from '../../common/char-length.decorator.js';
import { TrimToNull } from '../../common/transforms.js';

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 128;

const NormalizeEmail = () =>
  Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim().toLowerCase() : value,
  );

@ApiSchema({ name: 'LoginRequest' })
export class LoginDto {
  @ApiProperty({ format: 'email', example: 'collector@example.com' })
  @NormalizeEmail()
  @IsEmail()
  @MaxChars(254)
  email: string;

  @ApiProperty({ format: 'password', example: 'super-secret-123' })
  @IsString()
  @IsNotEmpty()
  @MaxChars(PASSWORD_MAX_LENGTH)
  password: string;
}

@ApiSchema({ name: 'RegisterRequest' })
export class RegisterDto {
  @ApiProperty({ format: 'email', example: 'collector@example.com' })
  @NormalizeEmail()
  @IsEmail()
  @MaxChars(254)
  email: string;

  @ApiProperty({
    format: 'password',
    minLength: PASSWORD_MIN_LENGTH,
    maxLength: PASSWORD_MAX_LENGTH,
    example: 'super-secret-123',
  })
  @IsString()
  @MinChars(PASSWORD_MIN_LENGTH)
  @MaxChars(PASSWORD_MAX_LENGTH)
  password: string;

  @ApiPropertyOptional({
    type: String,
    nullable: true,
    maxLength: 100,
    example: 'Retro Rob',
  })
  @TrimToNull()
  @IsOptional()
  @IsString()
  @MaxChars(100)
  displayName?: string | null;
}

@ApiSchema({ name: 'RefreshTokenRequest' })
export class RefreshTokenDto {
  @ApiProperty()
  @IsString()
  @IsNotEmpty()
  @MaxChars(200)
  refreshToken: string;
}

@ApiSchema({ name: 'ChangePasswordRequest' })
export class ChangePasswordDto {
  @ApiProperty({ format: 'password' })
  @IsString()
  @IsNotEmpty()
  @MaxChars(PASSWORD_MAX_LENGTH)
  currentPassword: string;

  @ApiProperty({
    format: 'password',
    minLength: PASSWORD_MIN_LENGTH,
    maxLength: PASSWORD_MAX_LENGTH,
  })
  @IsString()
  @MinChars(PASSWORD_MIN_LENGTH)
  @MaxChars(PASSWORD_MAX_LENGTH)
  newPassword: string;
}

@ApiSchema({ name: 'DeleteAccountRequest' })
export class DeleteAccountDto {
  @ApiProperty({
    format: 'password',
    description: 'Current password as a confirmation.',
  })
  @IsString()
  @IsNotEmpty()
  @MaxChars(PASSWORD_MAX_LENGTH)
  password: string;
}
