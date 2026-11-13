import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val releaseKeystore = providers.environmentVariable("ITIVIO_KEYSTORE")
    .orElse(providers.gradleProperty("itivio.keystore"))
val releaseStorePassword = providers.environmentVariable("ITIVIO_KEYSTORE_PASSWORD")
    .orElse(providers.gradleProperty("itivio.keystorePassword"))
val releaseKeyAlias = providers.environmentVariable("ITIVIO_KEY_ALIAS")
    .orElse(providers.gradleProperty("itivio.keyAlias"))
val releaseKeyPassword = providers.environmentVariable("ITIVIO_KEY_PASSWORD")
    .orElse(providers.gradleProperty("itivio.keyPassword"))

android {
    namespace = "app.itivio"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.itivio"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "0.7.0"
        // MapLibre Native ships these ABIs; 32-bit x86 is not supported by the runtime.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

        val contact = providers.gradleProperty("itivio.contact").getOrElse("")
        buildConfigField("String", "CONTACT", "\"$contact\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
                .all { it.isPresent }) {
            create("release") {
                storeFile = file(releaseKeystore.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // Recording is explicit. Normal test runs compare against committed reference images.
                val record = providers.gradleProperty("roborazzi.test.record").getOrElse("false")
                it.systemProperty("roborazzi.test.record", record)
                it.systemProperty("roborazzi.test.verify", (record != "true").toString())
            }
        }
    }
}

// Exported schemas are committed, so a later version can write and test its migrations.
room {
    schemaDirectory("$projectDir/schemas")
}

androidComponents {
    val baseVersionCode = requireNotNull(android.defaultConfig.versionCode)
    val abiVersionCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }
            val abiIndex = abi?.let { abiVersionCodes.getValue(it.identifier) } ?: 0
            output.versionCode.set(baseVersionCode * 10 + abiIndex)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.maplibre.compose)
    implementation(libs.maplibre.compose.material3)
    runtimeOnly(libs.maplibre.compose.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
