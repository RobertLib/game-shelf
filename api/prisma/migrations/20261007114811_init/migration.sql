-- CreateEnum
CREATE TYPE "Platform" AS ENUM ('PC', 'MAC', 'PS1', 'PS2', 'PS3', 'PS4', 'PS5', 'PSP', 'PS_VITA', 'XBOX', 'XBOX_360', 'XBOX_ONE', 'XBOX_SERIES', 'NES', 'SNES', 'N64', 'GAMECUBE', 'WII', 'WII_U', 'SWITCH', 'SWITCH_2', 'GAME_BOY', 'GAME_BOY_COLOR', 'GAME_BOY_ADVANCE', 'NINTENDO_DS', 'NINTENDO_3DS', 'VIRTUAL_BOY', 'MASTER_SYSTEM', 'MEGA_DRIVE', 'MEGA_CD', 'SEGA_32X', 'SATURN', 'DREAMCAST', 'GAME_GEAR', 'ATARI_2600', 'ATARI_7800', 'ATARI_LYNX', 'ATARI_JAGUAR', 'NEO_GEO', 'NEO_GEO_POCKET', 'PC_ENGINE', 'THREE_DO', 'ZX_SPECTRUM', 'COMMODORE_64', 'AMIGA', 'AMSTRAD_CPC', 'ATARI_8BIT', 'ATARI_ST', 'MSX', 'OTHER');

-- CreateEnum
CREATE TYPE "CollectionStatus" AS ENUM ('OWNED', 'WISHLIST', 'PREORDERED', 'LENT', 'FOR_SALE', 'SOLD');

-- CreateEnum
CREATE TYPE "GameFormat" AS ENUM ('PHYSICAL', 'DIGITAL');

-- CreateEnum
CREATE TYPE "Region" AS ENUM ('PAL', 'NTSC_U', 'NTSC_J', 'REGION_FREE', 'OTHER');

-- CreateEnum
CREATE TYPE "Completeness" AS ENUM ('SEALED', 'CIB', 'GAME_AND_BOX', 'GAME_AND_MANUAL', 'LOOSE', 'BOX_ONLY');

-- CreateEnum
CREATE TYPE "Condition" AS ENUM ('MINT', 'NEAR_MINT', 'VERY_GOOD', 'GOOD', 'FAIR', 'POOR');

-- CreateEnum
CREATE TYPE "PlayStatus" AS ENUM ('UNPLAYED', 'PLAYING', 'COMPLETED', 'ABANDONED');

-- CreateTable
CREATE TABLE "users" (
    "id" UUID NOT NULL,
    "email" TEXT NOT NULL,
    "passwordHash" TEXT NOT NULL,
    "displayName" TEXT,
    "passwordChangedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "refresh_tokens" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "tokenHash" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "revokedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "refresh_tokens_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "games" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "title" TEXT NOT NULL,
    "platform" "Platform" NOT NULL,
    "status" "CollectionStatus" NOT NULL DEFAULT 'OWNED',
    "format" "GameFormat" NOT NULL DEFAULT 'PHYSICAL',
    "region" "Region",
    "edition" TEXT,
    "completeness" "Completeness",
    "condition" "Condition",
    "playStatus" "PlayStatus",
    "genre" TEXT,
    "developer" TEXT,
    "publisher" TEXT,
    "releaseYear" INTEGER,
    "barcode" TEXT,
    "productCode" TEXT,
    "quantity" INTEGER NOT NULL DEFAULT 1,
    "purchasePrice" DECIMAL(12,2),
    "purchaseDate" DATE,
    "purchasePlace" TEXT,
    "estimatedValue" DECIMAL(12,2),
    "currency" CHAR(3) NOT NULL DEFAULT 'CZK',
    "storageLocation" TEXT,
    "rating" SMALLINT,
    "favorite" BOOLEAN NOT NULL DEFAULT false,
    "coverImageUrl" TEXT,
    "notes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "games_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "users_email_key" ON "users"("email");

-- CreateIndex
CREATE UNIQUE INDEX "refresh_tokens_tokenHash_key" ON "refresh_tokens"("tokenHash");

-- CreateIndex
CREATE INDEX "refresh_tokens_userId_idx" ON "refresh_tokens"("userId");

-- CreateIndex
CREATE INDEX "games_userId_title_idx" ON "games"("userId", "title");

-- CreateIndex
CREATE INDEX "games_userId_platform_idx" ON "games"("userId", "platform");

-- CreateIndex
CREATE INDEX "games_userId_createdAt_idx" ON "games"("userId", "createdAt");

-- AddForeignKey
ALTER TABLE "refresh_tokens" ADD CONSTRAINT "refresh_tokens_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "games" ADD CONSTRAINT "games_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
