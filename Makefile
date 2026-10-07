# Shortcuts for the whole monorepo. Each app can also be used on its own.
.PHONY: help db db-local api-install api-dev api-test openapi seed android android-test ios ios-device-host ios-test test

IOS_DESTINATION ?= platform=iOS Simulator,name=iPhone 17

help: ## List targets
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  %-16s %s\n", $$1, $$2}'

db: ## Start PostgreSQL in Docker
	docker compose up -d --wait db

db-local: ## Create the databases in a local PostgreSQL instead (e.g. Homebrew)
	@pg_isready -q || { echo "PostgreSQL is not running (Homebrew: brew services start postgresql@18)"; exit 1; }
	@for db in game_shelf game_shelf_test; do \
		if psql -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '$$db'" | grep -q 1; then \
			echo "$$db already exists"; \
		else \
			createdb $$db && echo "Created $$db" || exit 1; \
		fi; \
	done

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

ios-device-host: ## Point Debug builds on a physical iPhone at this Mac (writes ios/Config/Local.xcconfig)
	@host="$(or $(DEV_API_HOST),$$(scutil --get LocalHostName).local)"; \
	printf '// Created by `make ios-device-host`; not committed.\nDEV_API_HOST = %s\n' "$$host" > ios/Config/Local.xcconfig; \
	echo "Debug builds on a device will use http://$$host:3000/api/v1/ (rebuild the app in Xcode)"

ios-test: ## iOS unit tests
	xcodebuild -project ios/GameShelf.xcodeproj -scheme GameShelf -destination '$(IOS_DESTINATION)' test

test: api-test android-test ios-test ## Run every test suite
