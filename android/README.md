# Game Shelf – Android

Native Android app for cataloguing a game collection (Kotlin, Jetpack Compose, Material 3).
It implements [`../docs/mobile-spec.md`](../docs/mobile-spec.md) against the REST API described in
[`../openapi/openapi.yaml`](../openapi/openapi.yaml).

## Requirements

- JDK 17
- Android SDK with platform **API 37** (`compileSdk`/`targetSdk`) and build-tools 36+
- `local.properties` with the SDK path (not in git), e.g. `sdk.dir=/Users/<you>/Library/Android/sdk`

No Gradle installation is needed – use the wrapper (`./gradlew`).

## Build and run

```bash
cd android
./gradlew assembleDebug            # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest        # unit tests
./gradlew lint                     # Android Lint
./gradlew installDebug             # install on a running emulator / connected device
```

The debug build talks to the local API at `http://10.0.2.2:3000/api/v1/` (the host machine's
`localhost:3000` as seen from the emulator), so start the API from `../api` first.

Android 17+ (API 37) blocks apps from reaching the local network unless they hold the runtime
permission `ACCESS_LOCAL_NETWORK` (shown as "Nearby devices"). The debug build therefore declares it
and asks for it on first launch – without it, requests to `10.0.2.2` time out. The release build
does not need it (public API).

On a **physical device**, forward the port over USB and point the debug build at `localhost`
(cleartext HTTP is allowed in debug builds for `10.0.2.2` and `localhost` only):

```bash
adb reverse tcp:3000 tcp:3000
```

and temporarily change `debugApiBaseUrl` in `app/build.gradle.kts` to `http://localhost:3000/api/v1/`.

## Pointing the release build to another API

The release build reads its base URL from the Gradle property `gameshelf.apiBaseUrl`
(default `https://api.example.com/api/v1/`; it must end with a slash):

```bash
./gradlew assembleRelease -Pgameshelf.apiBaseUrl=https://games.example.org/api/v1/
```

The property can also be set permanently in `~/.gradle/gradle.properties` or `android/gradle.properties`.
The release build is minified with R8; signing is not configured in the repository.

## Architecture

- **Single activity, Compose, Navigation Compose** with type-safe `@Serializable` routes
  (`ui/navigation`). Two nested graphs: `AuthGraph` (sign in, create account) and `MainGraph`
  (list, detail, form, profile). The session decides the start graph; signing out, deleting the
  account or a failed token refresh returns to sign-in with the back stack cleared.
- **MVVM**: ViewModels expose immutable `StateFlow<…UiState>`; one-off events (snackbars,
  navigation) go through a `Channel` and are collected only while the screen is STARTED.
  Search, filters and sort live in the list ViewModel, so they survive opening a detail and rotation.
- **Repositories** (`data/auth`, `data/games`) sit between ViewModels and the API and return
  `ApiResult` with an `AppError`, which the UI maps to messages by error `code`. Successful game
  changes are broadcast via `GamesRepository.changes`, so the list and detail refresh themselves.
- **Networking**: Retrofit + OkHttp + kotlinx.serialization. `AuthInterceptor` adds the bearer token,
  `TokenAuthenticator` refreshes the tokens on 401 (single-flight via a `Mutex`, one retry).
  API enums tolerate unknown values (`OTHER`/`UNKNOWN`); money amounts are `BigDecimal`.
- **Tokens** are stored in Preferences DataStore, encrypted with an AES/GCM key from the Android
  Keystore; the file is excluded from backups and device transfers.
- **Manual DI**: `AppContainer` is created by `GameShelfApplication`; ViewModels are built with
  `viewModelFactory { initializer { … } }`. Images are loaded by Coil 3 over a shared OkHttp client.
- The UI is English only. Numbers, prices (always in the game's currency) and dates follow the
  device locale.

## Package structure

```
cz.gameshelf.app
├── data/api        Retrofit interfaces, DTOs, JSON, interceptor + authenticator, error mapping
├── data/auth       session (Keystore + DataStore), AuthRepository
├── data/games      GamesRepository, filter → query parameter mapping
├── di              AppContainer
├── domain/model    API models (Game, GamePage, GameFacets, enums), filters, errors
├── ui/auth         sign in, create account
├── ui/games/list   paged list with sorting and active filter chips
├── ui/games/filter filter bottom sheet
├── ui/games/detail game detail
├── ui/games/edit   add / edit form
├── ui/profile      profile, change password, delete account
├── ui/components   shared components (fields, dropdowns, states)
├── ui/common       texts, formatting, enum labels and error messages
└── ui/theme        Material 3 theme (dynamic color + custom light/dark fallback scheme)
```
