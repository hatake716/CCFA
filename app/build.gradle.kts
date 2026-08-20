plugins {
    id("com.android.application")
}

val runtimeDir = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
val embeddedProotLibrary = runtimeDir.file("libproot.so")
val embeddedProotLoader = runtimeDir.file("libproot-loader.so")
val embeddedShmem = runtimeDir.file("libandroid-shmem.so")
val embeddedTalloc = runtimeDir.file("libtalloc.so")
val legalDir = layout.projectDirectory.dir("src/main/assets/legal")

val verifyEmbeddedRuntime by tasks.registering {
    group = "verification"
    description = "Verify the Android/Bionic PRoot runtime bundle prepared by scripts/prepare-termux-android-proot.sh"
    inputs.files(embeddedProotLibrary, embeddedProotLoader, embeddedShmem, embeddedTalloc)

    doLast {
        val required = listOf(
            embeddedProotLibrary.asFile to 100_000L,
            embeddedProotLoader.asFile to 1_000L,
            embeddedShmem.asFile to 1_000L,
            embeddedTalloc.asFile to 10_000L
        )
        required.forEach { (file, minimum) ->
            check(file.isFile && file.length() > minimum) {
                "Missing embedded runtime component ${file.name}. Run scripts/prepare-termux-android-proot.sh before building."
            }
        }
    }
}

val verifyDistributionLegal by tasks.registering {
    group = "verification"
    description = "Require licenses, attribution notices and corresponding source in every distribution APK"
    val required = listOf(
        "NOTICE.txt",
        "licenses/APACHE-2.0.txt",
        "licenses/GPL-2.0.txt",
        "licenses/GPL-3.0.txt",
        "licenses/LGPL-3.0.txt",
        "licenses/BSD-3-Clause-libandroid-shmem.txt",
        "licenses/TERMUX-TERMINAL-LICENSE.md",
        "licenses/COMMONS-COMPRESS-NOTICE.txt",
        "licenses/COMMONS-CODEC-NOTICE.txt",
        "licenses/COMMONS-IO-NOTICE.txt",
        "licenses/COMMONS-LANG3-NOTICE.txt",
        "sources/proot-v5.1.107.91.zip",
        "sources/libandroid-shmem-v0.7.tar.gz.source",
        "sources/talloc-2.4.3.tar.gz.source",
        "sources/ccfa-prepare-termux-android-proot.sh",
        "SOURCE-AND-LICENSE-MANIFEST.sha256"
    )
    inputs.files(required.map { legalDir.file(it) })
    doLast {
        required.forEach { relative ->
            val file = legalDir.file(relative).asFile
            check(file.isFile && file.length() > 0L) {
                "Missing distribution legal/source asset: $relative. Run scripts/prepare-distribution-legal.sh before building."
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(verifyEmbeddedRuntime, verifyDistributionLegal)
}

android {
    namespace = "io.github.hatake716.claudecodeandroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.hatake716.claudecodeandroid"
        minSdk = 26
        targetSdk = 28
        versionCode = 18
        versionName = "0.9.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libproot.so"
            keepDebugSymbols += "**/libproot-loader.so"
            keepDebugSymbols += "**/libandroid-shmem.so"
            keepDebugSymbols += "**/libtalloc.so"
        }
    }
}

dependencies {
    implementation("com.termux.termux-app:terminal-view:0.118.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
}
