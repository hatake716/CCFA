plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.hatake716.claudecodeandroid"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.hatake716.claudecodeandroid"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // Only ship the languages the app actually has resources for.
        androidResources.localeFilters += listOf("en", "ja")
    }

    signingConfigs {
        create("release") {
            // Supplied via gradle.properties / -P flags / CI secrets so no
            // keystore material is committed. Release builds fall back to being
            // unsigned when these are absent.
            val storePath = providers.gradleProperty("CCFA_KEYSTORE_FILE").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("CCFA_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("CCFA_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("CCFA_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
                .takeIf { it.storeFile != null }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Reproducible-ish packaging; keeps the AAB free of build-host metadata.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}
