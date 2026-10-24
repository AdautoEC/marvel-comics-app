plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
}

val googleServicesFile = file("google-services.json")
if (!googleServicesFile.exists()) {
    val fakeFile = rootProject.file("ci/google-services.fake.json")
    if (fakeFile.exists()) {
        fakeFile.copyTo(googleServicesFile, overwrite = false)
    }
}

android {
    namespace = "com.adauto.marvel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marvel.comics"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("android.injected.version.code") as? String)?.toIntOrNull()
            ?: (project.findProperty("versionCode") as? String)?.toIntOrNull()
            ?: (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE") ?: project.findProperty("KEYSTORE_FILE") as? String
            val storePasswordEnv = System.getenv("KEYSTORE_PASSWORD") ?: project.findProperty("KEYSTORE_PASSWORD") as? String
            val keyAliasEnv = System.getenv("KEY_ALIAS") ?: project.findProperty("KEY_ALIAS") as? String
            val keyPasswordEnv = System.getenv("KEY_PASSWORD") ?: project.findProperty("KEY_PASSWORD") as? String
            val isPublishingOrCD = System.getenv("CI_RELEASE_BUILD") == "true" ||
                gradle.startParameter.taskNames.any { it.contains("bundleRelease", ignoreCase = true) }

            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists() &&
                !storePasswordEnv.isNullOrBlank() && !keyAliasEnv.isNullOrBlank() && !keyPasswordEnv.isNullOrBlank()
            ) {
                storeFile = file(keystorePath)
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            } else if (isPublishingOrCD) {
                throw GradleException("Production release signing configuration (KEYSTORE_FILE/PASSWORD/ALIAS) is missing or invalid.")
            } else {
                // Local dev / PR verification with assembleRelease falls back to debug signing to verify R8 minification
                initWith(getByName("debug"))
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
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

composeCompiler {
    stabilityConfigurationFile = layout.projectDirectory.file("stability_config.conf")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":feature:movies"))
    implementation(project(":feature:detail"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.coil.compose)
    implementation(libs.okhttp.core)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.perf)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.paging.compose)
    androidTestImplementation(libs.paging.runtime)
}
