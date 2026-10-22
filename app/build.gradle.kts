import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).orElse(10842).get()
        versionName = providers.gradleProperty("versionName").orElse("1.4-media-candidate").get()
        buildConfigField("boolean", "EXTENDED_MEDIA", extendedMedia.toString())

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
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile", "release-keystore.jks"))
                storePassword = keystoreProperties.getProperty("storePassword", "")
                keyAlias = keystoreProperties.getProperty("keyAlias", "")
                keyPassword = keystoreProperties.getProperty("keyPassword", "")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
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
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        // Phase 6: every user-visible string must exist in values/ and values-ar/.
        error += setOf("MissingTranslation")
    }

    testOptions {
        // pdfbox-android JVM tests hit android.util.Log inside font parsing.
        unitTests.isReturnDefaultValues = true
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
    if (extendedMedia) {
        // Native media engine; keep the library and FFmpeg on the same version.
        implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
        implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    }
    implementation(libs.androidx.core.ktx)
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
    implementation(libs.androidx.compose.material.icons.extended)
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
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.camera2)
    implementation(libs.camera.view)
    // On-device Google Play services scanner with CameraX manual fallback.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
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

// Hard APK distribution limit (decimal MB), enforced on the actual signed file.
// Use `./gradlew :app:verifyCompactApkSize` (includes :app:assembleCompact).
// A failure means optimization must continue; do NOT silently omit features.
tasks.register("verifyCompactApkSize") {
    group = "verification"
    description = "Build ARM64 compact APK and fail unless its actual size is <= 60,000,000 bytes"
    dependsOn("assembleCompact")
    doLast {
        // A small direct-link-only build is not a substitute for the full downloader.
        check(extendedMedia) { "Compact size verification requires the full media runtime; omit -PextendedMedia=false." }
        val folder = layout.buildDirectory.dir("outputs/apk/compact").get().asFile
        val apks = folder.listFiles()?.filter { it.isFile && it.extension == "apk" }.orEmpty()
        check(apks.size == 1) { "Expected exactly one compact APK in $folder; found ${apks.size}" }
        val apk = apks.single()
        val maxBytes = 60_000_000L
        println("Compact APK: ${apk.absolutePath} = ${apk.length()} bytes; cap = $maxBytes bytes")
        check(apk.length() <= maxBytes) {
            "Compact APK exceeds 60MB by ${apk.length() - maxBytes} bytes; full downloader is preserved. Do not distribute this build as <=60MB."
        }
    }
}
