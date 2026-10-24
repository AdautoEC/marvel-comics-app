import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.adauto.marvel.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            val localProps = Properties().apply {
                val file = rootProject.file("local.properties")
                if (file.exists()) {
                    file.inputStream().use { stream -> load(stream) }
                }
            }
            val debugApiKey = localProps.getProperty("tmdb.api.key") ?: System.getenv("TMDB_API_KEY") ?: ""
            val debugAccessToken = localProps.getProperty("tmdb.read.access.token") ?: System.getenv("TMDB_READ_ACCESS_TOKEN") ?: ""
            buildConfigField("String", "TMDB_API_KEY", "\"$debugApiKey\"")
            buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"$debugAccessToken\"")
        }
        release {
            buildConfigField("String", "TMDB_API_KEY", "\"\"")
            buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"\"")
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
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.sandwich.core)
    implementation(libs.sandwich.retrofit)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.play.integrity)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
}
