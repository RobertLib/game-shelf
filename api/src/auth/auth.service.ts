import { HttpStatus, Injectable } from '@nestjs/common';
import { ApiException } from '../common/api-exception.js';
import { ErrorCode } from '../common/error-codes.js';
import { Prisma, type User } from '../generated/prisma/client.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { AuthResponseDto } from './dto/auth-response.dto.js';
import {
  ChangePasswordDto,
  LoginDto,
  RegisterDto,
} from './dto/credentials.dto.js';
import { UserDto } from './dto/user.dto.js';
import { PasswordService } from './password.service.js';
import { TokenService } from './token.service.js';

@Injectable()
export class AuthService {
  /** Verified against when the e-mail is unknown, so timing does not reveal registered accounts. */
  private readonly dummyHash: Promise<string>;

  constructor(
    private readonly prisma: PrismaService,
    private readonly passwords: PasswordService,
    private readonly tokens: TokenService,
  ) {
    this.dummyHash = passwords.hash('dummy-password-for-timing');
  }

  async register(dto: RegisterDto): Promise<AuthResponseDto> {
    const passwordHash = await this.passwords.hash(dto.password);
    let user: User;
    try {
      user = await this.prisma.user.create({
        data: {
          email: dto.email,
          passwordHash,
          displayName: dto.displayName ?? null,
        },
      });
    } catch (e) {
      if (
        e instanceof Prisma.PrismaClientKnownRequestError &&
        e.code === 'P2002'
      ) {
        throw new ApiException(
          HttpStatus.CONFLICT,
          ErrorCode.EMAIL_ALREADY_REGISTERED,
          'E-mail is already registered',
        );
      }
      throw e;
    }
    return this.respond(user);
  }

  async login(dto: LoginDto): Promise<AuthResponseDto> {
    const user = await this.prisma.user.findUnique({
      where: { email: dto.email },
    });
    const valid = await this.passwords.verify(
      dto.password,
      user?.passwordHash ?? (await this.dummyHash),
    );
    if (!user || !valid) {
      throw new ApiException(
        HttpStatus.UNAUTHORIZED,
        ErrorCode.INVALID_CREDENTIALS,
        'Invalid e-mail or password',
      );
    }
    return this.respond(user);
  }

  async refresh(refreshToken: string): Promise<AuthResponseDto> {
    const { userId, tokens } = await this.tokens.rotate(refreshToken);
    const user = await this.prisma.user.findUniqueOrThrow({
      where: { id: userId },
    });
    return { ...tokens, user: UserDto.from(user) };
  }

  logout(refreshToken: string): Promise<void> {
    return this.tokens.revoke(refreshToken);
  }

  async me(userId: string): Promise<UserDto> {
    return UserDto.from(
      await this.prisma.user.findUniqueOrThrow({ where: { id: userId } }),
    );
  }

  /** Changes the password, signs out every other session and returns fresh tokens. */
  async changePassword(
    userId: string,
    dto: ChangePasswordDto,
  ): Promise<AuthResponseDto> {
    await this.verifyCurrentPassword(userId, dto.currentPassword);
    if (dto.currentPassword === dto.newPassword) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        ErrorCode.VALIDATION_FAILED,
        'Validation failed',
        ['newPassword must differ from currentPassword'],
      );
    }

    const user = await this.prisma.user.update({
      where: { id: userId },
      data: {
        passwordHash: await this.passwords.hash(dto.newPassword),
        passwordChangedAt: new Date(),
      },
    });
    await this.tokens.revokeAll(userId);
    return this.respond(user);
  }

  /** Permanently removes the account together with its whole collection. */
  async deleteAccount(userId: string, password: string): Promise<void> {
    await this.verifyCurrentPassword(userId, password);
    await this.prisma.user.delete({ where: { id: userId } });
  }

  private async verifyCurrentPassword(
    userId: string,
    password: string,
  ): Promise<void> {
    const user = await this.prisma.user.findUniqueOrThrow({
      where: { id: userId },
    });
    if (!(await this.passwords.verify(password, user.passwordHash))) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        ErrorCode.INVALID_CURRENT_PASSWORD,
        'Current password is incorrect',
      );
    }
  }

  private async respond(user: User): Promise<AuthResponseDto> {
    const tokens = await this.tokens.issue(user.id);
    return { ...tokens, user: UserDto.from(user) };
  }
}
