import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("io.github.takahirom.roborazzi") version "1.26.0"
}

// The normal app includes the local extractor, FFmpeg and JavaScript runtime.
// Developers can explicitly build a smaller direct-file-only APK with
// -PextendedMedia=false; that APK cannot extract social platform pages.
val extendedMedia = providers.gradleProperty("extendedMedia").map(String::toBoolean).orElse(true).get()

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}
fun releaseSecret(environment: String, property: String): String =
    providers.environmentVariable(environment).orNull?.takeIf(String::isNotBlank)
        ?: keystoreProperties.getProperty(property, "")
val releaseStorePath = releaseSecret("JUMLO_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = releaseSecret("JUMLO_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = releaseSecret("JUMLO_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = releaseSecret("JUMLO_KEY_PASSWORD", "keyPassword")
val releaseSigningReady = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all(String::isNotBlank)

android {
    namespace = "com.jumlo.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.jumlo.app"
        minSdk = 26
        targetSdk = 36
        // Release versioning: pass -PversionCode / -PversionName at build time
        // (or set them in gradle.properties); defaults keep local builds working.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).orElse(10844).get()
        versionName = providers.gradleProperty("versionName").orElse("1.6-tools").get()
        buildConfigField("boolean", "EXTENDED_MEDIA", extendedMedia.toString())
        buildConfigField("boolean", "CRASH_REPORTING_ENABLED",
            providers.gradleProperty("crashReportingEnabled").map(String::toBoolean).orElse(false).get().toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        resourceConfigurations += listOf("en", "ar")
    }

    // pdfbox-android keeps standard-14 font metrics in AAR assets (loaded via
    // PDFBoxResourceLoader on device); expose them on the unit-test classpath for JVM.
    sourceSets["test"].resources.srcDir(layout.buildDirectory.dir("generated/pdfboxTestAssets/assets"))

    signingConfigs {
        // AGP owns the default debug configuration and creates its local keystore.
        create("release") {
            if (releaseSigningReady) {
                storeFile = rootProject.file(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            ndk { abiFilters.addAll(listOf("arm64-v8a", "x86_64")) }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk { abiFilters.add("arm64-v8a") }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
        // Release-equivalent optimized installer for modern physical ARM64 phones.
        // Preserve the full local platform extractor and FFmpeg pipeline; only the
        // other CPU architectures and the Debug/Compose tooling are excluded.
        // This build is signed with the debug key for personal testing only.
        create("compact") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            versionNameSuffix = "-compact"
            ndk { abiFilters.add("arm64-v8a") }
        }
    }

    // One factory per variant: no reflection or unreachable external runtime in the standard APK.
    sourceSets["debug"].java.srcDir(if (extendedMedia) "src/media/java" else "src/base/java")
    sourceSets["release"].java.srcDir(if (extendedMedia) "src/media/java" else "src/base/java")
    sourceSets["benchmark"].java.srcDir(if (extendedMedia) "src/media/java" else "src/base/java")
    sourceSets["compact"].java.srcDir(if (extendedMedia) "src/media/java" else "src/base/java")
    if (extendedMedia) {
        for (variant in listOf("debug", "release", "benchmark", "compact")) {
            sourceSets[variant].assets.srcDir("src/media/assets")
        }
        // Emulator repair assets belong only to the full-runtime debug build.
        sourceSets["debug"].assets.srcDir("src/mediaDebug/assets")
        // Do not override benchmark's own profileable manifest overlay.
        for (variant in listOf("debug", "release", "compact")) {
            sourceSets[variant].manifest.srcFile("src/media/AndroidManifest.xml")
        }
    }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    bundle {
        abi { enableSplit = true }
        density { enableSplit = true }
        // Standalone APKs include both locales; Play distributes the declared locale splits for AABs.
        // resourceConfigurations above restricts the payload to Arabic and English.
        language { enableSplit = true }
    }

    lint {
        abortOnError = true
        // Phase 6: every user-visible string must exist in values/ and values-ar/.
        error += setOf("MissingTranslation")
    }

    testOptions {
        // pdfbox-android JVM tests hit android.util.Log inside font parsing.
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Post-quantum parameter tables of bcprov: PDF/ZIP features ride the
            // classic BC surface only; R8 strips the PQC classes, so these data
            // files are dead weight (measured ~4.1 MB compressed).
            excludes += "org/bouncycastle/pqc/**"
        }
    }
}

val pdfboxAar = configurations.create("pdfboxAar") {
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":downloader-core"))
    implementation(project(":document-core"))
    implementation(project(":storage-core"))
    if (extendedMedia) {
        // Native media engine; keep the library and FFmpeg on the same version.
        implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
        implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.window)
    implementation(libs.pdfbox.android)
    pdfboxAar(libs.pdfbox.android)
    implementation(libs.zip4j)
    // Multi-page scanner supplied by Google Play services, including gallery import.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    // Lifecycle-bound camera fallback; the Play-services scanner remains the primary flow.
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.26.0")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Keep host Compose tests on the same cached AndroidX Test set as device tests.
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test:runner:1.6.1")
    testImplementation("androidx.test.services:storage:1.5.0")
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val extractPdfboxTestAssets by tasks.registering(Copy::class) {
    from(pdfboxAar.map { zipTree(it) })
    include("assets/**")
    into(layout.buildDirectory.dir("generated/pdfboxTestAssets"))
}

afterEvaluate {
    tasks.matching { it.name.endsWith("UnitTestJavaRes") }.configureEach {
        dependsOn(extractPdfboxTestAssets)
    }

    // The packaged media payload zips are deterministic closure-verified
    // repacks (tools/repack-native-media.py). Bytes must match the digest
    // pins in assets/media/native-overrides/manifest.properties, because the
    // app refuses to run an FFmpeg payload that drifts from the pin.
    val repackTool = rootProject.file("tools/repack-native-media.py")
    val pinManifest = file("src/media/assets/media/native-overrides/manifest.properties")
    val abiNames = Regex("(arm64-v8a|x86_64|armeabi-v7a|x86)$")
    tasks.matching { it.name.matches(Regex("merge[A-Za-z]+NativeLibs")) }.configureEach {
        if (extendedMedia) {
            inputs.file(repackTool).withPropertyName("nativeMediaRepackTool")
                .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
            inputs.file(pinManifest).withPropertyName("nativeMediaPins")
                .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
        }
        doLast {
            if (!extendedMedia) return@doLast
            val abiDirs = outputs.files.filter { it.isDirectory }.flatMap { dir ->
                dir.walkTopDown().filter { it.isDirectory && abiNames.matches(it.name) }
            }.distinct()
            if (abiDirs.isEmpty()) return@doLast
            val command = listOf("python3", repackTool.absolutePath, "--write",
                "--pins", pinManifest.absolutePath) +
                abiDirs.flatMap { listOf("--dir", it.absolutePath) }
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
            val code = process.waitFor()
            if (code != 0) {
                println(output)
                throw GradleException("Native media repack failed (exit $code): $repackTool")
            }
            output.lineSequence().filter { it.startsWith("REPACKED_OK") }.forEach { println("    $it") }
        }
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Product release gate: the complete ARM64 media-enabled APK must be below 60 MB
// (60,000,000 decimal bytes). Never skip codecs/features to bypass this gate.
val maximumApkBytes = 60_000_000L
tasks.register("verifyCompactApkSize") {
    group = "verification"
    description = "Verify full-media ARM64 compact APK is below 60,000,000 bytes"
    dependsOn("assembleCompact")
    doLast {
        // A small direct-link-only build is not a substitute for the full downloader.
        check(extendedMedia) { "Compact size verification requires the full media runtime; omit -PextendedMedia=false." }
        val folder = layout.buildDirectory.dir("outputs/apk/compact").get().asFile
        val apks = folder.listFiles()?.filter { it.isFile && it.extension == "apk" }.orEmpty()
        check(apks.size == 1) { "Expected exactly one compact APK in $folder; found ${apks.size}" }
        val apk = apks.single()
        println("Compact APK: ${apk.absolutePath} = ${apk.length()} / $maximumApkBytes bytes")
        check(apk.length() < maximumApkBytes) { "Compact APK exceeds the strict 60 MB limit: ${apk.length()} bytes" }
    }
}

tasks.register("verifyReleaseApkSize") {
    group = "verification"
    description = "Verify full-media ARM64 release APK is below 60,000,000 bytes"
    dependsOn("assembleRelease")
    doLast {
        check(extendedMedia) { "Release qualification requires the complete media runtime." }
        val apks = layout.buildDirectory.dir("outputs/apk/release").get().asFile
            .listFiles()?.filter { it.isFile && it.extension == "apk" }.orEmpty()
        check(apks.size == 1) { "Expected one current release APK; clean obsolete release artifacts first." }
        val apk = apks.single()
        println("Release APK size: ${apk.length()} / $maximumApkBytes bytes")
        check(apk.length() < maximumApkBytes) { "Release APK exceeds the strict 60 MB limit: ${apk.length()} bytes" }
        ZipFile(apk).use { zip ->
            val abis = zip.entries().asSequence().map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith(".so") }.map { it.split('/')[1] }.toSet()
            check(abis == setOf("arm64-v8a")) { "Release APK must contain only ARM64 native libraries." }
            check(zip.getEntry("assets/media/yt-dlp") != null) { "Full release extractor payload is missing." }
        }
    }
}

tasks.register("verifyPublishableRelease") {
    group = "verification"
    description = "Require owner release signing, optimized APK/AAB and size/lint gates; does not publish"
    dependsOn("verifyReleaseApkSize", "bundleRelease", "lintRelease")
    doLast {
        check(releaseSigningReady) { "Supply JUMLO_KEYSTORE_PATH, JUMLO_STORE_PASSWORD, JUMLO_KEY_ALIAS and JUMLO_KEY_PASSWORD, or a complete gitignored keystore.properties." }
        check(rootProject.file(releaseStorePath).isFile) { "Configured release keystore does not exist." }
        check(!releaseKeyAlias.equals("androiddebugkey", ignoreCase = true)) { "A debug signing key cannot qualify a release." }
        check(!rootProject.file(releaseStorePath).name.equals("debug.keystore", ignoreCase = true)) { "A debug keystore cannot qualify a release." }
        println("Build/signing/size gates passed. Device, content-rights, privacy and Play Console gates remain in RELEASE_CHECKLIST.md.")
    }
}
