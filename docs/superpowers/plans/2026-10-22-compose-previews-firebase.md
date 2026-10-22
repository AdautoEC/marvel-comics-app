# Compose Previews (Skydoves Patterns) & Firebase Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement comprehensive Jetpack Compose Previews according to Skydoves' ProAndroidDev architecture standards (Multi-Previews, PreviewParameterProviders, LocalInspectionMode) across all screens and components, and configure Firebase Crashlytics and App Distribution pipelines.

**Architecture:** 
- Modular design system token and preview annotations (`@ThemePreviews`, `@DevicePreviews`) in `:core:designsystem`.
- Design-time preview resilience in `:core:ui` with `LocalInspectionMode.current` placeholder fallback.
- Strongly-typed `PreviewParameterProvider` implementations in `:core:ui`, `:feature:comics`, and `:feature:detail` covering all visual states (Loading, Populated, Empty, Error, Offline).
- Firebase Gradle configuration with Google Services and Crashlytics plugins resilient to missing local secrets, plus App Distribution step in `.github/workflows/release.yml`.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose 2024.10.01, Compose Tooling Preview, Skydoves UI Guidelines, Firebase BoM 33.5.1, Crashlytics, Firebase App Distribution.

---

## Global Constraints

- Never use hardcoded strings or raw mock data directly inside `@Preview` declarations; always encapsulate via `PreviewParameterProvider`.
- All composables rendered inside previews must use State Hoisting (never accept `ViewModel` instances in previewable content composables).
- Image components (`ComicCover`) must check `LocalInspectionMode.current` to render elegant placeholders in Android Studio without triggering network calls or Coil initialization failures.
- All code changes must strictly adhere to Detekt governance (including `ForbiddenImport`).
- All Gradle tasks (`detekt`, `testDebugUnitTest`, `assembleDebug`, `assembleRelease`) must compile with 0 warnings/errors.

---

## Tasks

### Task 1: Multi-Preview Annotations in `:core:designsystem`
- [ ] Create `core/designsystem/src/main/java/com/adauto/marvel/core/designsystem/preview/ThemePreviews.kt`:
  - Meta-annotation `@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, backgroundColor = 0xFFFFFFFF)`
  - Meta-annotation `@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF0F0F14)`
  - Target: `AnnotationTarget.FUNCTION`
- [ ] Create `core/designsystem/src/main/java/com/adauto/marvel/core/designsystem/preview/DevicePreviews.kt`:
  - Meta-annotation `@Preview(name = "Phone - Portrait", device = Devices.PIXEL_7, showSystemUi = true)`
  - Meta-annotation `@Preview(name = "Phone - Landscape", device = "spec:width=891dp,height=411dp", showSystemUi = true)`
  - Meta-annotation `@Preview(name = "Foldable", device = Devices.FOLDABLE, showSystemUi = true)`
  - Meta-annotation `@Preview(name = "Tablet", device = Devices.PIXEL_TABLET, showSystemUi = true)`
  - Target: `AnnotationTarget.FUNCTION`
- [ ] Create unit test `core/designsystem/src/test/java/com/adauto/marvel/core/designsystem/preview/PreviewAnnotationsTest.kt` verifying annotation retention and target types.
- [ ] Run `./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:detekt`.

### Task 2: Inspection Mode & Preview Providers for Core UI Components
- [ ] Update `core/ui/src/main/java/com/adauto/marvel/core/ui/component/ComicCover.kt`:
  - Add `if (LocalInspectionMode.current)` branch: render a design-time placeholder with `SurfaceCard` background and an centered placeholder vector/initial, completely bypassing Coil `AsyncImage` in Android Studio preview.
- [ ] Create `core/ui/src/main/java/com/adauto/marvel/core/ui/preview/ComicCardPreviewParameterProvider.kt`:
  - Provides sample `ComicSummary` models: standard comic, comic with 3-line long title, comic with missing cover URL.
- [ ] Create `core/ui/src/main/java/com/adauto/marvel/core/ui/preview/ErrorStatePreviewParameterProvider.kt`:
  - Provides sample error scenarios: network timeout, rate limit 429, generic failure.
- [ ] Add `@ThemePreviews` with `@PreviewParameter` to:
  - `ComicCard.kt`
  - `ComicCover.kt`
  - `ErrorState.kt`
  - `OfflineBanner.kt`
  - `AttributionFooter.kt`
  - `Shimmer.kt`
- [ ] Run `./gradlew :core:ui:testDebugUnitTest :core:ui:detekt`.

### Task 3: Feature Comics PreviewParameterProviders & Screen Previews
- [ ] Create `feature/comics/src/main/java/com/adauto/marvel/feature/comics/preview/ComicListUiStatePreviewParameterProvider.kt`:
  - Provides states:
    1. Loading (shimmer placeholder active)
    2. Populated (list of Marvel comics with cover images and attribution footer)
    3. Empty (no comics available)
    4. Offline (isOffline = true, showing OfflineBanner)
    5. Error (network error with retry button)
- [ ] Update `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListScreen.kt`:
  - Add `@ThemePreviews` and `@DevicePreviews` composable preview `ComicListScreenPreview` wrapping `ComicListContent` with `MarvelTheme` and `@PreviewParameter(ComicListUiStatePreviewParameterProvider::class)`.
- [ ] Create unit test verifying `ComicListUiStatePreviewParameterProvider` produces all expected states.
- [ ] Run `./gradlew :feature:comics:testDebugUnitTest :feature:comics:detekt`.

### Task 4: Feature Detail PreviewParameterProviders & Screen Previews
- [ ] Create `feature/detail/src/main/java/com/adauto/marvel/feature/detail/preview/ComicDetailUiStatePreviewParameterProvider.kt`:
  - Provides states:
    1. Loading (shimmer state)
    2. Populated (full comic detail with title, issue #1, $4.99 price, 32 pages, description, creators, characters)
    3. Populated Minimal (comic without description, no creators)
    4. Error (error message with retry action)
- [ ] Update `feature/detail/src/main/java/com/adauto/marvel/feature/detail/ComicDetailScreen.kt`:
  - Add `@ThemePreviews` and `@DevicePreviews` composable preview `ComicDetailScreenPreview` wrapping `ComicDetailContent` with `MarvelTheme` and `@PreviewParameter(ComicDetailUiStatePreviewParameterProvider::class)`.
- [ ] Create unit test verifying `ComicDetailUiStatePreviewParameterProvider` produces all expected states.
- [ ] Run `./gradlew :feature:detail:testDebugUnitTest :feature:detail:detekt`.

### Task 5: Firebase Crashlytics & App Distribution Pipeline Hardening
- [ ] In `app/build.gradle.kts`:
  - Apply `alias(libs.plugins.google.services)` and `alias(libs.plugins.firebase.crashlytics)`.
  - Ensure Google Services and Crashlytics configure cleanly with `ci/google-services.fake.json` during debug/release compilation.
- [ ] In `.github/workflows/release.yml`:
  - Add Firebase App Distribution job/step deploying `app-release.apk` or `app-release.aab` using `FirebaseExtended/action-hosting-deploy` or `wzieba/Firebase-Distribution-Github-Action` when `FIREBASE_APP_ID` and `FIREBASE_CLI_TOKEN` are present.
- [ ] Run `./gradlew detekt lintDebug testDebugUnitTest assembleDebug assembleRelease`.
- [ ] Commit all changes and push `feat/compose-previews-and-firebase-config`.
- [ ] Create PR to `main` with summary and verification evidence.
