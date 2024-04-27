CREATE EXTENSION unaccent;

CREATE TYPE USER_ROLE AS ENUM ('USER', 'ADMIN');

CREATE TABLE "users" (
  "id" SERIAL PRIMARY KEY,
  "createdAt" TIMESTAMP NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMP NOT NULL DEFAULT NOW(),
  "deletedAt" TIMESTAMP,
  "email" VARCHAR(255) NOT NULL UNIQUE,
  "password" VARCHAR(255) NOT NULL,
  "role" USER_ROLE NOT NULL DEFAULT 'USER'
);

CREATE TABLE "games" (
  "id" SERIAL PRIMARY KEY,
  "createdAt" TIMESTAMP NOT NULL DEFAULT NOW(),
  "updatedAt" TIMESTAMP NOT NULL DEFAULT NOW(),
  "deletedAt" TIMESTAMP,
  "name" VARCHAR(255) NOT NULL,
  "genre" VARCHAR(100) NOT NULL,
  "releaseDate" DATE,
  "developer" VARCHAR(255),
  "publisher" VARCHAR(255),
  "platform" VARCHAR(255),
  "rating" DECIMAL(2, 1),
  "ownerId" INTEGER REFERENCES "users" ("id") ON DELETE SET NULL
);

CREATE INDEX "idx_name" ON "games" ("name");
CREATE INDEX "idx_genre" ON "games" ("genre");
CREATE INDEX "idx_releaseDate" ON "games" ("releaseDate");
CREATE INDEX "idx_developer_publisher" ON "games" ("developer", "publisher");
