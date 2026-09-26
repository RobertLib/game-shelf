# Game Shelf

A React Native (Expo) app for video game collectors. Users sign up, add the games they own (with photos and collector details) and browse their library with search, filters and sorting. Supabase provides auth, the Postgres database and photo storage.

## Features (v1)

- **Accounts**: email + password sign up and sign in. The session is kept on the device.
- **Add, edit and delete games** with:
  - title, platform (59 consoles, handhelds and computers), edition, physical or digital
  - region (PAL, NTSC-U/J/K/C, region free), condition, completeness (CIB, game + box, loose, box only), serial or product code
  - genres, developer, publisher, release year
  - purchase date, price paid, current value, currency, where you bought it
  - play status, a 1–5 rating, favorite, notes
  - up to 12 photos per game from the camera or the photo library. The first photo is the cover. Photos are resized on the device and uploaded as a full-size image plus a thumbnail.
- **Library**: a cover grid or a detailed list, infinite scrolling, pull to refresh.
- **Search** by title, edition, developer, publisher or serial. Every word has to match, so `zelda wind` finds *The Wind Waker*.
- **Filters**: platform (shows only platforms you own, with game counts), region, format, condition, completeness, play status, genre, release-year range, minimum rating and favorites only. Filters can be combined. The filter screen shows a live "Show N games" count, and active filters appear as removable chips.
- **Sorting**: recently added, title, release year, purchase price, rating.
- **Account screen**: collection stats (games, platforms, favorites, completed), money spent and estimated value per currency, top platforms.
- **Privacy**: each user only ever sees their own games and photos. Postgres row level security and Storage policies enforce this, not only the app.

## Tech stack

| | |
|---|---|
| App | Expo SDK 57, React Native 0.86, Expo Router (file-based navigation, protected routes), TypeScript |
| Data | `@supabase/supabase-js`, TanStack Query (caching, infinite lists), Zustand (filter state and preferences) |
| Forms | react-hook-form + zod |
| Media | expo-image-picker, expo-image-manipulator, expo-image |
| Backend | Supabase Auth, Postgres (RLS), Storage (private bucket) |

Every native module used is included in **Expo Go**, so you don't need a custom build to try the app.

## Setup

### 1. Create the Supabase backend

