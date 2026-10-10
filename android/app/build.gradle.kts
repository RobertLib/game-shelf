plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val debugApiBaseUrl = "http://10.0.2.2:3000/api/v1/"

// Required to package a release (see checkReleaseApiBaseUrl below); debug builds and tests don't need it.
val releaseApiBaseUrlProperty: Provider<String> = providers.gradleProperty("gameshelf.apiBaseUrl")
val releaseApiBaseUrl: String? = releaseApiBaseUrlProperty.orNull
    ?.also { require(it.endsWith("/")) { "gameshelf.apiBaseUrl must end with '/' (got '$it')" } }

android {
    namespace = "cz.gameshelf.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "cz.gameshelf.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Empty without gameshelf.apiBaseUrl – such a release is never packaged.
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiBaseUrl.orEmpty()}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// A release must never ship without its API address: packaging one (APK or bundle) fails without it.
val checkReleaseApiBaseUrl by tasks.registering {
    description = "Checks that gameshelf.apiBaseUrl is set for a release build."
    val apiBaseUrl = releaseApiBaseUrlProperty
    doLast {
        if (!apiBaseUrl.isPresent) {
            throw GradleException(
                "A release build needs the address of the API. Pass it with " +
                    "-Pgameshelf.apiBaseUrl=https://games.example.org/api/v1/ (ending with '/'), " +
                    "or set gameshelf.apiBaseUrl in ~/.gradle/gradle.properties.",
            )
        }
    }
}
tasks.named { it == "packageRelease" || it == "packageReleaseBundle" }.configureEach {
    dependsOn(checkReleaseApiBaseUrl)
}

room {
    // Exported schemas are the baseline for future migrations; keep them in version control.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.play.services.code.scanner)
    // Play services bring Fragment 1.0.0, which breaks the Activity Result API (lint InvalidFragmentVersionForActivityResult).
    implementation(libs.androidx.fragment)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
}
