import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';
import { ThrottlerModule } from '@nestjs/throttler';
import { AuthModule } from './auth/auth.module.js';
import { JwtAuthGuard } from './auth/jwt-auth.guard.js';
import {
  accountTracker,
  AppThrottlerGuard,
  hasAccountThrottle,
} from './common/throttling.js';
import { type Env, validateEnv } from './config/env.js';
import { GamesModule } from './games/games.module.js';
import { HealthController } from './health/health.controller.js';
import { LookupModule } from './lookup/lookup.module.js';
import { PrismaModule } from './prisma/prisma.module.js';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      cache: true,
      validate: validateEnv,
    }),
    ThrottlerModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService<Env, true>) => {
        const disabled = () => !config.get('THROTTLE_ENABLED', { infer: true });
        return {
          throttlers: [
            // Per route, per signed-in user (or IP address); see AppThrottlerGuard.
            { name: 'default', ttl: 60_000, limit: 300 },
            {
              name: 'account',
              ttl: 15 * 60_000,
              limit: 20,
              getTracker: accountTracker,
              skipIf: (context) => disabled() || !hasAccountThrottle(context),
            },
          ],
          skipIf: disabled,
        };
      },
    }),
    PrismaModule,
    AuthModule,
    GamesModule,
    LookupModule,
  ],
  controllers: [HealthController],
  // Global guards run in this order: the throttler counts per user, so it needs `req.user`.
  providers: [
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: AppThrottlerGuard },
  ],
})
export class AppModule {}
