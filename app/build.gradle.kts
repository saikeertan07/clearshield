import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.clearshield.adblocker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clearshield.adblocker"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val credsFile = rootProject.file("release-creds.properties")
            val credsProps = Properties()
            if (credsFile.exists()) {
                credsFile.inputStream().use { credsProps.load(it) }
            }

            val keystorePath: String = providers.gradleProperty("KEYSTORE_FILE").orNull
                ?: System.getenv("KEYSTORE_FILE")
                ?: credsProps.getProperty("KEYSTORE_FILE")
                ?: "release.keystore"

            val keystorePass: String? = providers.gradleProperty("KEYSTORE_PASSWORD").orNull
                ?: System.getenv("KEYSTORE_PASSWORD")
                ?: credsProps.getProperty("KEYSTORE_PASSWORD")

            val keyAliasStr: String = providers.gradleProperty("KEY_ALIAS").orNull
                ?: System.getenv("KEY_ALIAS")
                ?: credsProps.getProperty("KEY_ALIAS")
                ?: "clearshield"

            val keyPass: String? = providers.gradleProperty("KEY_PASSWORD").orNull
                ?: System.getenv("KEY_PASSWORD")
                ?: credsProps.getProperty("KEY_PASSWORD")
                ?: keystorePass

            val keystoreFile = if (file(keystorePath).isAbsolute) file(keystorePath) else rootProject.file(keystorePath)

            if (!keystoreFile.exists()) {
                throw GradleException("Release keystore file not found at: ${keystoreFile.absolutePath}")
            }
            if (keystorePass.isNullOrEmpty()) {
                throw GradleException("Release keystore password not provided via environment variable or gradle.properties")
            }

            storeFile = keystoreFile
            storePassword = keystorePass
            keyAlias = keyAliasStr
            keyPassword = keyPass
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
        }
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Networking (DoH) & WorkManager
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)

    // Unit testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