1. Create a project at [supabase.com](https://supabase.com).
2. Open **SQL Editor**, paste the contents of [`supabase/migrations/20260926000000_init.sql`](supabase/migrations/20260926000000_init.sql) and run it. This creates:
   - the `platforms`, `games` and `game_images` tables, with row level security
   - the `collection_stats()` function used by the account screen
   - the private `game-images` storage bucket and its access policies
   
   If you use the Supabase CLI instead: `npx supabase link --project-ref <ref>` and then `npx supabase db push`.
3. **Authentication → Sign In / Providers → Email**: email + password is on by default.
   - With **Confirm email** on (the default), new users get a confirmation email and sign in after clicking the link. The app tells them this.
   - For local development you can turn **Confirm email** off, so sign-up logs the user in straight away.

### 2. Configure the app

```bash
cp .env.example .env
```

Fill in both values from **Project Settings → API Keys**:

```
EXPO_PUBLIC_SUPABASE_URL=https://<project-ref>.supabase.co
EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY=sb_publishable_...   # or the legacy anon key
```

Never put the secret or `service_role` key into the app.

### 3. Run it

```bash
npm install
npx expo start
```

Scan the QR code with Expo Go (Android) or the Camera app (iOS), or press `i` / `a` to open a simulator or emulator. After changing `.env`, restart with `npx expo start --clear`.

## Native builds

You can build both apps on your own Mac, without EAS. You need Xcode with CocoaPods for iOS, and Android Studio (Android SDK, JDK 17) for Android.

`ios/` and `android/` are generated from `app.json` and `plugins/` ([Continuous Native Generation](https://docs.expo.dev/workflow/continuous-native-generation/)). They are gitignored. Don't edit them by hand, because your changes are lost the next time they are generated. The build commands generate them if they are missing. After you change `app.json`, change a plugin or add a package with native code, regenerate them:

```bash
npx expo prebuild --clean
```

| Command | What it does |
|---|---|
| `npm run ios` / `npm run android` | Debug build. Installs the app on a simulator or emulator and starts Metro. |
| `npm run ios:release` / `npm run android:release` | Release build. The JS is bundled into the app, so it runs without Metro. |
| `npm run android:bundle` | Signed release bundle for Google Play: `android/app/build/outputs/bundle/release/app-release.aab`. |

To pick a connected phone instead of a simulator, add `-- --device`, e.g. `npm run ios:release -- --device`.

`EXPO_PUBLIC_*` values from `.env` are built into the JS bundle. A release build keeps the values that were in `.env` when you built it.

Apps built with Xcode 27 (the iOS 27 SDK) must use the UIScene life cycle, otherwise they crash at launch. On SDK 57 this is enabled by `expo-build-properties` → `ios.enableSceneSupport` in `app.json` ([details](https://github.com/expo/fyi/blob/main/ios-scene-lifecycle.md)). SDK 58 does this by default, so you can remove the option after upgrading.

### iOS signing

- **Your own iPhone**: the first time you run `npm run ios -- --device`, the Expo CLI asks which development team to sign with. Your Apple ID must be added in Xcode → Settings → Accounts. A free account works, but the app stops launching after 7 days.
- **App Store / TestFlight**: run `npx expo prebuild --platform ios` and `xed ios`. In Xcode, select your team under Signing & Capabilities, then choose Product → Archive and upload the archive with Distribute App.

### Android signing

Release builds are signed with the upload key from your Gradle properties. The [`withAndroidReleaseSigning`](plugins/withAndroidReleaseSigning.js) config plugin sets this up. If the properties are missing, release builds use the debug key. You can install these builds on your own devices, but Google Play rejects them.

1. Create an upload key once, outside the repo:
   ```bash
   mkdir -p ~/keys
   keytool -genkeypair -v -storetype PKCS12 -keystore ~/keys/game-shelf-upload.jks \
     -alias game-shelf -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Add these lines to `~/.gradle/gradle.properties`. Use an absolute path, because `~` is not expanded:
   ```properties
   GAMESHELF_UPLOAD_STORE_FILE=/Users/<you>/keys/game-shelf-upload.jks
   GAMESHELF_UPLOAD_STORE_PASSWORD=...
   GAMESHELF_UPLOAD_KEY_ALIAS=game-shelf
   GAMESHELF_UPLOAD_KEY_PASSWORD=...
   ```
3. Back up the keystore and both passwords. Never commit them. `*.jks` and `*.keystore` are gitignored.

Before each store upload, raise `version` in `app.json`. Also add and increase `ios.buildNumber` and `android.versionCode`.

## Project structure

```
src/
  app/                      Expo Router screens
    _layout.tsx             providers + auth guard (Stack.Protected)
    sign-in.tsx, sign-up.tsx
    (app)/                  signed-in area
      index.tsx             library: search, sort, grid/list, active filter chips
      filters.tsx           filter screen (modal) with live result count
      account.tsx           stats + sign out
      games/new.tsx         add a game
      games/[id]/index.tsx  game detail + photo gallery
      games/[id]/edit.tsx   edit a game
  features/
    games/                  Supabase queries, image upload, form schema, React Query hooks
    library/                filter/sort state (Zustand) and active-filter chips
  components/               UI kit (ui/), game components (games/), library tiles (library/)
  constants/                theme tokens, option lists (regions, conditions, genres…)
  lib/                      Supabase client, generated DB types, formatting, dialogs
supabase/migrations/        database schema, RLS policies, storage bucket
```

### Data model

- `platforms` is a read-only reference list. To add a platform, insert a row (`id`, `name`, `short_name`, `manufacturer`, `release_year`, `sort_order`).
- `games` holds one row per copy a user owns. `user_id` defaults to `auth.uid()`. A generated `search_text` column with a trigram index powers the search box.
- `game_images` holds photo metadata. The files live in Storage at `<user_id>/<game_id>/<file>.jpg` (plus `_thumb.jpg`). `position` 0 is the cover.

After changing the schema, regenerate the types:

```bash
npx supabase gen types typescript --project-id <ref> > src/lib/database.types.ts
```

## Scripts

```bash
npx expo start      # dev server
npm run ios         # native debug build (see "Native builds")
npm run android
npx tsc --noEmit    # typecheck
npx expo lint       # lint
npx expo-doctor     # dependency/config checks
```

## Known limitations and ideas for next versions

- **Password reset** is not built yet. It needs a deep-link or OTP email template.
- Collection value is totalled **per currency**. There is no currency conversion.
- Sorting by price compares raw amounts, even across currencies.
- The platform list is fixed. Custom per-user platforms could be added.
- Possible next steps: sharing a public shelf, a wishlist, barcode scanning, looking up game metadata from an online database (e.g. IGDB), CSV import and export.
