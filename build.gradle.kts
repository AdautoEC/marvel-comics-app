plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    kover(project(":app"))
    kover(project(":core:model"))
    kover(project(":core:network"))
    kover(project(":core:database"))
    kover(project(":core:data"))
    kover(project(":core:designsystem"))
    kover(project(":core:ui"))
    kover(project(":feature:movies"))
    kover(project(":feature:detail"))
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*_Factory",
                    "*_Factory\$*",
                    "Hilt_*",
                    "*_HiltModules*",
                    "*Database_Impl*",
                    "*_Impl*",
                    "*BuildConfig*",
                    "*_Provide*Factory*",
                    "*ComposableSingletons*",
                    "*JsonAdapter*",
                )
                packages(
                    "*.di",
                    "dagger.hilt.*",
                    "hilt_aggregated_deps"
                )
                annotatedBy(
                    "*Composable*",
                    "*Preview*",
                    "*Generated*"
                )
            }
        }
    }
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    if (name != "benchmark") {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            val stabilityFile = rootDir.resolve("app/stability_config.conf")
            if (stabilityFile.exists()) {
                stabilityConfigurationFile.set(stabilityFile)
            }
            if (project.findProperty("composeCompilerReports") == "true") {
                val reportsDir = layout.buildDirectory.dir("compose_compiler")
                metricsDestination.set(reportsDir)
                reportsDestination.set(reportsDir)
            }
        }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        parallel = true
        val baselineFile = file("$projectDir/detekt-baseline.xml")
        if (baselineFile.exists()) {
            baseline.set(baselineFile)
        }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        baseline.set(file("$projectDir/detekt-baseline.xml"))
    }
}

tasks.register<Exec>("checkNoHardcodedStrings") {
    group = "verification"
    description = "Ensures no hardcoded string literals exist in production Compose code"
    workingDir = rootDir
    commandLine("./ci/check_no_hardcoded_strings.sh")
}
