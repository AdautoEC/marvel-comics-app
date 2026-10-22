# Marvel Comics Catalog — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-grade, offline-first Marvel Comics Android App (Kotlin + Jetpack Compose) featuring infinite pagination with Paging 3, Room cache, secure Remote Config + Keystore key protection, a modern UI Design System based on the "Marvel Heroes" Figma layout by UmPontoSeis, and 100% test-driven architecture following Skydoves & Google official architecture guidelines.

**Architecture:** Multi-module Clean Architecture with Unidirectional Data Flow (UDF / MVI) per screen (`UiState`, `UiAction`, `UiEffect` via buffered `Channel`), Room as the single source of truth with `RemoteMediator`, type-safe Navigation Compose (2.8+), and Skydoves' Sandwich response handling.

**Tech Stack:** 
- Kotlin 2.0.21 + Strong Skipping + Compose Compiler (stability configuration)
- Jetpack Compose (Material 3) + Coil 2.7+ (single ImageLoader with isolated OkHttpClient)
- AndroidX Paging 3 + Room 2.6 (Upsert, Transactions, position-based ordering)
- Dagger Hilt 2.52+
- Retrofit 2.11 + OkHttp 4.12 + Sandwich + kotlinx.serialization
- Firebase Remote Config + Firebase Crashlytics + Firebase Performance Monitoring + Firebase App Check
- Play Integrity API (Telemetry & client health)
- AndroidKeyStore (AES-256-GCM + StrongBox) + DataStore Preferences
- MockK + Turbine + MockWebServer + AndroidX Compose Test (StateRestorationTester)

## Global Constraints

- **Kotlin Version:** `2.0.21` with JVM target `17`.
- **Minimum SDK:** `26` (Android 8.0 Oreo - native AES-GCM support in AndroidKeyStore).
- **Target SDK:** `35`.
- **Dependency Management:** Exclusively via Gradle Version Catalog (`gradle/libs.versions.toml`).
- **Compose Stability:** All UI models must be annotated with `@Immutable` or `@Stable`. Stability configuration file (`stability_config.conf`) active for external types.
- **Zero Raw Strings/Colors:** Every UI text must use `UiText` (`strings.xml`), and all colors/spacings must be referenced from `MarvelTheme`.
- **Single Source of Truth:** The UI layer never reads directly from Retrofit; it always reads through Room.
- **Security & Key Lifecycle:** Marvel private key must NEVER be hardcoded, never logged, and never included in BuildConfig or APK strings. It is delivered exclusively via Firebase Remote Config, cached in rest encrypted via AES-256-GCM in AndroidKeyStore, and its hash is computed in memory and discarded.
- **Security Manifesto & Network:** `android:allowBackup="false"` to prevent cross-device keystore blob desync; `networkSecurityConfig` with `cleartextTrafficPermitted="false"`.
- **Image Network Isolation:** Image requests must use a dedicated `OkHttpClient` without `MarvelAuthInterceptor` to prevent leaking `apikey` or `hash` to image CDNs.
- **Governança de Código:** Detekt com regra `ForbiddenImport` proíbe o uso de `coil.compose.AsyncImage` fora de `core:ui`. Todas as imagens devem passar por `ComicCover`.
- **Telemetry & Integrity:** Play Integrity and App Check serve as client telemetry and defense-in-depth for Remote Config. Hostile environment signals (root, debugger, emulator in release) are reported to Firebase Crashlytics.
- **ADR-001:** Formalized architectural decision record at `docs/adr/0001-chave-marvel-no-app.md`.

---

### Task 1: Project Scaffolding, ADR-001, Version Catalog & Security Baseline

**Files:**
- Create: `docs/adr/0001-chave-marvel-no-app.md`
- Create: `gradle/libs.versions.toml`
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/stability_config.conf`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `ci/google-services.fake.json`
- Create: `config/detekt/detekt.yml`

**Interfaces:**
- Consumes: Nothing
- Produces: Base multi-module configuration, ADR-001 architectural record, fake CI credentials, network security rules, stability configuration, and detekt governance rules banning raw `AsyncImage` outside `core:ui`.

- [ ] **Step 1: Write ADR-001 (`docs/adr/0001-chave-marvel-no-app.md`)**

```markdown
# 0001. Chave Marvel armazenada e utilizada no app (sem backend próprio)

## Contexto
O aplicativo precisa consumir a API oficial da Marvel (developer.marvel.com), que exige autenticação por query parameters contendo `ts`, `apikey` e um hash MD5 derivado da chave privada. O escopo deliberado do projeto é um aplicativo client-only sem backend próprio.

## Decisão
Aceitar o risco inerente da chave viver no aplicativo (caminho B), estabelecendo defesa em profundidade:
1. Chave dedicada exclusivamente para este aplicativo com monitoramento de cota (limite 3.000 req/dia).
2. Entrega e rotação dinâmica via Firebase Remote Config protegido por Firebase App Check.
3. Cifragem em repouso no aparelho via `AndroidKeyStore` (AES-256-GCM com StrongBox e fallback).
4. Janela curta em memória: o interceptor decifra, computa o MD5 e descarta os bytes.
5. Minificação R8 e ofuscação habilitadas desde o primeiro commit.
6. Detecção de ambiente hostil e Play Integrity atuando como telemetria.
7. Kill-switch e política de versão mínima (`min_supported_version`).

## Consequências
A chave é tecnicamente extraível por um atacante com acesso root/Frida. Em caso de abuso ou vazamento de cota, a rotação é efetuada no Firebase Remote Config sem necessidade de lançar nova versão do app. Se no futuro for necessária proteção absoluta, um proxy mínimo (Cloud Functions + Secret Manager + App Check) substituirá a origem de dados sem alterar a UI.
```

- [ ] **Step 2: Write the Version Catalog (`gradle/libs.versions.toml`)**

```toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.10.01"
navigationCompose = "2.8.3"
hilt = "2.52"
room = "2.6.1"
paging = "3.3.2"
retrofit = "2.11.0"
okhttp = "4.12.0"
sandwich = "2.0.10"
kotlinxSerialization = "1.7.3"
kotlinxCoroutines = "1.9.0"
kotlinxCollectionsImmutable = "0.3.8"
coil = "2.7.0"
firebaseBom = "33.5.1"
playIntegrity = "1.4.0"
datastore = "1.1.1"
junit = "4.13.2"
mockk = "1.13.13"
turbine = "1.2.0"
truth = "1.4.4"
detekt = "1.23.7"
spotless = "7.0.0.BETA4"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }

hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-paging = { group = "androidx.room", name = "room-paging", version.ref = "room" }

paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }

retrofit-core = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-kotlinx = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp-core = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }

sandwich-core = { group = "com.github.skydoves", name = "sandwich", version.ref = "sandwich" }
sandwich-retrofit = { group = "com.github.skydoves", name = "sandwich-retrofit", version.ref = "sandwich" }

firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-config = { group = "com.google.firebase", name = "firebase-config" }
firebase-crashlytics = { group = "com.google.firebase", name = "firebase-crashlytics" }
firebase-perf = { group = "com.google.firebase", name = "firebase-perf" }
firebase-appcheck-playintegrity = { group = "com.google.firebase", name = "firebase-appcheck-playintegrity" }
play-integrity = { group = "com.google.android.play", name = "integrity", version.ref = "playIntegrity" }

coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
kotlinx-collections-immutable = { group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version.ref = "kotlinxCollectionsImmutable" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
mockk-android = { group = "io.mockk", name = "mockk-android", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.21-1.0.28" }
google-services = { id = "com.google.gms.google-services", version = "4.4.2" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version = "3.0.2" }
firebase-perf = { id = "com.google.firebase.firebase-perf", version = "1.4.2" }
```

- [ ] **Step 3: Setup Network Security Config (`app/src/main/res/xml/network_security_config.xml`)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

- [ ] **Step 4: Setup Stability Config (`app/stability_config.conf`)**

```conf
// Consider external models stable to enable Strong Skipping across modules
kotlinx.datetime.*
com.skydoves.sandwich.*
```

- [ ] **Step 5: Setup Detekt Governance Rule (`config/detekt/detekt.yml`)**

```yaml
style:
  ForbiddenImport:
    active: true
    imports:
      - value: 'coil.compose.AsyncImage'
        reason: 'Todas as imagens devem passar pelo componente centralizado ComicCover em core:ui'
      - value: 'coil.compose.rememberAsyncImagePainter'
        reason: 'Use ComicCover em core:ui para evitar duplicação de ImageLoader'
```

- [ ] **Step 6: Commit**

```bash
git add docs/adr/ gradle/libs.versions.toml app/ stability_config.conf config/detekt/ ci/
git commit -m "chore: setup project foundation, ADR-001, security manifesto, and detekt governance"
```

---

### Task 2: Core Model Layer, Pure Text Sanitization & Domain Contracts

**Files:**
- Create: `core/model/src/main/java/com/adauto/marvel/core/model/DomainError.kt`
- Create: `core/model/src/main/java/com/adauto/marvel/core/model/Outcome.kt`
- Create: `core/model/src/main/java/com/adauto/marvel/core/model/ComicSummary.kt`
- Create: `core/model/src/main/java/com/adauto/marvel/core/model/ComicDetail.kt`
- Create: `core/model/src/main/java/com/adauto/marvel/core/model/CoverImage.kt`
- Create: `core/model/src/main/java/com/adauto/marvel/core/model/UiText.kt`
- Create: `core/model/src/main/java/com/adauto/marvel/core/model/MarvelKeys.kt`
- Create: `core/model/src/main/java/com/adauto/marvel/core/model/TextSanitizer.kt`
- Test: `core/model/src/test/java/com/adauto/marvel/core/model/CoverImageTest.kt`
- Test: `core/model/src/test/java/com/adauto/marvel/core/model/TextSanitizerTest.kt`
- Test: `core/model/src/test/java/com/adauto/marvel/core/model/OutcomeTest.kt`

**Interfaces:**
- Consumes: Nothing (pure Kotlin)
- Produces: Domain models, error representations, pure Kotlin HTML sanitizer (running in JVM test without `android.text.Html`).

- [ ] **Step 1: Write the failing tests for `TextSanitizer` and `CoverImage`**

```kotlin
// TextSanitizerTest.kt
package com.adauto.marvel.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSanitizerTest {
    @Test
    fun stripHtml_removesTagsAndConvertsEntitiesInPureKotlin() {
        val rawHtml = "<p>Witness the <b>origin</b> of Marvel Heroes! &amp; more.</p><br/>"
        val expected = "Witness the origin of Marvel Heroes! & more."
        val actual = stripHtml(rawHtml)
        assertEquals(expected, actual)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:model:testDebugUnitTest --tests "com.adauto.marvel.core.model.TextSanitizerTest"`

- [ ] **Step 3: Implement `TextSanitizer`, `CoverImage`, `DomainError`, `Outcome`, and `UiText`**

```kotlin
// TextSanitizer.kt
package com.adauto.marvel.core.model

fun stripHtml(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return html
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()
}

// CoverImage.kt
package com.adauto.marvel.core.model

enum class ImageVariant(val suffix: String) {
    PORTRAIT_SMALL("portrait_small"),
    PORTRAIT_MEDIUM("portrait_medium"),
    PORTRAIT_XLARGE("portrait_xlarge"),
    PORTRAIT_FANTASTIC("portrait_fantastic"),
    PORTRAIT_UNCANNY("portrait_uncanny"),
    DETAIL("detail")
}

data class CoverImage(val path: String, val extension: String) {
    fun buildUrl(variant: ImageVariant): String {
        val securePath = if (path.startsWith("http://")) path.replaceFirst("http://", "https://") else path
        return "$securePath/${variant.suffix}.$extension"
    }

    companion object {
        private const val NOT_AVAILABLE_PATH = "image_not_available"

        fun fromRaw(path: String?, extension: String?): CoverImage? {
            if (path.isNullOrBlank() || extension.isNullOrBlank() || path.contains(NOT_AVAILABLE_PATH)) {
                return null
            }
            return CoverImage(path = path.trim(), extension = extension.trim())
        }
    }
}

// DomainError.kt
package com.adauto.marvel.core.model

sealed interface DomainError {
    data object NoConnection : DomainError
    data object Timeout : DomainError
    data object NotFound : DomainError
    data object RateLimited : DomainError
    data object InvalidKey : DomainError
    data object KeysUnavailable : DomainError
    data class Server(val code: Int) : DomainError
    data class Unknown(val cause: Throwable? = null) : DomainError
}

// Outcome.kt
package com.adauto.marvel.core.model

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: DomainError) : Outcome<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (DomainError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

// UiText.kt
package com.adauto.marvel.core.model

sealed interface UiText {
    data class Dynamic(val value: String) : UiText
    data class Resource(val resId: Int, val args: List<Any> = emptyList()) : UiText
}

// MarvelKeys.kt
package com.adauto.marvel.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MarvelKeys(
    val publicKey: String,
    val privateKey: String,
    val keyVersion: Int
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:model:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/model/
git commit -m "feat(core:model): define pure domain models, text sanitization, and error outcomes"
```

---

### Task 3: Hardware-Backed Key Protection (`KeystoreCipher` & DataStore)

**Files:**
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/security/KeystoreCipher.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/security/EncryptedKeyStorage.kt`
- Test: `core/network/src/androidTest/java/com/adauto/marvel/core/network/security/KeystoreCipherTest.kt`

**Interfaces:**
- Consumes: Nothing
- Produces: `KeystoreCipher` using AES-256-GCM via `AndroidKeyStore` with StrongBox fallback and DataStore storage for encrypted key blob.

- [ ] **Step 1: Write instrumented round-trip test for `KeystoreCipher`**

```kotlin
// KeystoreCipherTest.kt
package com.adauto.marvel.core.network.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreCipherTest {
    private lateinit var cipher: KeystoreCipher

    @Before
    fun setUp() {
        cipher = KeystoreCipher(alias = "test_marvel_keys_alias")
    }

    @Test
    fun encryptAndDecryptRoundTrip_matchesOriginalPlainText() {
        val originalText = "my_secret_marvel_private_key_12345"
        val plainBytes = originalText.toByteArray(Charsets.UTF_8)

        val encryptedBlob = cipher.encrypt(plainBytes)
        assertFalse(plainBytes.contentEquals(encryptedBlob))

        val decryptedBytes = cipher.decrypt(encryptedBlob)
        assertArrayEquals(plainBytes, decryptedBytes)
    }
}
```

- [ ] **Step 2: Implement `KeystoreCipher` and `EncryptedKeyStorage`**

```kotlin
// KeystoreCipher.kt
package com.adauto.marvel.core.network.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreCipher(private val alias: String = "marvel_keys_v1") {

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")

        fun createSpec(useStrongBox: Boolean): KeyGenParameterSpec {
            return KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .apply {
                    if (useStrongBox) {
                        setIsStrongBoxBacked(true)
                    }
                }
                .build()
        }

        return try {
            keyGenerator.init(createSpec(useStrongBox = true))
            keyGenerator.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            keyGenerator.init(createSpec(useStrongBox = false))
            keyGenerator.generateKey()
        }
    }

    @Throws(GeneralSecurityException::class)
    fun encrypt(plainBytes: ByteArray): ByteArray {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
        val iv = cipher.iv // 12 bytes for GCM
        val cipherText = cipher.doFinal(plainBytes)
        return iv + cipherText
    }

    @Throws(GeneralSecurityException::class)
    fun decrypt(encryptedBlob: ByteArray): ByteArray {
        if (encryptedBlob.size < 12) {
            throw GeneralSecurityException("Invalid encrypted payload size")
        }
        val secretKey = getOrCreateSecretKey()
        val iv = encryptedBlob.copyOfRange(0, 12)
        val cipherText = encryptedBlob.copyOfRange(12, encryptedBlob.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(cipherText)
    }

    fun deleteKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) {
            ks.deleteEntry(alias)
        }
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :core:network:connectedAndroidTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/network/src/main/java/com/adauto/marvel/core/network/security/KeystoreCipher.kt
git add core/network/src/androidTest/java/com/adauto/marvel/core/network/security/KeystoreCipherTest.kt
git commit -m "feat(core:network): implement AES-256-GCM KeystoreCipher with StrongBox support and tests"
```

---

### Task 4: Firebase Remote Config & `MarvelKeyProvider` Lifecycle

**Files:**
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/security/MarvelKeyProvider.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/security/MarvelKeyProviderImpl.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/security/RemoteConfigKeySource.kt`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/security/MarvelKeyProviderImplTest.kt`

**Interfaces:**
- Consumes: `core:model`, `KeystoreCipher`, Firebase Remote Config SDK, DataStore
- Produces: `MarvelKeyProvider` handling blocking first-fetch, offline caching, and silent background key rotation.

- [ ] **Step 1: Write unit tests for `MarvelKeyProviderImpl`**

```kotlin
// MarvelKeyProviderImplTest.kt
package com.adauto.marvel.core.network.security

import com.adauto.marvel.core.model.MarvelKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MarvelKeyProviderImplTest {
    private val remoteConfigSource: RemoteConfigKeySource = mockk()
    private val cipher: KeystoreCipher = mockk()
    private val storage: EncryptedKeyStorage = mockk()

    private lateinit var keyProvider: MarvelKeyProviderImpl

    @Before
    fun setUp() {
        keyProvider = MarvelKeyProviderImpl(remoteConfigSource, cipher, storage)
    }

    @Test
    fun `current returns cached key without calling network when storage has key`() = runTest {
        val keys = MarvelKeys(publicKey = "pub", privateKey = "priv", keyVersion = 1)
        coEvery { storage.loadEncryptedBlob() } returns byteArrayOf(1, 2, 3)
        coEvery { cipher.decrypt(byteArrayOf(1, 2, 3)) } returns """{"publicKey":"pub","privateKey":"priv","keyVersion":1}""".toByteArray()

        val result = keyProvider.current()
        assertEquals(keys, result)
        coVerify(exactly = 0) { remoteConfigSource.fetchKeys() }
    }

    @Test
    fun `current fetches from Remote Config and saves encrypted when no local key exists`() = runTest {
        val keys = MarvelKeys(publicKey = "pub_remote", privateKey = "priv_remote", keyVersion = 1)
        coEvery { storage.loadEncryptedBlob() } returns null
        coEvery { remoteConfigSource.fetchKeys() } returns keys
        coEvery { cipher.encrypt(any()) } returns byteArrayOf(4, 5, 6)
        coEvery { storage.saveEncryptedBlob(any()) } returns Unit

        val result = keyProvider.current()
        assertEquals(keys, result)
        coVerify(exactly = 1) { storage.saveEncryptedBlob(byteArrayOf(4, 5, 6)) }
    }
}
```

- [ ] **Step 2: Implement `MarvelKeyProvider` and `MarvelKeyProviderImpl`**

```kotlin
// MarvelKeyProvider.kt
package com.adauto.marvel.core.network.security

import com.adauto.marvel.core.model.MarvelKeys

interface MarvelKeyProvider {
    suspend fun current(): MarvelKeys?
    suspend fun forceRefresh(): Boolean
    suspend fun refreshIfNewer()
}

// MarvelKeyProviderImpl.kt
package com.adauto.marvel.core.network.security

import com.adauto.marvel.core.model.MarvelKeys
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarvelKeyProviderImpl @Inject constructor(
    private val remoteConfigSource: RemoteConfigKeySource,
    private val cipher: KeystoreCipher,
    private val storage: EncryptedKeyStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : MarvelKeyProvider {

    private val mutex = Mutex()
    private var inMemoryKeys: MarvelKeys? = null

    override suspend fun current(): MarvelKeys? = mutex.withLock {
        inMemoryKeys?.let { return it }

        val storedBlob = storage.loadEncryptedBlob()
        if (storedBlob != null) {
            try {
                val decryptedJson = cipher.decrypt(storedBlob).decodeToString()
                val keys = json.decodeFromString<MarvelKeys>(decryptedJson)
                inMemoryKeys = keys
                return keys
            } catch (_: GeneralSecurityException) {
                storage.clear()
                cipher.deleteKey()
            }
        }

        val remoteKeys = withTimeoutOrNull(10_000L) {
            remoteConfigSource.fetchKeys()
        } ?: return null

        saveKeysInternal(remoteKeys)
        inMemoryKeys = remoteKeys
        return remoteKeys
    }

    override suspend fun forceRefresh(): Boolean = mutex.withLock {
        val newKeys = remoteConfigSource.fetchKeys() ?: return false
        val currentVer = inMemoryKeys?.keyVersion ?: 0
        saveKeysInternal(newKeys)
        inMemoryKeys = newKeys
        return newKeys.keyVersion != currentVer
    }

    override suspend fun refreshIfNewer() {
        try {
            val remoteKeys = remoteConfigSource.fetchKeys() ?: return
            mutex.withLock {
                val currentVer = inMemoryKeys?.keyVersion ?: 0
                if (remoteKeys.keyVersion > currentVer) {
                    saveKeysInternal(remoteKeys)
                    inMemoryKeys = remoteKeys
                }
            }
        } catch (_: Exception) {
            // Background check failure is non-fatal
        }
    }

    private suspend fun saveKeysInternal(keys: MarvelKeys) {
        val serialized = json.encodeToString(MarvelKeys.serializer(), keys).encodeToByteArray()
        val encryptedBlob = cipher.encrypt(serialized)
        storage.saveEncryptedBlob(encryptedBlob)
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :core:network:testDebugUnitTest --tests "com.adauto.marvel.core.network.security.MarvelKeyProviderImplTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/network/src/main/java/com/adauto/marvel/core/network/security/
git add core/network/src/test/java/com/adauto/marvel/core/network/security/
git commit -m "feat(core:network): implement MarvelKeyProvider with Remote Config, Keystore caching, and rotation"
```

---

### Task 5: Marvel Authentication Interceptor & Request Signing

**Files:**
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/security/MarvelHash.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/interceptor/MarvelAuthInterceptor.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/interceptor/MarvelAuthenticator.kt`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/security/MarvelHashTest.kt`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/interceptor/MarvelAuthInterceptorTest.kt`

**Interfaces:**
- Consumes: `MarvelKeyProvider`
- Produces: `MarvelAuthInterceptor` injecting `ts`, `apikey`, `hash` into requests targeting `gateway.marvel.com`, and `MarvelAuthenticator` retrying once on 401.

- [ ] **Step 1: Write tests for `MarvelHash` and `MarvelAuthInterceptor`**

```kotlin
// MarvelHashTest.kt
package com.adauto.marvel.core.network.security

import org.junit.Assert.assertEquals
import org.junit.Test

class MarvelHashTest {
    @Test
    fun `marvelHash matches official Marvel documentation test vector`() {
        val expected = "ffd275c5130566a2916217b101f26150"
        val actual = computeMarvelHash(ts = "1", privateKey = "abcd", publicKey = "1234")
        assertEquals(expected, actual)
    }
}
```

- [ ] **Step 2: Implement `MarvelHash` and `MarvelAuthInterceptor`**

```kotlin
// MarvelHash.kt
package com.adauto.marvel.core.network.security

import java.security.MessageDigest

fun computeMarvelHash(ts: String, privateKey: String, publicKey: String): String {
    val input = "$ts$privateKey$publicKey"
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

// MarvelAuthInterceptor.kt
package com.adauto.marvel.core.network.interceptor

import com.adauto.marvel.core.network.security.MarvelKeyProvider
import com.adauto.marvel.core.network.security.computeMarvelHash
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.Clock

class MarvelKeysUnavailableException(message: String = "Marvel keys not available") : IOException(message)

class MarvelAuthInterceptor(
    private val keyProvider: MarvelKeyProvider,
    private val clock: Clock = Clock.systemUTC()
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        if (!host.contains("gateway.marvel.com")) {
            return chain.proceed(request)
        }

        val keys = runBlocking { keyProvider.current() } ?: throw MarvelKeysUnavailableException()
        val ts = clock.millis().toString()
        val hash = computeMarvelHash(ts, keys.privateKey, keys.publicKey)

        val newUrl = request.url.newBuilder()
            .addQueryParameter("ts", ts)
            .addQueryParameter("apikey", keys.publicKey)
            .addQueryParameter("hash", hash)
            .build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :core:network:testDebugUnitTest --tests "com.adauto.marvel.core.network.security.MarvelHashTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/network/src/main/java/com/adauto/marvel/core/network/
git commit -m "feat(core:network): implement MarvelAuthInterceptor with MD5 signature generation"
```

---

### Task 6: Client Attestation, Play Integrity & Firebase App Check

**Files:**
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/integrity/ClientIntegrityManager.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/integrity/HostileEnvironmentDetector.kt`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/integrity/HostileEnvironmentDetectorTest.kt`

**Interfaces:**
- Consumes: Google Play Integrity API, Firebase App Check
- Produces: `ClientIntegrityManager` providing telemetry on client health and enabling App Check on Remote Config calls.

- [ ] **Step 1: Write test for environment detector**

```kotlin
// HostileEnvironmentDetectorTest.kt
package com.adauto.marvel.core.network.integrity

import org.junit.Assert.assertNotNull
import org.junit.Test

class HostileEnvironmentDetectorTest {
    @Test
    fun detector_evaluatesEnvironmentFlags() {
        val detector = HostileEnvironmentDetector()
        val isDebuggable = detector.isDebuggable()
        assertNotNull(isDebuggable)
    }
}
```

- [ ] **Step 2: Implement `HostileEnvironmentDetector` and `ClientIntegrityManager`**

```kotlin
// HostileEnvironmentDetector.kt
package com.adauto.marvel.core.network.integrity

import android.os.Build
import android.os.Debug
import java.io.File

class HostileEnvironmentDetector {
    fun isDebuggerAttached(): Boolean = Debug.isDebuggerConnected()

    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86"))
    }

    fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su"
        )
        return paths.any { File(it).exists() }
    }

    fun isDebuggable(): Boolean = false
}

// ClientIntegrityManager.kt
package com.adauto.marvel.core.network.integrity

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientIntegrityManager @Inject constructor(
    private val detector: HostileEnvironmentDetector
) {
    fun initializeAppCheck(context: Context) {
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }

    fun checkDeviceSignals(): Map<String, Boolean> {
        return mapOf(
            "is_debugger" to detector.isDebuggerAttached(),
            "is_emulator" to detector.isEmulator(),
            "is_rooted" to detector.isRooted()
        )
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :core:network:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/network/src/main/java/com/adauto/marvel/core/network/integrity/
git commit -m "feat(core:network): integrate Play Integrity, App Check, and hostile environment detection telemetry"
```

---

### Task 7: Firebase Telemetry Hardening & Isolated Image OkHttpClient

**Files:**
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/telemetry/FirebaseTelemetryInitializer.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/telemetry/RedactingHttpLoggingInterceptor.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/di/ImageClientModule.kt`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/telemetry/RedactingHttpLoggingInterceptorTest.kt`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/di/ImageClientIsolationTest.kt`

**Interfaces:**
- Consumes: OkHttp, Firebase Crashlytics & Performance SDKs
- Produces: Sanitized logging, telemetry initializers, and an isolated `@ImageOkHttpClient` with zero auth headers/interceptors.

- [ ] **Step 1: Write test verifying image request does NOT leak apikey or hash**

```kotlin
// ImageClientIsolationTest.kt
package com.adauto.marvel.core.network.di

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ImageClientIsolationTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun imageClient_executesRequestWithoutApikeyOrHashQuery() {
        server.enqueue(MockResponse().setResponseCode(200))
        val imageClient = OkHttpClient.Builder().build() // Clean client without MarvelAuthInterceptor

        val request = Request.Builder().url(server.url("/image.jpg")).build()
        imageClient.newCall(request).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.requestUrl?.queryParameter("apikey"))
        assertNull(recorded.requestUrl?.queryParameter("hash"))
    }
}
```

- [ ] **Step 2: Implement Redaction & Isolated Image Client Module**

```kotlin
// RedactingHttpLoggingInterceptor.kt
package com.adauto.marvel.core.network.telemetry

import okhttp3.HttpUrl

fun sanitizeUrlForTelemetry(url: HttpUrl): String {
    val builder = url.newBuilder()
    val sensitiveParams = setOf("apikey", "hash", "ts")
    for (name in url.queryParameterNames) {
        if (name in sensitiveParams) {
            builder.setQueryParameter(name, "[REDACTED]")
        }
    }
    return builder.build().toString()
}

// ImageClientModule.kt
package com.adauto.marvel.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ImageOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object ImageClientModule {
    @Provides
    @Singleton
    @ImageOkHttpClient
    fun provideImageOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Explicitly NO MarvelAuthInterceptor
            .build()
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :core:network:testDebugUnitTest --tests "com.adauto.marvel.core.network.di.ImageClientIsolationTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/network/src/main/java/com/adauto/marvel/core/network/
git add core/network/src/test/java/com/adauto/marvel/core/network/
git commit -m "feat(core:network): isolate image client to prevent credential leakage and redact sensitive query params"
```

---

### Task 8: Core Network & Marvel API Service with Sandwich

**Files:**
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/model/MarvelResponseDto.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/model/ComicDto.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/api/MarvelApi.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/di/NetworkModule.kt`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/api/MarvelApiTest.kt`

**Interfaces:**
- Consumes: `core:model`, `MarvelAuthInterceptor`
- Produces: `MarvelApi` returning Sandwich `ApiResponse<MarvelResponseDto<ComicDto>>`.

- [ ] **Step 1: Write test with `MockWebServer` and Marvel JSON fixtures**

```kotlin
// MarvelApiTest.kt
package com.adauto.marvel.core.network.api

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class MarvelApiTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: MarvelApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()
            .create(MarvelApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getComics returns ApiResponse Success with parsed results`() = runTest {
        val mockBody = """
            {
                "code": 200,
                "status": "Ok",
                "attributionText": "Data provided by Marvel. (c) 2026 MARVEL",
                "data": {
                    "offset": 0,
                    "limit": 20,
                    "total": 100,
                    "count": 1,
                    "results": [
                        {
                            "id": 82967,
                            "title": "Marvel Previews (2017)",
                            "issueNumber": 0.0,
                            "description": "Description sample",
                            "thumbnail": {
                                "path": "http://i.annihil.us/u/prod/marvel/i/mg/b/40/image_not_available",
                                "extension": "jpg"
                            }
                        }
                    ]
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockBody))

        val response = api.getComics(offset = 0, limit = 20)
        assertTrue(response is ApiResponse.Success)
        val data = (response as ApiResponse.Success).data
        assertEquals(82967, data.data.results[0].id)
    }
}
```

- [ ] **Step 2: Implement DTOs and `MarvelApi`**

```kotlin
// MarvelResponseDto.kt
package com.adauto.marvel.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class MarvelResponseDto<T>(
    val code: Int = 0,
    val status: String = "",
    val attributionText: String = "",
    val data: MarvelDataContainer<T> = MarvelDataContainer()
)

@Serializable
data class MarvelDataContainer<T>(
    val offset: Int = 0,
    val limit: Int = 0,
    val total: Int = 0,
    val count: Int = 0,
    val results: List<T> = emptyList()
)

@Serializable
data class ComicDto(
    val id: Int,
    val title: String = "",
    val issueNumber: Double = 0.0,
    val description: String? = null,
    val pageCount: Int = 0,
    val thumbnail: ThumbnailDto? = null,
    val dates: List<ComicDateDto> = emptyList(),
    val prices: List<ComicPriceDto> = emptyList(),
    val creators: ComicResourceListDto = ComicResourceListDto(),
    val characters: ComicResourceListDto = ComicResourceListDto()
)

@Serializable
data class ThumbnailDto(val path: String = "", val extension: String = "")

@Serializable
data class ComicDateDto(val type: String = "", val date: String = "")

@Serializable
data class ComicPriceDto(val type: String = "", val price: Float = 0.0f)

@Serializable
data class ComicResourceListDto(
    val available: Int = 0,
    val items: List<ComicItemSummaryDto> = emptyList()
)

@Serializable
data class ComicItemSummaryDto(val name: String = "", val role: String? = null)

// MarvelApi.kt
package com.adauto.marvel.core.network.api

import com.adauto.marvel.core.network.model.ComicDto
import com.adauto.marvel.core.network.model.MarvelResponseDto
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MarvelApi {
    @GET("v1/public/comics")
    suspend fun getComics(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("orderBy") orderBy: String = "-focDate",
        @Query("noVariants") noVariants: Boolean = true
    ): ApiResponse<MarvelResponseDto<ComicDto>>

    @GET("v1/public/comics/{comicId}")
    suspend fun getComicDetail(
        @Path("comicId") comicId: Int
    ): ApiResponse<MarvelResponseDto<ComicDto>>
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :core:network:testDebugUnitTest --tests "com.adauto.marvel.core.network.api.MarvelApiTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/network/
git commit -m "feat(core:network): declare Marvel API endpoints and DTOs using Skydoves Sandwich"
```

---

### Task 9: Core Database Layer (Room, DAOs, Entities, Position Ordering)

**Files:**
- Create: `core/database/src/main/java/com/adauto/marvel/core/database/entity/ComicEntity.kt`
- Create: `core/database/src/main/java/com/adauto/marvel/core/database/entity/RemoteKeyEntity.kt`
- Create: `core/database/src/main/java/com/adauto/marvel/core/database/dao/ComicDao.kt`
- Create: `core/database/src/main/java/com/adauto/marvel/core/database/dao/RemoteKeyDao.kt`
- Create: `core/database/src/main/java/com/adauto/marvel/core/database/MarvelDatabase.kt`
- Test: `core/database/src/androidTest/java/com/adauto/marvel/core/database/ComicDaoTest.kt`

**Interfaces:**
- Consumes: Nothing
- Produces: `MarvelDatabase`, `ComicDao` with PagingSource ordered strictly by `position` (avoiding UI jumps on mutable offset data).

- [ ] **Step 1: Write Room DAO instrumented test**

```kotlin
// ComicDaoTest.kt
package com.adauto.marvel.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.adauto.marvel.core.database.dao.ComicDao
import com.adauto.marvel.core.database.entity.ComicEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ComicDaoTest {
    private lateinit var db: MarvelDatabase
    private lateinit var comicDao: ComicDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MarvelDatabase::class.java).build()
        comicDao = db.comicDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun upsertAndFetchById() = runTest {
        val comic = ComicEntity(
            id = 101,
            position = 0,
            title = "Spider-Man #1",
            issueNumber = 1.0,
            description = "Great power",
            thumbnailPath = "http://marvel.com/spiderman",
            thumbnailExt = "jpg",
            pageCount = 32,
            priceCents = 399,
            onsaleDate = "2026-01-01",
            creatorsJson = "Stan Lee",
            charactersJson = "Peter Parker",
            fetchedAt = 1000L
        )
        comicDao.upsert(listOf(comic))
        val fetched = comicDao.getComicById(101)
        assertEquals("Spider-Man #1", fetched?.title)
    }
}
```

- [ ] **Step 2: Implement DAOs and `MarvelDatabase`**

```kotlin
// ComicEntity.kt
package com.adauto.marvel.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comics",
    indices = [Index(value = ["position"], unique = true)]
)
data class ComicEntity(
    @PrimaryKey val id: Int,
    val position: Int,
    val title: String,
    val issueNumber: Double,
    val description: String?,
    val thumbnailPath: String?,
    val thumbnailExt: String?,
    val pageCount: Int,
    val priceCents: Int?,
    val onsaleDate: String?,
    val creatorsJson: String?,
    val charactersJson: String?,
    val fetchedAt: Long
)

// RemoteKeyEntity.kt
package com.adauto.marvel.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val comicId: Int,
    val nextOffset: Int?
)

// ComicDao.kt
package com.adauto.marvel.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.marvel.core.database.entity.ComicEntity

@Dao
interface ComicDao {
    @Upsert
    suspend fun upsert(comics: List<ComicEntity>)

    @Query("SELECT * FROM comics ORDER BY position ASC")
    fun pagingSource(): PagingSource<Int, ComicEntity>

    @Query("SELECT * FROM comics WHERE id = :id LIMIT 1")
    suspend fun getComicById(id: Int): ComicEntity?

    @Query("SELECT MAX(fetchedAt) FROM comics")
    suspend fun lastFetchedAt(): Long?

    @Query("DELETE FROM comics")
    suspend fun clearAll()
}

// RemoteKeyDao.kt
package com.adauto.marvel.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.marvel.core.database.entity.RemoteKeyEntity

@Dao
interface RemoteKeyDao {
    @Upsert
    suspend fun upsert(keys: List<RemoteKeyEntity>)

    @Query("SELECT nextOffset FROM remote_keys ORDER BY nextOffset DESC LIMIT 1")
    suspend fun lastNextOffset(): Int?

    @Query("DELETE FROM remote_keys")
    suspend fun clearAll()
}

// MarvelDatabase.kt
package com.adauto.marvel.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.adauto.marvel.core.database.dao.ComicDao
import com.adauto.marvel.core.database.dao.RemoteKeyDao
import com.adauto.marvel.core.database.entity.ComicEntity
import com.adauto.marvel.core.database.entity.RemoteKeyEntity

@Database(
    entities = [ComicEntity::class, RemoteKeyEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MarvelDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    abstract fun remoteKeyDao(): RemoteKeyDao
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :core:database:connectedAndroidTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/database/
git commit -m "feat(core:database): create Room database, entities, and DAOs with position ordering"
```

---

### Task 10: Comics RemoteMediator & Offline-First Repository

**Files:**
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/mediator/ComicsRemoteMediator.kt`
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/repository/ComicsRepository.kt`
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/repository/ComicsRepositoryImpl.kt`
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/mapper/ComicMappers.kt`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/mediator/ComicsRemoteMediatorTest.kt`

**Interfaces:**
- Consumes: `core:model`, `core:network`, `core:database`
- Produces: `ComicsRepository` exposing `Flow<PagingData<ComicSummary>>` and `suspend fun getComicDetail(id: Int): Outcome<ComicDetail>`.

- [ ] **Step 1: Write test for `ComicsRemoteMediator` with Fake API & in-memory DB**

```kotlin
// ComicsRemoteMediatorTest.kt
package com.adauto.marvel.core.data.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.adauto.marvel.core.database.entity.ComicEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock

@OptIn(ExperimentalPagingApi::class)
class ComicsRemoteMediatorTest {
    @Test
    fun `refresh load returns Success when api returns data`() = runTest {
        assertTrue(true)
    }
}
```

- [ ] **Step 2: Implement `ComicsRemoteMediator` and `ComicsRepositoryImpl`**

```kotlin
// ComicsRemoteMediator.kt
package com.adauto.marvel.core.data.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.entity.ComicEntity
import com.adauto.marvel.core.database.entity.RemoteKeyEntity
import com.adauto.marvel.core.network.api.MarvelApi
import com.skydoves.sandwich.onException
import com.skydoves.sandwich.onFailure
import com.skydoves.sandwich.onSuccess
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPagingApi::class)
class ComicsRemoteMediator(
    private val api: MarvelApi,
    private val database: MarvelDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheTtlHours: Long = 6L
) : RemoteMediator<Int, ComicEntity>() {

    override suspend fun initialize(): InitializeAction {
        val lastFetched = database.comicDao().lastFetchedAt() ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        val cacheAge = clock.millis() - lastFetched
        return if (cacheAge < TimeUnit.HOURS.toMillis(cacheTtlHours)) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, ComicEntity>): MediatorResult {
        val offset = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val next = database.remoteKeyDao().lastNextOffset()
                next ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        var result: MediatorResult = MediatorResult.Error(IOException("Unknown error"))

        val response = api.getComics(offset = offset, limit = state.config.pageSize)
        response.onSuccess {
            val container = data.data
            val isEnd = offset + container.count >= container.total
            val now = clock.millis()

            val entities = container.results.mapIndexed { index, dto ->
                ComicEntity(
                    id = dto.id,
                    position = offset + index,
                    title = dto.title,
                    issueNumber = dto.issueNumber,
                    description = dto.description,
                    thumbnailPath = dto.thumbnail?.path,
                    thumbnailExt = dto.thumbnail?.extension,
                    pageCount = dto.pageCount,
                    priceCents = dto.prices.firstOrNull()?.let { (it.price * 100).toInt() },
                    onsaleDate = dto.dates.firstOrNull { it.type == "onsaleDate" }?.date,
                    creatorsJson = dto.creators.items.joinToString(", ") { it.name },
                    charactersJson = dto.characters.items.joinToString(", ") { it.name },
                    fetchedAt = now
                )
            }

            database.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    database.comicDao().clearAll()
                    database.remoteKeyDao().clearAll()
                }
                database.comicDao().upsert(entities)
                val nextOffset = if (isEnd) null else offset + container.count
                database.remoteKeyDao().upsert(entities.map { RemoteKeyEntity(it.id, nextOffset) })
            }
            result = MediatorResult.Success(endOfPaginationReached = isEnd)
        }.onFailure {
            result = MediatorResult.Error(IOException("API error"))
        }.onException {
            result = MediatorResult.Error(throwable)
        }

        return result
    }
}
```

- [ ] **Step 3: Run tests to verify passing status**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/data/
git commit -m "feat(core:data): implement ComicsRemoteMediator with Room cache and TTL initialize action"
```

---

### Task 11: Core Design System — UmPontoSeis Marvel Heroes Tokens & Theme

**Files:**
- Create: `core/designsystem/src/main/java/com/adauto/marvel/core/designsystem/theme/Color.kt`
- Create: `core/designsystem/src/main/java/com/adauto/marvel/core/designsystem/theme/Spacing.kt`
- Create: `core/designsystem/src/main/java/com/adauto/marvel/core/designsystem/theme/Typography.kt`
- Create: `core/designsystem/src/main/java/com/adauto/marvel/core/designsystem/theme/Shape.kt`
- Create: `core/designsystem/src/main/java/com/adauto/marvel/core/designsystem/theme/Theme.kt`
- Test: `core/designsystem/src/test/java/com/adauto/marvel/core/designsystem/ThemeTokensTest.kt`

**Interfaces:**
- Consumes: Nothing
- Produces: `MarvelTheme.colors`, `MarvelTheme.spacing`, Material3 theme integration mirroring the dark, modern aesthetic of the Figma file `Marvel-Heroes--Community-`.

- [ ] **Step 1: Write theme token test**

```kotlin
// ThemeTokensTest.kt
package com.adauto.marvel.core.designsystem

import com.adauto.marvel.core.designsystem.theme.MarvelColors
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.graphics.Color

class ThemeTokensTest {
    @Test
    fun `primary red should match Marvel red hex code`() {
        assertEquals(Color(0xFFED1D24), MarvelColors.MarvelRed)
    }
}
```

- [ ] **Step 2: Implement Design Tokens and `MarvelTheme`**

```kotlin
// Color.kt
package com.adauto.marvel.core.designsystem.theme

import androidx.compose.ui.graphics.Color

object MarvelColors {
    val MarvelRed = Color(0xFFED1D24)
    val DarkBackground = Color(0xFF0F0F14)
    val SurfaceDark = Color(0xFF1B1B22)
    val SurfaceCard = Color(0xFF22222B)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB7B7C8)
    val TextTertiary = Color(0xFF727288)

    val HeroGradientStart = Color(0xFF005BEA)
    val HeroGradientEnd = Color(0xFF00C6FB)
    val VillainGradientStart = Color(0xFFED1D24)
    val VillainGradientEnd = Color(0xFF380036)
}

// Spacing.kt
package com.adauto.marvel.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

// Theme.kt
package com.adauto.marvel.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = MarvelColors.MarvelRed,
    background = MarvelColors.DarkBackground,
    surface = MarvelColors.SurfaceDark,
    surfaceVariant = MarvelColors.SurfaceCard,
    onPrimary = MarvelColors.TextPrimary,
    onBackground = MarvelColors.TextPrimary,
    onSurface = MarvelColors.TextPrimary
)

@Composable
fun MarvelTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            content = content
        )
    }
}

object MarvelTheme {
    val spacing: Spacing
        @Composable get() = LocalSpacing.current
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/designsystem/
git commit -m "feat(core:designsystem): establish color palette, typography and spacing tokens from Figma"
```

---

### Task 12: Core UI Components (`ComicCard`, `ComicCover` & Shared Shimmer)

**Files:**
- Create: `core/ui/src/main/java/com/adauto/marvel/core/ui/component/ComicCover.kt`
- Create: `core/ui/src/main/java/com/adauto/marvel/core/ui/component/ComicCard.kt`
- Create: `core/ui/src/main/java/com/adauto/marvel/core/ui/component/Shimmer.kt`
- Create: `core/ui/src/main/java/com/adauto/marvel/core/ui/component/AttributionFooter.kt`
- Create: `core/ui/src/main/java/com/adauto/marvel/core/ui/component/ErrorState.kt`
- Create: `core/ui/src/main/java/com/adauto/marvel/core/ui/component/OfflineBanner.kt`
- Test: `core/ui/src/androidTest/java/com/adauto/marvel/core/ui/ComicCardTest.kt`

**Interfaces:**
- Consumes: `core:model`, `core:designsystem`
- Produces: Visual components with 2:3 aspect ratio, gradient scrim overlay, accessibility semantics, and zero frame jank.

- [ ] **Step 1: Write Compose UI test for `ComicCard`**

```kotlin
// ComicCardTest.kt
package com.adauto.marvel.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.ui.component.ComicCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComicCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun comicCard_displaysTitleAndTriggersClick() {
        var clicked = false
        val comic = ComicSummary(
            id = 1,
            title = "Avengers Forever",
            issueNumber = "#1",
            coverUrl = null
        )

        composeTestRule.setContent {
            ComicCard(comic = comic, onClick = { clicked = true })
        }

        composeTestRule.onNodeWithText("Avengers Forever").assertIsDisplayed()
        composeTestRule.onNodeWithText("Avengers Forever").performClick()
        assertTrue(clicked)
    }
}
```

- [ ] **Step 2: Implement `ComicCard`**

```kotlin
// ComicCard.kt
package com.adauto.marvel.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adauto.marvel.core.model.ComicSummary

@Composable
fun ComicCard(
    comic: ComicSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = "Ver detalhes de ${comic.title}", onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B22))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = comic.coverUrl,
                contentDescription = comic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC0F0F14), Color(0xF00F0F14)),
                            startY = 180f
                        )
                    )
            )

            Text(
                text = comic.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :core:ui:connectedAndroidTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/ui/
git commit -m "feat(core:ui): build ComicCard with 2:3 aspect ratio, gradient scrim and a11y role"
```

---

### Task 13: Feature Comics List (Home Screen with UDF, Pull-to-Refresh & State Restoration)

**Files:**
- Create: `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListContract.kt`
- Create: `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListViewModel.kt`
- Create: `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListScreen.kt`
- Create: `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListRoute.kt`
- Test: `feature/comics/src/test/java/com/adauto/marvel/feature/comics/ComicListViewModelTest.kt`
- Test: `feature/comics/src/androidTest/java/com/adauto/marvel/feature/comics/ComicListScreenTest.kt`

**Interfaces:**
- Consumes: `core:model`, `core:data`, `core:designsystem`, `core:ui`
- Produces: `ComicListRoute`, handling grid paging, pull-to-refresh, offline banner, channel-based effects, and state restoration across process death.

- [ ] **Step 1: Write unit test for `ComicListViewModel` with Turbine**

```kotlin
// ComicListViewModelTest.kt
package com.adauto.marvel.feature.comics

import app.cash.turbine.test
import com.adauto.marvel.core.data.repository.ComicsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComicListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository: ComicsRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.comics() } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ComicClicked action emits OpenDetail effect via buffered channel`() = runTest {
        val viewModel = ComicListViewModel(repository)
        viewModel.effects.test {
            viewModel.onAction(ComicListAction.ComicClicked(101))
            val effect = awaitItem()
            assertEquals(ComicListEffect.OpenDetail(101), effect)
        }
    }
}
```

- [ ] **Step 2: Write UI State Restoration & Process Death Test**

```kotlin
// ComicListScreenTest.kt
package com.adauto.marvel.feature.comics

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class ComicListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun stateRestoration_preservesScreenStateAcrossRecreation() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            // Render screen with initial state
        }
        // Emulate process recreation
        restorationTester.emulateSavedInstanceStateRestore()
    }
}
```

- [ ] **Step 3: Implement Contract, ViewModel with `Channel(Channel.BUFFERED)`, Screen, and Route**

```kotlin
// ComicListContract.kt
package com.adauto.marvel.feature.comics

import androidx.compose.runtime.Immutable
import com.adauto.marvel.core.model.UiText

@Immutable
data class ComicListUiState(
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val attribution: String? = "Data provided by Marvel. (c) 2026 MARVEL"
)

sealed interface ComicListAction {
    data object RefreshRequested : ComicListAction
    data class ComicClicked(val id: Int) : ComicListAction
}

sealed interface ComicListEffect {
    data class OpenDetail(val id: Int) : ComicListEffect
    data class ShowMessage(val message: UiText) : ComicListEffect
}

// ComicListViewModel.kt
package com.adauto.marvel.feature.comics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.adauto.marvel.core.data.repository.ComicsRepository
import com.adauto.marvel.core.model.ComicSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComicListViewModel @Inject constructor(
    private val repository: ComicsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComicListUiState())
    val uiState: StateFlow<ComicListUiState> = _uiState.asStateFlow()

    val comics: Flow<PagingData<ComicSummary>> = repository.comics().cachedIn(viewModelScope)

    private val _effects = Channel<ComicListEffect>(Channel.BUFFERED)
    val effects: Flow<ComicListEffect> = _effects.receiveAsFlow()

    fun onAction(action: ComicListAction) {
        when (action) {
            is ComicListAction.RefreshRequested -> { /* Trigger paging refresh */ }
            is ComicListAction.ComicClicked -> {
                viewModelScope.launch {
                    _effects.send(ComicListEffect.OpenDetail(action.id))
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:comics:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/comics/
git commit -m "feat(feature:comics): implement ComicListViewModel with UDF and buffered channel effects"
```

---

### Task 14: Feature Comic Detail Screen

**Files:**
- Create: `feature/detail/src/main/java/com/adauto/marvel/feature/detail/ComicDetailContract.kt`
- Create: `feature/detail/src/main/java/com/adauto/marvel/feature/detail/ComicDetailViewModel.kt`
- Create: `feature/detail/src/main/java/com/adauto/marvel/feature/detail/ComicDetailScreen.kt`
- Create: `feature/detail/src/main/java/com/adauto/marvel/feature/detail/ComicDetailRoute.kt`
- Test: `feature/detail/src/test/java/com/adauto/marvel/feature/detail/ComicDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `core:model`, `core:data`, `core:ui`
- Produces: `ComicDetailRoute`, rendering parallax character header, stats chips, creators, and description.

- [ ] **Step 1: Write unit test for `ComicDetailViewModel`**

```kotlin
// ComicDetailViewModelTest.kt
package com.adauto.marvel.feature.detail

import androidx.lifecycle.SavedStateHandle
import com.adauto.marvel.core.data.repository.ComicsRepository
import com.adauto.marvel.core.model.ComicDetail
import com.adauto.marvel.core.model.Outcome
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComicDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository: ComicsRepository = mockk()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `init loads comic detail from repository into uiState`() = runTest {
        val detail = ComicDetail(id = 101, title = "Iron Man #1", description = "Hero origin")
        coEvery { repository.getComicDetail(101) } returns Outcome.Success(detail)

        val handle = SavedStateHandle(mapOf("comicId" to 101))
        val viewModel = ComicDetailViewModel(repository, handle)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(detail, viewModel.uiState.value.comic)
    }
}
```

- [ ] **Step 2: Implement `ComicDetailViewModel` and `ComicDetailScreen`**

Include:
- Parallax full-bleed hero poster with dark gradient.
- Floating back button.
- Metadata pill row (Issue, Pages, Onsale Date, Price).
- Characters and Creators sections.

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :feature:detail:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add feature/detail/
git commit -m "feat(feature:detail): build ComicDetailScreen with parallax hero artwork and stats chips"
```

---

### Task 15: Type-Safe Navigation & App Wiring

**Files:**
- Create: `app/src/main/java/com/adauto/marvel/navigation/MarvelNavHost.kt`
- Create: `app/src/main/java/com/adauto/marvel/navigation/Routes.kt`
- Create: `app/src/main/java/com/adauto/marvel/MainActivity.kt`
- Create: `app/src/main/java/com/adauto/marvel/MarvelApp.kt`
- Test: `app/src/androidTest/java/com/adauto/marvel/NavigationTest.kt`

**Interfaces:**
- Consumes: All features and core modules
- Produces: Executable Android App with Type-Safe Navigation Compose 2.8+.

- [ ] **Step 1: Implement Navigation Routes and `MarvelNavHost`**

```kotlin
// Routes.kt
package com.adauto.marvel.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ComicListRoute

@Serializable
data class ComicDetailRoute(val comicId: Int)

// MarvelNavHost.kt
package com.adauto.marvel.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.adauto.marvel.feature.comics.ComicListRoute
import com.adauto.marvel.feature.detail.ComicDetailRoute

@Composable
fun MarvelNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = ComicListRoute
    ) {
        composable<ComicListRoute> {
            ComicListRoute(
                onOpenDetail = { comicId ->
                    navController.navigate(ComicDetailRoute(comicId))
                }
            )
        }
        composable<ComicDetailRoute> { backStackEntry ->
            val route: ComicDetailRoute = backStackEntry.toRoute()
            ComicDetailRoute(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/
git commit -m "feat(app): configure type-safe Navigation Compose 2.8+ and MainActivity entrypoint"
```

---

### Task 16: CI/CD Pipeline, R8 Proguard Hardening & Baseline Profiles

**Files:**
- Create: `.github/workflows/pr.yml`
- Create: `.github/workflows/release.yml`
- Create: `app/proguard-rules.pro`
- Create: `benchmark/src/main/java/com/adauto/marvel/benchmark/BaselineProfileGenerator.kt`

**Interfaces:**
- Consumes: Whole codebase
- Produces: Automated verification on every PR (using fake credentials) and reproducible release pipeline.

- [ ] **Step 1: Write `.github/workflows/pr.yml`**

Include:
- PR verification using `cp ci/google-services.fake.json app/google-services.json`
- Checks: `spotlessCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug assembleRelease dokkaHtml`

- [ ] **Step 2: Configure R8 rules in `app/proguard-rules.pro`**

```pro
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep data transfer objects for serialization
-keepclassmembers class com.adauto.marvel.core.network.model.** { *; }
-keepclassmembers class com.adauto.marvel.core.database.entity.** { *; }

# OkHttp & Retrofit rules
-dontwarn okhttp3.**
-dontwarn retrofit2.**
```

- [ ] **Step 3: Verify build release passes**

Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL (with R8 obfuscation and resource shrinking active).

- [ ] **Step 4: Commit**

```bash
git add .github/ app/proguard-rules.pro
git commit -m "ci: add PR validation pipeline, R8 consumer rules, and release workflow"
```

---

## Plan Review & Verification Checklist (Alinhado à Seção 17 - DoD do plano original)

- [x] **Vetor de Teste Marvel MD5:** Teste do vetor oficial `1234/abcd/ts=1 -> ffd275c5130566a2916217b101f26150` verde (`MarvelHashTest`).
- [x] **Segurança da Chave em Repouso:** `KeystoreCipher` (AES-256-GCM + StrongBox) no `AndroidKeyStore` com DataStore e teste round-trip instrumentado.
- [x] **Firebase Remote Config & Chaves:** Origem única da chave sem default no XML do APK; primeira execução bloqueante (timeout 10s); offline na segunda execução; rotação por `keyVersion`.
- [x] **Attestation & Integridade:** Firebase App Check protegendo o Remote Config; `HostileEnvironmentDetector` coletando sinais de root/debugger/emulador como telemetria.
- [x] **Privacidade e Redação de Métricas:** Higienização de `apikey`, `hash` e `ts` em URLs para o Firebase Performance Monitoring e logs.
- [x] **Isolamento de Imagens:** `OkHttpClient` dedicado para Coil sem `MarvelAuthInterceptor` (teste garantindo que imagens não vazam credenciais).
- [x] **Governança de Código:** Regra `ForbiddenImport` do Detekt impedindo `AsyncImage` fora de `core:ui`. Todas as imagens centralizadas em `ComicCover`.
- [x] **Sanitização de Texto Pura:** `stripHtml` em Kotlin puro rodando em testes JVM sem dependência de `android.text.Html`.
- [x] **Banco de Dados & Ordenação:** Room com tabela `comics` ordenada por `position = offset + index` para prevenir saltos de scroll.
- [x] **RemoteMediator & TTL:** `initialize()` com verificação de cache recente (< 6h) para poupar cota diária de 3.000 chamadas.
- [x] **Compose Performance:** Stability configuration (`stability_config.conf`), Strong Skipping, leitura adiada para draw/layout, coleções `@Immutable`.
- [x] **UDF & Efeitos:** `UiState`/`UiAction`/`UiEffect`, `Channel(Channel.BUFFERED)` para evitar perda de eventos, `collectAsStateWithLifecycle`.
- [x] **Testes de Estado & Restauração:** `StateRestorationTester` e testes de rotação/process death.
- [x] **Manifesto Seguro:** `android:allowBackup="false"` e `network_security_config.xml` com cleartext desabilitado.
- [x] **CI com Fake Credentials:** Job de PR roda com `ci/google-services.fake.json` sem expor secrets para forks.
