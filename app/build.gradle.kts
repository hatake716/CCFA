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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
