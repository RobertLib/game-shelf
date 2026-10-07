# Shortcuts for the whole monorepo. Each app can also be used on its own.
.PHONY: help db api-install api-dev api-test openapi seed android android-test ios ios-test test

IOS_DESTINATION ?= platform=iOS Simulator,name=iPhone 17

help: ## List targets
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  %-14s %s\n", $$1, $$2}'

db: ## Start PostgreSQL in Docker
	docker compose up -d --wait db

api-install: ## Install API dependencies and apply migrations
	cd api && npm install && npx prisma migrate deploy

api-dev: ## Run the API in watch mode (http://localhost:3000, docs at /docs)
	cd api && npm run start:dev

api-test: ## API unit + e2e tests
	cd api && npm test && npm run test:e2e

openapi: ## Regenerate openapi/openapi.yaml from the API code
	cd api && npm run openapi

seed: ## Create the demo account (demo@example.com / demo-collector)
	cd api && npm run build && npm run db:seed

android: ## Build the Android debug APK
	cd android && ./gradlew assembleDebug

android-test: ## Android unit tests
	cd android && ./gradlew testDebugUnitTest

ios: ## Build the iOS app for the simulator
	xcodebuild -project ios/GameShelf.xcodeproj -scheme GameShelf -destination '$(IOS_DESTINATION)' build

ios-test: ## iOS unit tests
	xcodebuild -project ios/GameShelf.xcodeproj -scheme GameShelf -destination '$(IOS_DESTINATION)' test

test: api-test android-test ios-test ## Run every test suite
