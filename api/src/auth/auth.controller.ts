import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Post,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiCreatedResponse,
  ApiNoContentResponse,
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
import { Public } from '../common/public.decorator.js';
import { AuthService } from './auth.service.js';
import { AuthResponseDto } from './dto/auth-response.dto.js';
import {
  ChangePasswordDto,
  DeleteAccountDto,
  LoginDto,
  RefreshTokenDto,
  RegisterDto,
} from './dto/credentials.dto.js';
import { UserDto } from './dto/user.dto.js';

const { BAD_REQUEST, UNAUTHORIZED, CONFLICT, TOO_MANY_REQUESTS } = HttpStatus;

/** Brute-force protection for endpoints that accept a password. */
const StrictThrottle = () => Throttle({ default: { limit: 10, ttl: 60_000 } });

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  @Public()
  @StrictThrottle()
  @Post('register')
  @ApiOperation({
    operationId: 'register',
    summary: 'Create an account and sign in',
  })
  @ApiCreatedResponse({ type: AuthResponseDto })
  @ApiErrorResponses(BAD_REQUEST, CONFLICT, TOO_MANY_REQUESTS)
  register(@Body() dto: RegisterDto): Promise<AuthResponseDto> {
    return this.auth.register(dto);
  }

  @Public()
  @StrictThrottle()
  @Post('login')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    operationId: 'login',
    summary: 'Sign in with e-mail and password',
  })
  @ApiOkResponse({ type: AuthResponseDto })
  @ApiErrorResponses(BAD_REQUEST, UNAUTHORIZED, TOO_MANY_REQUESTS)
  login(@Body() dto: LoginDto): Promise<AuthResponseDto> {
    return this.auth.login(dto);
  }

  @Public()
  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    operationId: 'refreshTokens',
    summary: 'Exchange a refresh token for a new token pair',
    description:
      'The refresh token is single-use. Repeating a refresh whose response was lost, within ' +
      '2 minutes (by default) and before the token it returned is used, returns a new pair and ' +
      'invalidates the lost one. Any other reuse of an old token revokes all sessions of the user.',
  })
  @ApiOkResponse({ type: AuthResponseDto })
  @ApiErrorResponses(BAD_REQUEST, UNAUTHORIZED, TOO_MANY_REQUESTS)
  refresh(@Body() dto: RefreshTokenDto): Promise<AuthResponseDto> {
    return this.auth.refresh(dto.refreshToken);
  }

  @Public()
  @Post('logout')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({
    operationId: 'logout',
    summary: 'Revoke a refresh token',
    description:
      'Also revokes the tokens it was already exchanged for, e.g. by a refresh that raced the logout.',
  })
  @ApiNoContentResponse()
  @ApiErrorResponses(BAD_REQUEST)
  logout(@Body() dto: RefreshTokenDto): Promise<void> {
    return this.auth.logout(dto.refreshToken);
  }

  @ApiBearerAuth()
  @Get('me')
  @ApiOperation({
    operationId: 'getCurrentUser',
    summary: 'Profile of the signed-in user',
  })
  @ApiOkResponse({ type: UserDto })
  @ApiErrorResponses(UNAUTHORIZED)
  me(@CurrentUser() user: AuthUser): Promise<UserDto> {
    return this.auth.me(user.id);
  }

  @ApiBearerAuth()
  @StrictThrottle()
  @Post('change-password')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    operationId: 'changePassword',
    summary: 'Change password',
    description:
      'Signs out all sessions (every refresh token is revoked) and returns a new token pair ' +
      'for the current device. Fails with `INVALID_CURRENT_PASSWORD` when the current password is wrong.',
  })
  @ApiOkResponse({ type: AuthResponseDto })
  @ApiErrorResponses(BAD_REQUEST, UNAUTHORIZED, TOO_MANY_REQUESTS)
  changePassword(
    @CurrentUser() user: AuthUser,
    @Body() dto: ChangePasswordDto,
  ): Promise<AuthResponseDto> {
    return this.auth.changePassword(user.id, dto);
  }

  @ApiBearerAuth()
  @StrictThrottle()
  @Delete('me')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({
    operationId: 'deleteAccount',
    summary: 'Delete the account and the whole collection',
  })
  @ApiNoContentResponse()
  @ApiErrorResponses(BAD_REQUEST, UNAUTHORIZED, TOO_MANY_REQUESTS)
  deleteAccount(
    @CurrentUser() user: AuthUser,
    @Body() dto: DeleteAccountDto,
  ): Promise<void> {
    return this.auth.deleteAccount(user.id, dto.password);
  }
}
