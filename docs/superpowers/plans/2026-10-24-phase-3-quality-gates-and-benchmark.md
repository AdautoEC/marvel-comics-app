# Phase 3: Real Quality Gates, Kover & Benchmark Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish fail-closed quality gates (Detekt with `maxIssues: 0`, per-module baselines, and Compose exceptions; multi-module Kover coverage aggregation; functional `:benchmark` macrobenchmark module; and CI canary validation).

**Architecture:** Hardens static analysis via Detekt 1.23.7 default rules (`buildUponDefaultConfig = true`) with per-module baseline XMLs and `ignoreAnnotated: ['Composable']` on `MagicNumber`. Configures Kotlinx Kover 0.8.3 across all subprojects with root aggregation and exclusions for generated code. Configures `:benchmark` module as a `com.android.test` target for `:app` with `benchmark` build type and Compose `testTagsAsResourceId = true`. Validates with a fail-closed CI pipeline and disposable canary PR.

**Tech Stack:** Gradle Kotlin DSL, Detekt 1.23.7, Kotlinx Kover 0.8.3, AndroidX Benchmark 1.3.3, AndroidX UiAutomator 2.3.0, Jetpack Compose Semantics, GitHub Actions.

**Spec:** [`docs/superpowers/specs/2026-10-24-cinema-hub-production-resilience-design.md`](file:///Users/adautosouza/repos/estudos/docs/superpowers/specs/2026-10-24-cinema-hub-production-resilience-design.md)

## Global Constraints

- Android Application ID: `com.adauto.cinemahub`
- Detekt Configuration: `buildUponDefaultConfig = true`, `maxIssues = 0`, `ignoreAnnotated: ['Composable']` on `MagicNumber`.
- Detekt Baselines: `detekt-baseline.xml` in each module root directory.
- Kover Exclusions: Generated classes (`*_Factory`, `Hilt_*`, `*Database_Impl*`, `*BuildConfig*`, `*_Impl*`), `@Composable`, `@Preview`.
- Benchmark Target: `:app` with build type `benchmark` (initWith `release`, signingConfig `debug`, matchingFallbacks `listOf("release")`).
- Semantics: Root Compose tree sets `Modifier.semantics { testTagsAsResourceId = true }`.
- CI Quality Gate: PR verification workflow must run Detekt, unit tests, Kover coverage reports, and `:benchmark:assembleBenchmark`.

## Review Focus

1. **Detekt Baseline Drift**: Ensure each subproject binds to its local `detekt-baseline.xml` so existing legacy smells don't fail, but any new smell in any module immediately fails the build.
2. **Kover Multi-Module Aggregation**: Ensure the root project collects coverage from all android libraries and application without ClassCastException or missing variant errors.
3. **Kover Exclusions Filter**: Verify dagger/hilt factory classes and compose previews do not distort coverage percentages.
4. **Benchmark Build Type Compatibility**: Ensure `:benchmark` module cleanly resolves the `:app` dependency using the `benchmark` build variant and matchingFallbacks.
5. **CI Fail-Closed Verification**: Ensure the canary PR with deliberate code smells actually breaks the CI build rather than passing silently.

---

### Task 1: Detekt Quality Gate Hardening & Per-Module Baselines

**Files:**
- Modify: `config/detekt/detekt.yml`
- Modify: `build.gradle.kts`
- Create: `app/detekt-baseline.xml`
- Create: `core/model/detekt-baseline.xml`
- Create: `core/network/detekt-baseline.xml`
- Create: `core/database/detekt-baseline.xml`
- Create: `core/data/detekt-baseline.xml`
- Create: `core/designsystem/detekt-baseline.xml`
- Create: `core/ui/detekt-baseline.xml`
- Create: `feature/movies/detekt-baseline.xml`
- Create: `feature/detail/detekt-baseline.xml`

**Interfaces:**
- Consumes: Detekt Gradle Plugin 1.23.7.
- Produces: Hardened Detekt gate with `maxIssues = 0`, `buildUponDefaultConfig = true`, and per-module baselines.

- [ ] **Step 1: Update `config/detekt/detekt.yml`**
  Add `build` block with `maxIssues: 0`, and configure `style: MagicNumber` to ignore `@Composable`:
  ```yaml
  build:
    maxIssues: 0
    weights:
      complexity: 1
      formatting: 1
      style: 1

  naming:
    FunctionNaming:
      active: true
      ignoreAnnotated: ['Composable', 'Test']

  style:
    MagicNumber:
      active: true
      ignoreAnnotated: ['Composable']
      ignoreNumbers: ['-1', '0', '1', '2']
      ignoreHashCodeFunction: true
      ignorePropertyDeclaration: true
      ignoreConstantDeclaration: true
      ignoreCompanionObjectPropertyDeclaration: true
    ForbiddenImport:
      active: true
      imports:
        - value: 'coil.compose.AsyncImage'
          reason: 'Todas as imagens devem passar pelo componente centralizado MoviePoster em core:ui'
        - value: 'coil.compose.rememberAsyncImagePainter'
          reason: 'Use MoviePoster em core:ui para evitar duplicação de ImageLoader'
  ```

- [ ] **Step 2: Update root `build.gradle.kts` Detekt configuration**
  Enable `buildUponDefaultConfig = true` and configure per-module baseline:
  ```kotlin
  subprojects {
      apply(plugin = "io.gitlab.arturbosch.detekt")

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
  ```

- [ ] **Step 3: Generate per-module baselines**
  Run: `./gradlew detektBaseline`
  Verify that `detekt-baseline.xml` files are generated across subprojects.

- [ ] **Step 4: Verify Detekt passes with 0 issues**
  Run: `./gradlew detekt`
  Expected: BUILD SUCCESSFUL (0 code smells).

- [ ] **Step 5: Test fail-closed behavior on new smell**
  Temporarily add an unused private property `private val unusedSmell = 42` to any file, run `./gradlew detekt`, verify it FAILS with `Build failed with 1 weighted issues`. Revert the temporary change.

- [ ] **Step 6: Commit**
  ```bash
  git add config/detekt/detekt.yml build.gradle.kts **/detekt-baseline.xml
  git commit -m "chore(detekt): enforce buildUponDefaultConfig, maxIssues=0, and per-module baselines"
  ```

---

### Task 2: Kover Test Coverage Aggregation Across All Modules

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `core/model/build.gradle.kts`
- Modify: `core/network/build.gradle.kts`
- Modify: `core/database/build.gradle.kts`
- Modify: `core/data/build.gradle.kts`
- Modify: `core/designsystem/build.gradle.kts`
- Modify: `core/ui/build.gradle.kts`
- Modify: `feature/movies/build.gradle.kts`
- Modify: `feature/detail/build.gradle.kts`

**Interfaces:**
- Consumes: Kotlinx Kover 0.8.3 Gradle Plugin.
- Produces: Aggregated coverage report tasks `koverXmlReport` and `koverHtmlReport` with class and preview filters.

- [ ] **Step 1: Add Kover to `gradle/libs.versions.toml`**
  ```toml
  [versions]
  kover = "0.8.3"

  [plugins]
  kotlinx-kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
  ```

- [ ] **Step 2: Configure Kover in root `build.gradle.kts`**
  Apply plugin `alias(libs.plugins.kotlinx.kover)` in root `plugins` block.
  In root `dependencies`:
  ```kotlin
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
  ```
  Configure `kover` report extension at root:
  ```kotlin
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
  ```
  Apply Kover to all subprojects in `subprojects { ... }`:
  ```kotlin
  apply(plugin = "org.jetbrains.kotlinx.kover")
  ```

- [ ] **Step 3: Run Kover HTML and XML reports**
  Run: `./gradlew koverXmlReport koverHtmlReport`
  Expected: BUILD SUCCESSFUL.
  Verify reports exist at `build/reports/kover/report.xml` and `build/reports/kover/html/index.html`.

- [ ] **Step 4: Update per-module Detekt baselines if needed**
  Run: `./gradlew detekt`
  If any Detekt baseline requires updating due to build script changes, update and verify.

- [ ] **Step 5: Commit**
  ```bash
  git add gradle/libs.versions.toml build.gradle.kts
  git commit -m "build(kover): add kotlinx-kover aggregation and exclusions across all modules"
  ```

---

### Task 3: Functional `:benchmark` Module & Compose Test Semantics

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/adauto/marvel/MainActivity.kt`
- Create: `benchmark/build.gradle.kts`
- Modify: `benchmark/src/main/java/com/adauto/marvel/benchmark/BaselineProfileGenerator.kt`

**Interfaces:**
- Consumes: `com.android.test`, `androidx.benchmark.macro.junit4`.
- Produces: `:benchmark:assembleBenchmark` compilation task and Compose UI Automator accessibility tags.

- [ ] **Step 1: Include `:benchmark` in `settings.gradle.kts`**
  Add `include(":benchmark")`.

- [ ] **Step 2: Add Benchmark libraries to `gradle/libs.versions.toml`**
  ```toml
  [versions]
  androidx-benchmark = "1.3.3"
  androidx-uiautomator = "2.3.0"
  androidx-profileinstaller = "1.4.1"

  [libraries]
  androidx-benchmark-macro = { group = "androidx.benchmark", name = "benchmark-macro-junit4", version.ref = "androidx-benchmark" }
  androidx-uiautomator = { group = "androidx.test.uiautomator", name = "uiautomator", version.ref = "androidx-uiautomator" }
  androidx-profileinstaller = { group = "androidx.profileinstaller", name = "profileinstaller", version.ref = "androidx-profileinstaller" }
  ```
  In `app/build.gradle.kts`, add implementation dependency `libs.androidx.profileinstaller`.

- [ ] **Step 3: Configure `benchmark` build type in `app/build.gradle.kts`**
  ```kotlin
  buildTypes {
      create("benchmark") {
          initWith(buildTypes.getByName("release"))
          signingConfig = signingConfigs.getByName("debug")
          matchingFallbacks = listOf("release")
          isDebuggable = false
      }
  }
  ```

- [ ] **Step 4: Set Compose `testTagsAsResourceId = true` in `MainActivity.kt`**
  In `app/src/main/java/com/adauto/marvel/MainActivity.kt`:
  Wrap `MarvelNavHost` with:
  ```kotlin
  Box(
      modifier = Modifier
          .fillMaxSize()
          .semantics { testTagsAsResourceId = true }
  ) {
      val navController = rememberNavController()
      MarvelNavHost(navController = navController)
  }
  ```

- [ ] **Step 5: Create `benchmark/build.gradle.kts`**
  ```kotlin
  plugins {
      alias(libs.plugins.android.test)
      alias(libs.plugins.kotlin.android)
  }

  android {
      namespace = "com.adauto.cinemahub.benchmark"
      compileSdk = 35

      defaultConfig {
          minSdk = 26
          targetSdk = 35
          testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }

      targetProjectPath = ":app"
      experimentalProperties["android.experimental.self-instrumenting"] = true

      buildTypes {
          create("benchmark") {
              isDebuggable = true
              signingConfig = signingConfigs.getByName("debug")
              matchingFallbacks = listOf("release")
          }
      }
  }

  dependencies {
      implementation(libs.androidx.test.ext.junit)
      implementation(libs.androidx.test.runner)
      implementation(libs.androidx.uiautomator)
      implementation(libs.androidx.benchmark.macro)
  }
  ```
  In `gradle/libs.versions.toml`, declare `android-test = { id = "com.android.test", version.ref = "agp" }`.
  In root `build.gradle.kts`, declare `alias(libs.plugins.android.test) apply false`.

- [ ] **Step 6: Update `BaselineProfileGenerator.kt`**
  Ensure package name is `com.adauto.cinemahub` and test target references valid UI accessibility descriptions or testTags.

- [ ] **Step 7: Verify compilation of `:benchmark` and `:app`**
  Run: `./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run Detekt and Unit Tests**
  Run: `./gradlew detekt testDebugUnitTest`
  Expected: PASS with 0 errors.

- [ ] **Step 9: Commit**
  ```bash
  git add settings.gradle.kts gradle/libs.versions.toml build.gradle.kts app/ benchmark/
  git commit -m "feat(benchmark): configure com.android.test benchmark module and testTagsAsResourceId"
  ```

---

### Task 4: CI Quality Gates Workflow & Disposable Canary PR Validation

**Files:**
- Modify: `.github/workflows/pr.yml`
- Modify: `.github/workflows/main-cd.yml`

**Interfaces:**
- Consumes: Detekt, Kover, `:benchmark:assembleBenchmark`.
- Produces: Fail-closed CI pipeline blocking invalid PRs, proven by a disposable canary PR.

- [ ] **Step 1: Update `.github/workflows/pr.yml`**
  Ensure CI checks run Detekt, tests, Kover XML/HTML reports, benchmark assembly, and release assemblies:
  ```yaml
  - name: Run checks, tests, coverage and benchmark assembly
    run: ./gradlew detekt lintDebug testDebugUnitTest koverXmlReport koverHtmlReport :benchmark:assembleBenchmark assembleDebug assembleRelease --stacktrace

  - name: Upload Kover Coverage Report
    uses: actions/upload-artifact@v4
    with:
      name: kover-coverage-report
      path: build/reports/kover/
  ```

- [ ] **Step 2: Update `.github/workflows/main-cd.yml`**
  Add `:benchmark:assembleBenchmark` to ensure benchmark artifact compilation is validated during CD builds as well.

- [ ] **Step 3: Test and commit CI workflow changes**
  Run: `python3 -c 'import yaml; yaml.safe_load(open(".github/workflows/pr.yml")); yaml.safe_load(open(".github/workflows/main-cd.yml"))'`
  Commit:
  ```bash
  git add .github/workflows/
  git commit -m "ci: enforce Detekt, Kover coverage reports, and assembleBenchmark in PR verification"
  ```

- [ ] **Step 4: Create disposable canary branch & PR**
  Create a temporary branch `test/canary-quality-gate`:
  Introduce an intentional Detekt violation in a Kotlin file (e.g., `private val canaryCodeSmell = 999999` in `MainActivity.kt` with a MagicNumber or UnusedPrivateMember not covered by baseline).
  Push and open a draft PR against `main`.
  Verify in GitHub Actions that CI immediately **FAILS** with error (proving fail-closed gate).
  Close and delete the canary PR and delete the canary branch.

- [ ] **Step 5: Run Phase 3 Final Whole-Branch Review**
  Review full diff `main..HEAD` across all 4 tasks on `Model: pro`.
