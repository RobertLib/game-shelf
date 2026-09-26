const { withAppBuildGradle } = require('expo/config-plugins');

// Signs Android release builds with the upload key described by Gradle
// properties (keep them in ~/.gradle/gradle.properties, never in the repo):
//
//   GAMESHELF_UPLOAD_STORE_FILE=/absolute/path/to/upload-key.jks
//   GAMESHELF_UPLOAD_STORE_PASSWORD=...
//   GAMESHELF_UPLOAD_KEY_ALIAS=...
//   GAMESHELF_UPLOAD_KEY_PASSWORD=...
//
// Without them, release builds fall back to the debug key: fine for testing
// on your own devices, rejected by Google Play.

const MARKER = 'GAMESHELF_UPLOAD_STORE_FILE';

const RELEASE_SIGNING_CONFIG = `
        release {
            if (findProperty('GAMESHELF_UPLOAD_STORE_FILE')) {
                storeFile file(findProperty('GAMESHELF_UPLOAD_STORE_FILE'))
                storePassword findProperty('GAMESHELF_UPLOAD_STORE_PASSWORD')
                keyAlias findProperty('GAMESHELF_UPLOAD_KEY_ALIAS')
                keyPassword findProperty('GAMESHELF_UPLOAD_KEY_PASSWORD')
            }
        }`;

const RELEASE_SIGNING =
  "signingConfig findProperty('GAMESHELF_UPLOAD_STORE_FILE') ? signingConfigs.release : signingConfigs.debug";

function replaceOrThrow(contents, pattern, replacement, what) {
  if (!pattern.test(contents)) {
    throw new Error(
      `withAndroidReleaseSigning: could not find ${what} in android/app/build.gradle. ` +
        'The Expo template has probably changed; update plugins/withAndroidReleaseSigning.js.',
    );
  }
  return contents.replace(pattern, replacement);
}

module.exports = function withAndroidReleaseSigning(config) {
  return withAppBuildGradle(config, (config) => {
    let contents = config.modResults.contents;
    if (contents.includes(MARKER)) {
      return config;
    }

    contents = replaceOrThrow(
      contents,
      /(signingConfigs \{\n\s*debug \{[^}]*\})/,
      `$1${RELEASE_SIGNING_CONFIG}`,
      'the debug signing config',
    );
    contents = replaceOrThrow(
      contents,
      /(buildTypes \{[\s\S]*?release \{(?:\s*\/\/[^\n]*)*\s*)signingConfig signingConfigs\.debug/,
      `$1${RELEASE_SIGNING}`,
      'the release build type signing config',
    );

    config.modResults.contents = contents;
    return config;
  });
};
