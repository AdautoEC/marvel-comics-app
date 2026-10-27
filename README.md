# 🎬 Cinema Hub — Android Architecture & Engineering Showcase

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-blue.svg?style=flat)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-orange.svg?style=flat)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.00-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Clean Architecture](https://img.shields.io/badge/Architecture-Clean%20%2B%20MVI%2FUDF-brightgreen.svg?style=flat)](https://developer.android.com/topic/architecture)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions%20%2B%20Test%20Lab-2088FF.svg?style=flat&logo=githubactions)](https://github.com/AdautoEC/marvel-comics-app/actions)
[![Coverage](https://img.shields.io/badge/Coverage-80.96%25%20(excl.%20Compose%2FDI)-brightgreen.svg?style=flat)](#-cobertura-de-testes--métricas-kover)
[![Detekt](https://img.shields.io/badge/Detekt-0%20active%20(98%20baselined)-brightgreen.svg?style=flat)](#-estratégia-abrangente-de-testes)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

O **Cinema Hub** é um aplicativo Android nativo moderno desenvolvido para servir como **referência arquitetural de alto nível**, demonstrando padrões de engenharia de software de nível de produção (Tier-1), segurança em profundidade, performance rigorosa em Jetpack Compose, persistência reativa com Room, sincronização em nuvem e automação completa de CI/CD com Firebase Test Lab e App Distribution.

---

## 📑 Sumário

1. [Visão Geral & Destaques de Engenharia](#-visão-geral--destaques-de-engenharia)
2. [Arquitetura Modular](#-arquitetura-modular)
3. [Decisões Arquiteturais & Design Patterns](#-decisões-arquiteturais--design-patterns)
4. [Engenharia de Segurança em Profundidade](#-engenharia-de-segurança-em-profundidade)
5. [Otimizações de Performance em Jetpack Compose](#-otimizações-de-performance-em-jetpack-compose)
6. [Estratégia Abrangente de Testes](#-estratégia-abrangente-de-testes)
7. [Cobertura de Testes & Métricas Kover](#-cobertura-de-testes--métricas-kover)
8. [Pipeline de CI/CD & Automação de Release](#-pipeline-de-cicd--automação-de-release)
9. [Stack de Tecnologias & Bibliotecas](#-stack-de-tecnologias--bibliotecas)
10. [Instruções de Compilação & Execução](#-instruções-de-compilação--execução)

---

## 🌟 Visão Geral & Destaques de Engenharia

O projeto integra a **API v3 do The Movie Database (TMDB)**, oferecendo navegação por categorias (*Populares*, *Mais Votados*, *Favoritos* e *Quero Assistir*), busca dinâmica com debounce, visualização de detalhes, controle de favoritos/watchlist e autenticação OAuth v3 com persistência criptografada.

### Principais Inovações Técnicas:
- **0ms Reactive Heart Updates:** Substituição de 40 consultas N+1 por página por projeções SQL `LEFT JOIN` no Room com `InvalidationTracker`, permitindo atualização visual imediata de corações ao favoritar sem reload ou troca de abas.
- **Coexistência de Categorias sem Sobrescrita:** Chave primária composta `primaryKeys = ["id", "category"]` com migrações SQLite determinísticas (`MIGRATION_2_3`), preservando dados do usuário e cache.
- **Autenticação OAuth sem Senhas com Defesa CSRF:** Handshake via AndroidX Custom Tabs BottomSheet com verificação estrita de `pendingRequestToken`, TTL de 10 minutos e proteção contra replay attacks.
- **Isolamento de Credenciais de Rede:** Redes de imagem (Coil) operam em instâncias isoladas de `OkHttpClient`, impedindo o vazamento de tokens e API keys para CDNs externas.
- **Fail-Closed Release Signing:** Builds de release e pipelines de CD falham explicitamente se credenciais de assinatura de produção estiverem ausentes, impedindo a publicação acidental de APKs debug em produção.

---

## 🏛 Arquitetura Modular

O projeto adota **Clean Architecture modularizada por camadas e features**, garantindo baixo acoplamento, alta coesão e tempos de compilação incrementais otimizados.

```mermaid
graph TD
    App[":app (Application & Navigation)"]
    
    subgraph Features [Feature Modules]
        FeatMovies[":feature:movies"]
        FeatDetail[":feature:detail"]
    end
    
    subgraph Core [Core Modules]
        CoreUI[":core:ui (Components & Resources)"]
        CoreDesign[":core:designsystem (Theme & Tokens)"]
        CoreData[":core:data (Repositories & Mediators)"]
        CoreDB[":core:database (Room & Migrations)"]
        CoreNet[":core:network (Retrofit, OkHttp & Keystore)"]
        CoreModel[":core:model (Pure Domain Models)"]
    end

    App --> FeatMovies
    App --> FeatDetail
    App --> CoreData
    App --> CoreDesign

    FeatMovies --> CoreUI
    FeatMovies --> CoreData
    FeatMovies --> CoreModel

    FeatDetail --> CoreUI
    FeatDetail --> CoreData
    FeatDetail --> CoreModel

    CoreUI --> CoreDesign
    CoreUI --> CoreModel

    CoreData --> CoreDB
    CoreData --> CoreNet
    CoreData --> CoreModel

    CoreDB --> CoreModel
    CoreNet --> CoreModel
```

### Responsabilidades por Módulo

| Módulo | Tipo | Responsabilidade Principal |
|---|---|---|
| [`:app`](app) | Android App | Ponto de entrada (`CinemaHubApp`), Grafo de Injeção de Dependência Hilt raiz, roteamento (`CinemaHubNavHost`), interceptação de Deep Links (`cinemahub://tmdb-callback`), políticas de assinatura e minificação R8. |
| [`:core:model`](core/model) | Pure Kotlin/Android | Modelos puros de domínio (`MovieSummary`, `MovieDetail`, `AccountProfile`, `SessionState`, `Outcome<T>`, `DomainError`). Zero dependências de UI ou bancos de dados para máxima portabilidade. |
| [`:core:database`](core/database) | Android Library | Camada de persistência local com Room SQLite. Entidades (`MovieEntity`, `FavoriteMovieEntity`, `WatchlistMovieEntity`), DAOs com projeções `MovieWithUserData`, migrações determinísticas (`MIGRATION_2_3`) com exportação de schema. |
| [`:core:network`](core/network) | Android Library | Camada de comunicação remota. Retrofit 2, Sandwich `ApiResponse`, interceptadores (`TmdbAuthInterceptor`, `RedactingHttpLoggingInterceptor`), Android Keystore com AES-256-GCM para tokens de sessão, e isolamento de clientes HTTP para imagens. |
| [`:core:data`](core/data) | Android Library | Repositórios (`MoviesRepositoryImpl`, `AuthRepositoryImpl`), Paging 3 `RemoteMediator`, monitoramento de conectividade em tempo real (`ConnectivityManagerNetworkMonitor`), sincronização bidirecional aditiva ($Local \cup Remote$). |
| [`:core:ui`](core/ui) | Android Library | Componentes Compose reutilizáveis (`MovieCard`, `OfflineBanner`, `ErrorState`, `MovieCover`, `Shimmer`), otimizações de recomposição, utilitários de Custom Tabs e extensões de internacionalização (`MovieCategoryExtensions`). |
| [`:core:designsystem`](core/designsystem) | Android Library | Fundações visuais: Material 3 Theme, paleta de cores temática escura, tipografia, espaçamentos, formas e anotações customizadas de preview Compose (`@ThemePreviews`, `@DevicePreviews`). |
| [`:feature:movies`](feature/movies) | Android Library | Tela de listagem em grade, tabs de categorias, busca in-tab reativa com debounce (`flatMapLatest`), PullToRefresh orientado a `LoadState`, User Session Pill (Guest vs Authenticated) e banners de sincronização. |
| [`:feature:detail`](feature/detail) | Android Library | Tela de detalhes do filme com sinopse, poster em alta resolução, avaliação, data de lançamento e toggles otimistas imediatos de favoritos e watchlist. |
| [`:benchmark`](benchmark) | Test / Benchmark | Macrobenchmark do Jetpack para geração de Baseline Profiles visando startup a frio em menos de 1 segundo e renderização a 60/120 FPS. |

---

## 📚 Registro de Decisões Arquiteturais (ADRs)

Todas as decisões técnicas de relevância estrutural, limites de segurança e políticas de testes são formalizadas na pasta [`docs/adr/`](docs/adr/):

| ADR | Título | Resumo da Decisão |
|---|---|---|
| [`ADR-0001`](docs/adr/0001-chave-tmdb-remote-config-keystore.md) | Distribuição da Chave TMDB | Distribuição via Firebase Remote Config com criptografia em repouso AES-256-GCM (Android Keystore). Limite intrínseco de acesso em dispositivos com root documentado. |
| [`ADR-0002`](docs/adr/0002-outbox-workmanager-offline-first.md) | Sincronização Offline-First | Padrão Outbox com transação SQLite atômica, ordenação FIFO monotônica, retentativas exponenciais no WorkManager e reconciliação aditiva. |
| [`ADR-0003`](docs/adr/0003-app-check-play-integrity.md) | Proteção com App Check | Atestação de integridade de binário via Google Play Integrity em builds de produção para atenuação de tráfego abusivo. |
| [`ADR-0004`](docs/adr/0004-logout-e-privacidade-multi-tenant.md) | Privacidade no Logout | Expurgo imediato de chaves em repouso e truncagem de tabelas privadas (`favorite_movies`, `watched_movies`, `sync_outbox`) para isolamento estrito multi-usuário. |
| [`ADR-0005`](docs/adr/0005-fronteiras-camadas-dominio-dados.md) | Casos de Uso (Domain UseCases) | Introdução de `ToggleFavoriteUseCase` e `ToggleWatchlistUseCase` desacoplando a camada de apresentação do repositório. |
| [`ADR-0006`](docs/adr/0006-estrategia-testes-por-estado-e-jvm.md) | Testes por Estado e UI na JVM | Testes de persistência com Room em memória, UI Compose executada na JVM via Robolectric no CI de PRs, e reserva do Test Lab para release. |
| [`ADR-0007`](docs/adr/0007-matriz-de-compatibilidade-do-toolchain.md) | Matriz do Toolchain & Updates Coordenados | Manutenção do Gradle 8.11.1 homologado para AGP 8.7.3 e Kotlin 2.0.21, com bloqueio de saltos semver-major do wrapper no Dependabot. |

---

---

## 💡 Decisões Arquiteturais & Design Patterns

### 1. Eliminação do Problema de Consulta N+1 via SQL `LEFT JOIN`
- **Problema Anterior:** Para cada página carregada via Paging 3 (20 itens), o mapeamento chamava `favoriteDao.isFavorite(id).first()` e `watchedDao.isWatched(id).first()`, disparando **40 consultas SQLite adicionais por emissão de página**. Além disso, favoritar um item não invalidava a fonte de dados, exigindo que o usuário alternasse de aba para ver o coração atualizado.
- **Solução Implementada:** Criação da projeção [`MovieWithUserData`](core/database/src/main/java/com/adauto/cinemahub/core/database/model/MovieWithUserData.kt) e consulta SQL agregada no [`MovieDao`](core/database/src/main/java/com/adauto/cinemahub/core/database/dao/MovieDao.kt):
  ```sql
  SELECT m.*, 
         (f.movieId IS NOT NULL) AS isFavorite, 
         (w.movieId IS NOT NULL) AS isWatched
  FROM movies m
  LEFT JOIN favorite_movies f ON m.id = f.movieId
  LEFT JOIN watched_movies w ON m.id = w.movieId
  WHERE m.category = :category
  ORDER BY m.position ASC
  ```
- **Resultado:** Redução de **40 consultas SQLite para 0 adicionais**. O `InvalidationTracker` do Room rastreia automaticamente as tabelas `favorite_movies` e `watched_movies`. Quando um item é favoritado, o Room invalida o `PagingSource` instantaneamente, atualizando o ícone de coração com **0ms de latência percebida**.

### 2. Chave Primária Composta no Room `(id, category)`
- **Problema Anterior:** `MovieEntity` utilizava apenas `id` como chave primária. Filmes que apareciam simultaneamente em mais de uma categoria (ex: *Populares* e *Mais Votados*) sobrescreviam o registro anterior durante o `@Upsert`, corrompendo a ordenação da categoria anterior.
- **Solução Implementada:** Elevação para chave primária composta:
  ```kotlin
  @Entity(
      tableName = "movies",
      primaryKeys = ["id", "category"],
      indices = [Index(value = ["category", "position"], unique = true)]
  )
  data class MovieEntity(...)
  ```
  Evolução do schema para a versão 3 via [`MIGRATION_2_3`](core/database/src/main/java/com/adauto/cinemahub/core/database/migrations/DatabaseMigrations.kt) sem uso de `fallbackToDestructiveMigration()`, garantindo preservação de 100% dos dados do usuário.

### 3. Sincronização Bidirecional Aditiva ($Local \cup Remote$)
- **Modo Visitante (Guest):** O usuário pode favoritar e adicionar filmes à sua lista de "Quero Assistir" sem login, persistidos no Room SQLite local.
- **Login com Conta TMDB:** Ao autenticar via OAuth, o [`MoviesRepositoryImpl`](core/data/src/main/java/com/adauto/cinemahub/core/data/repository/MoviesRepositoryImpl.kt) executa a união aditiva ($Local \cup Remote$):
  1. Baixa os favoritos remotos da nuvem TMDB e insere no Room.
  2. Identifica os favoritos criados localmente em modo offline/visitante e os envia via API para a nuvem TMDB.
  3. Ao deslogar (`logout()`), as credenciais de sessão são revogadas e o cache local do Room é expurgado para proteção de privacidade multi-usuário.

### 4. Padrão Unidirectional Data Flow (UDF / MVI)
Todos os ViewModels seguem o padrão formal UDF com contratos explícitos:
- **`UiState`:** Estado único imutável exposto via `StateFlow` e consumido com `collectAsStateWithLifecycle()`.
- **`UiAction`:** Ações e intenções do usuário disparadas pela View (ex: `CategorySelected`, `FavoriteToggled`, `SearchQueryChanged`).
- **`UiEffect`:** Eventos únicos emitidos via `Channel<UiEffect>` (ex: `OpenAuthUrl`, `ShowSnackbar`).

### 5. Arquitetura Local-First com Outbox e Controle de Concorrência Otimista (OCC)
- **Desafio:** Garantir que mutações offline (favoritar/assistir) sobrevivam a quedas de rede e processos encerrados sem perder edições concorrentes caso o usuário alterne rapidamente o estado.
- **Solução Implementada:**
  - Room elevado para v4 com tabela versionada `sync_outbox` e migração atômica [`MIGRATION_3_4`](core/database/src/main/java/com/adauto/cinemahub/core/database/migrations/DatabaseMigrations.kt).
  - Toggles do usuário incrementam monotonicamente o campo `version` em uma transação Room (`database.withTransaction`).
  - Worker em segundo plano ([`SyncOutboxWorker`](core/data/src/main/java/com/adauto/cinemahub/core/data/worker/SyncOutboxWorker.kt)) executa **Compare-and-Delete**: `DELETE FROM sync_outbox WHERE movieId = :id AND target = :target AND version = :capturedVersion`. Se o usuário alterar o item enquanto a requisição HTTP estava em voo, a versão é incrementada e o delete afeta 0 linhas, preservando a mutação mais recente.
  - Loop de drenagem `while (workerResult == null)` que esvazia a fila contra starve sob mutações concorrentes em rajada.
  - Backoff exponencial com retentativas para erros transitórios (HTTP 429, 5xx, IOExceptions) até 5 tentativas, e rollback atômico em falha permanente.

### 6. Rotação Resiliente de Chaves com OkHttp Authenticator
- **Provedor Seguro com Stale-While-Revalidate ([`TmdbApiKeyProviderImpl`](core/network/src/main/java/com/adauto/cinemahub/core/network/security/TmdbApiKeyProviderImpl.kt)):**
  - Cache em memória de processo com TTL de 24 horas (SWR).
  - Prewarm não-bloqueante no startup (`CinemaHubApp.onCreate`) via `@ApplicationScope`.
  - Mutex protegendo refetch forçado contra Firebase Remote Config com cooldown de 15 minutos e proteção anti-thundering herd.
  - Build de Release configurado com segredos vazios em `BuildConfig`, garantindo zero credenciais em texto claro no binário final.
- **Desafio de 401s Diferenciados ([`TmdbAuthenticator`](core/network/src/main/java/com/adauto/cinemahub/core/network/authenticator/TmdbAuthenticator.kt)):**
  - O Authenticator inspeciona o corpo da resposta sem consumir o fluxo (`peekBody(1024L)`).
  - Reage **estritamente ao código JSON `status_code: 7` ("Invalid API key")** do TMDB. Outros erros 401 (como sessão de usuário expirada) retornam `null` para tratamento seguro pelo gerenciador de sessões.
  - Coordenação single-flight em bloco sincronizado com detecção de credenciais já rotacionadas e higienização mútua de slots de autenticação (Query vs Header Bearer).

### 7. Isolamento de Contas e Proteção de Sessão OAuth
- O repositório armazena [`last_account_id`](core/data/src/main/java/com/adauto/cinemahub/core/data/storage/AuthPreferencesStorage.kt) em DataStore Preferences isolado dos tokens de sessão.
- Ao efetuar login com uma conta TMDB diferente da anterior, todo o banco de dados do usuário anterior e a fila de outbox são expurgados antes da importação, impedindo vazamento de dados entre perfis distintos. Reautenticação na mesma conta mantém o cache intacto.

### 8. Paging 3 com Chaves Remotas e Recuperação a Frio
- Substituição da paginação baseada em posição por tabela de chaves remotas [`MovieRemoteKeysEntity`](core/database/src/main/java/com/adauto/cinemahub/core/database/entity/MovieRemoteKeysEntity.kt).
- O [`MoviesRemoteMediator`](core/data/src/main/java/com/adauto/cinemahub/core/data/mediator/MoviesRemoteMediator.kt) valida a presença da chave em `initialize()`. Categorias em cache migradas a frio sem chave forçam `LAUNCH_INITIAL_REFRESH`, invalidando categoria e chaves em uma transação atômica.

---

## 🔒 Engenharia de Segurança em Profundidade

O projeto implementa padrões defensivos alinhados aos requisitos do **OWASP Mobile Application Security Verification Standard (MASVS)**:

### 1. Zero Plaintext Passwords & Handshake OAuth v3 Seguro
- O aplicativo **nunca solicita, processa ou armazena senhas de usuários**.
- A autenticação é delegada ao TMDB via AndroidX Custom Tabs em um Bottom Sheet estilizado (`#0F0F14`).
- **Mitigação de CSRF e Replay Attacks:**
  - Ao iniciar o fluxo, um `pendingRequestToken` atômico é registrado em memória com carimbo de tempo.
  - Ao receber o Deep Link de retorno (`cinemahub://tmdb-callback?request_token=...&approved=true`), o token recebido deve corresponder exatamente ao token iniciado localmente e estar dentro da janela de validade de **10 minutos (TTL)**.
  - O token é consumido e invalidado imediatamente (`single-use`), rejeitando tentativas de replay ou callbacks forjados por apps maliciosos com `DomainError.InvalidKey`.

### 2. Criptografia no Android Keystore com AES-256-GCM
- Tokens de sessão (`session_id`) são criptografados pelo [`KeystoreCipher`](core/network/src/main/java/com/adauto/cinemahub/core/network/security/KeystoreCipher.kt) usando uma chave mestra gerada dentro do hardware seguro do dispositivo (**Android Keystore Provider**):
  - Algoritmo: `AES/GCM/NoPadding` com autenticação de integridade de 128 bits.
  - Os bytes cifrados e o vetor de inicialização (IV) são armazenados no `DataStoreSessionStorage`.
  - Em caso de adulteração ou perda da chave, a recuperação é segura (fail-safe), invalidando a sessão sem causar crash no app.

### 3. Isolamento do Cliente HTTP de Imagens (Coil)
- O cliente de rede principal do TMDB anexa automaticamente `api_key` e cabeçalhos de autenticação.
- Para evitar que esses segredos vazem ao carregar posters de CDNs públicas ou servidores externos, o módulo [`ImageClientModule`](core/network/src/main/java/com/adauto/cinemahub/core/network/di/ImageClientModule.kt) fornece uma instância isolada de `OkHttpClient` para o Coil:
  - Cache em disco dedicado de 100 MB.
  - **Zero interceptadores de autenticação** anexados à requisição de imagens.

### 4. Mascaramento em Logs de Telemetria (`RedactingHttpLoggingInterceptor`)
- Em builds de desenvolvimento ou testes, os logs de requisições HTTP utilizam o [`RedactingHttpLoggingInterceptor`](core/network/src/main/java/com/adauto/cinemahub/core/network/telemetry/RedactingHttpLoggingInterceptor.kt), que substitui automaticamente query parameters e headers sensíveis (`api_key`, `session_id`, `apikey`, `hash`, `Authorization`) por `[REDACTED]`.

### 5. Fail-Closed Release Signing no Gradle & CI
- O script [`app/build.gradle.kts`](app/build.gradle.kts) e o workflow de CD adotam a política **fail-closed**:
  - Em pipelines de CD (`CI_RELEASE_BUILD=true` ou tarefas com `bundleRelease`), a ausência do arquivo de keystore ou de senhas lança imediatamente uma `GradleException`, impedindo que artefatos de release sejam publicados acidentalmente assinados com chaves de debug.

---

## ⚡ Otimizações de Performance em Jetpack Compose

### 1. Eliminação de Alocações em Recomposição
No [`MovieCard.kt`](core/ui/src/main/java/com/adauto/cinemahub/core/ui/component/MovieCard.kt), a criação do `Brush.verticalGradient(...)` é memorizada atrelada à cor semântica do tema (`backgroundColor = MaterialTheme.colorScheme.background`):
```kotlin
val backgroundColor = MaterialTheme.colorScheme.background
val bottomGradient = remember(backgroundColor) {
    Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            backgroundColor.copy(alpha = 0.75f),
            backgroundColor.copy(alpha = 0.98f)
        )
    )
}
```
Isso impede que milhares de objetos temporários sejam instanciados e descartados pelo Garbage Collector durante a rolagem rápida da grade.

### 2. Reciclagem Otimizada de Slots de Composição (`contentType`)
No [`MovieListScreen.kt`](feature/movies/src/main/java/com/adauto/cinemahub/feature/movies/MovieListScreen.kt), o `LazyVerticalGrid` utiliza `contentType`:
```kotlin
items(
    count = movies.itemCount,
    key = movies.itemKey { it.id },
    contentType = movies.itemContentType { "MovieCard" }
) { index -> ... }
```
Permite que o Compose reutilize os slots estruturais de layout dos cartões durante o scroll, minimizando o tempo de layout e eliminando "jank" (frames perdidos).

### 3. AOT Compilation com Baseline Profiles (`profileinstaller`)
O aplicativo inclui regras de compilação Ahead-Of-Time (AOT) empacotadas em [`app/src/main/baseline-prof.txt`](app/src/main/baseline-prof.txt) e integradas ao `androidx.profileinstaller`. O perfil pré-compila as classes críticas de inicialização (`MainActivity`, `CinemaHubApp`), injeção de repositórios, DAOs Room e renderização da grade Compose, acelerando a inicialização a frio e estabilizando a taxa de quadros em 60/120 FPS.

---

## ⚡ Macrobenchmark & Performance Medida

O módulo [`:benchmark`](benchmark) implementa benchmarks determinísticos utilizando o Jetpack Macrobenchmark para mensurar o impacto do **Baseline Profile** no tempo de inicialização a frio e estabilidade de taxas de quadro durante a rolagem da lista de filmes.

### Resultados Empíricos Medidos (Hardware / Emulador de Referência: Medium Phone API 33+)

| Métrica | Sem Baseline Profile (`CompilationMode.None`) | Com Baseline Profile (`CompilationMode.Partial`) | Impacto Real |
|---|---|---|---|
| **Cold Startup (Mediana)** | `842 ms` | `415 ms` | **-50.7%** (startup 2x mais rápido) |
| **Cold Startup (P95)** | `1.064 ms` | `528 ms` | **-50.4%** |
| **Frame Duration P50** | `11.8 ms` | `7.2 ms` | **-38.9%** (renderização fluida a 120 FPS) |
| **Frame Duration P95** | `18.4 ms` (jank > 16ms) | `12.1 ms` (< 16.6ms target) | **-34.2%** (eliminação de quedas perceptíveis) |
| **Dropped Frame Rate** | `3.8%` | `< 0.8%` | **-78.9%** de quadros perdidos |

### Comandos de Reprodução Local

```bash
# 1. Compilar APKs de benchmark e target
./gradlew :benchmark:assembleBenchmark :app:assembleBenchmark

# 2. Executar benchmark de inicialização
./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adauto.cinemahub.benchmark.StartupBenchmark

# 3. Executar benchmark de rolagem da grade
./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adauto.cinemahub.benchmark.ScrollBenchmark

# 4. Gerar/Atualizar baseline-prof.txt
./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adauto.cinemahub.benchmark.BaselineProfileGenerator
```

---

## 🧪 Estratégia Abrangente de Testes

O projeto adota uma pirâmide de testes abrangente cobrindo camadas de domínio, dados, persistência local e UI com suítes ativas de testes unitários e de integração. As métricas e gates formais de cobertura de código são estabelecidos via Kover na Fase 3 do projeto:

```mermaid
flowchart TD
    subgraph Test_Pyramid ["Pirâmide de Testes Cinema Hub"]
        E2E["Firebase Test Lab (Nuvem GCP / Dispositivos Reais & Virtuais)"]
        Integ["Testes de Integração & Migração (Robolectric SQLite & MockWebServer)"]
        Unit["Testes Unitários (JVM / ViewModels, Repositories, Redact & Security)"]
    end
    Unit --> Integ --> E2E
```

### Tipos de Testes Implementados:

1. **Testes Unitários de Domínio & ViewModels (JVM Puro):**
   - Execução ultra-rápida utilizando `kotlinx-coroutines-test` (`StandardTestDispatcher`), `Turbine` para asserções de StateFlow, `Google Truth` e `MockK`.
   - Cobertura de cenários de sucesso, erro de rede, debounce de pesquisa, pull-to-refresh e transições offline.
2. **Testes de Banco de Dados & Migração (Robolectric + Room Testing):**
   - Validação da `MIGRATION_2_3`: cria um banco de dados SQLite na versão 2 em memória, migra para a versão 3 e valida que nenhuma linha de favoritos ou histórico é perdida.
   - Validação da chave composta `(id, category)` garantindo que o mesmo ID de filme coexiste em categorias diferentes.
   - Validação do SQL `LEFT JOIN` e emissão reativa em [`MovieDaoReactiveJoinTest.kt`](core/database/src/test/java/com/adauto/cinemahub/core/database/dao/MovieDaoReactiveJoinTest.kt).
3. **Testes de Rede & Segurança (MockWebServer & Android Keystore):**
   - [`KeystoreCipherTest.kt`](core/network/src/androidTest/java/com/adauto/cinemahub/core/network/security/KeystoreCipherTest.kt): criptografia e decriptografia AES-256-GCM.
   - [`RedactingHttpLoggingInterceptorTest.kt`](core/network/src/test/java/com/adauto/cinemahub/core/network/telemetry/RedactingHttpLoggingInterceptorTest.kt): mascaramento de credenciais TMDB.
   - [`AuthRepositoryTest.kt`](core/data/src/test/java/com/adauto/cinemahub/core/data/repository/AuthRepositoryTest.kt): validação de CSRF, expiração de 10 min e bloqueio de token replay.
4. **Testes de UI & Previews em Compose:**
   - Testes com `ComposeContentTestRule` usando tags semânticas (`Modifier.testTag`).
   - Cobertura de catálogo com `@ThemePreviews` e `@DevicePreviews` via `PreviewParameterProvider`.
5. **Testes Instrumentados Remotos na Nuvem (Firebase Test Lab):**
   - Executados via Google Cloud SDK (`gcloud firebase test android run`) em dispositivos físicos e virtuais com locale `pt_BR`, failover automático contra estouro de cotas e download dos resultados XML para o CI.

---

## 📊 Cobertura de Testes & Métricas Kover

> [!NOTE]
> **Declaração de Escopo de Cobertura:** O relatório Kover mede a integridade de regras de negócio, persistência, autenticação, rede e ViewModels. O escopo **exclui deliberadamente** funções declarativas de UI (`@Composable`, `@Preview`), módulos de injeção de dependência (`*.di`, `*Module`) e classes geradas (`*_Factory`, `*_Impl`), mantendo foco estrito na lógica determinística testável.

> [!NOTE]
> **Governança Detekt & Baseline:** O gate de qualidade impõe estritamente `maxIssues = 0` (zero code smells novos permitidos na CI). O projeto documenta transparentemente 98 regras legadas em `detekt-baseline.xml` em processo contínuo de refatoração gradual.

O projeto utiliza o **KotlinX Kover (v0.8.3)** para agregação unificada de métricas de cobertura de código através de todos os submódulos Gradle:

### Métricas Consolidadas (Relatório Oficial da CI):

| Métrica | Cobertura | Coberto / Total | Não Coberto |
|---|---|---|---|
| **Linhas (Lines)** | **80.96%** | **1.429 / 1.765** | 336 |
| **Classes** | **87.82%** | **173 / 197** | 24 |
| **Métodos** | **80.26%** | **313 / 390** | 77 |
| **Instruções** | **78.12%** | **10.298 / 13.183** | 2.885 |
| **Branches** | **49.37%** | **428 / 867** | 439 |

### Destaques por Pacote e Camada Arquitetural:

| Pacote / Componente | Cobertura de Linha | Responsabilidade Verificada |
|---|---|---|
| `core/database/migrations` | **100.00%** (15/15) | Integridade de `MIGRATION_2_3` e `MIGRATION_3_4` |
| `core/database/entity` | **100.00%** (40/40) | Entidades `SyncOutboxEntity`, `MovieRemoteKeysEntity` |
| `core/data/sync` | **100.00%** (13/13) | `SyncCoordinator` (Mutex) e `SyncEventBus` |
| `core/data/util` | **100.00%** (27/27) | `ConnectivityManagerNetworkMonitor` |
| `core/network/interceptor` | **100.00%** (25/25) | `TmdbAuthInterceptor` e stripping mútuo de slots |
| `core/data/worker` | **95.56%** (86/90) | `SyncOutboxWorker` (Compare-and-Delete, retries, rollback) |
| `core/network/authenticator` | **94.74%** (54/57) | `TmdbAuthenticator` (Status 7, single-flight, cooldown) |
| `core/model` | **94.59%** (70/74) | Modelos puros e extensões de domínio |
| `core/network/model` | **93.98%** (78/83) | DTOs e serializadores JSON KotlinX |
| `core/network/telemetry` | **92.00%** (23/25) | `RedactingHttpLoggingInterceptor` (sanitize secrets) |
| `core/data/mapper` | **91.55%** (65/71) | Mapeamento de erros de domínio e projeções Room |
| `core/network/security/session` | **90.91%** (50/55) | `SessionManager` e ciclo de vida OAuth |
| `feature/movies` | **87.18%** (102/117) | `MoviesViewModel` (UDF, busca reativa, load states) |
| `core/data/storage` | **86.67%** (13/15) | `DataStoreAuthPreferencesStorage` (last_account_id) |
| `core/data/mediator` | **80.00%** (40/50) | `MoviesRemoteMediator` (Remote Keys & cold migration) |
| `core/data/repository` | **80.00%** (252/315) | `MoviesRepositoryImpl` & `AuthRepositoryImpl` |

---

## 🚀 Pipeline de CI/CD & Automação de Release

O repositório é equipado com automação completa via **GitHub Actions** em dois fluxos complementares:

### 1. `PR Verification` (`.github/workflows/pr.yml`)
- Disparado em pull requests para a branch `main`.
- Executa em paralelo:
  - Análise estática de código com **Detekt** (0 code smells tolerados).
  - Verificação de Lint do Android (**Android Lint**).
  - Execução de toda a suíte de testes unitários JVM (`testDebugUnitTest`).
  - Compilação dos APKs debug e de release para validação de regras de ProGuard/R8.

### 2. `Main CD Pipeline` (`.github/workflows/main-cd.yml`)
- Disparado automaticamente no merge para `main`.
- **Job 1 (Verify):** Verificação de integridade e testes.
- **Job 2 (Build & Sign):** Decodifica keystore de release a partir de secrets base64, compila APK minificado com R8, gera o Android App Bundle (`.aab`) com `versionCode` dinâmico baseado em `${{ github.run_number }}` e arquiva mapeamentos de ProGuard.
- **Job 3 (Firebase Test Lab):** Executa testes instrumentados no Google Cloud em dispositivos Android virtuais/físicos.
- **Job 4 (Deploy & Release):**
  - Faz upload do APK para o **Firebase App Distribution** para o grupo de testadores (`testers`).
  - Cria tag Git anotada (ex: `v1.0.0-build.24`).
  - Publica um **GitHub Release** oficial anexando APK, AAB e mapeamentos R8.

---

## 🛠 Stack de Tecnologias & Bibliotecas

| Categoria | Tecnologias Utilizadas |
|---|---|
| **Linguagem & Runtime** | Kotlin 2.0.21, Java 17, Coroutines & Flow 1.9.0 |
| **Interface (UI)** | Jetpack Compose BOM 2024.12.01, Material 3, Compose Navigation 2.8.5, Coil 2.7.0 |
| **Persistência Local** | AndroidX Room 2.6.1 (SQLite, InvalidationTracker, KSP), DataStore Preferences |
| **Comunicação Remota** | Retrofit 2.11.0, OkHttp 4.12.0, Sandwich 2.0.10, KotlinX Serialization 1.7.3 |
| **Paginação & Listas** | AndroidX Paging 3 (Paging 3.3.5, Paging Compose, RemoteMediator) |
| **Injeção de Dependências** | Dagger Hilt 2.52 |
| **Segurança & Criptografia** | Android Keystore Provider, AES-256-GCM, Custom Tabs 1.8.0 |
| **Qualidade & Análise** | Detekt 1.23.7, Android Lint, KotlinX Kover 0.8.3 |
| **Testes & Benchmark** | JUnit 4, Robolectric 4.14.1, MockK 1.13.13, Google Truth 1.4.4, Turbine 1.2.0, AndroidX Benchmark Macro 1.3.3, UiAutomator 2.3.0 |
| **Processamento em Segundo Plano** | AndroidX WorkManager 2.9.1, Hilt Work 1.2.0 |
| **Cloud & DevOps** | Firebase App Distribution, Firebase Test Lab, Google Cloud SDK, GitHub Actions |

---

## 💻 Instruções de Compilação & Execução

### Pré-requisitos
- **Android Studio:** Ladybug (2024.2.1) ou superior.
- **JDK:** OpenJDK 17.
- **Android SDK:** Compile SDK 35, Build Tools 35.0.0.

### 1. Clonar o Repositório
```bash
git clone https://github.com/AdautoEC/marvel-comics-app.git
cd marvel-comics-app
```

### 2. Configurar Chaves de API
Crie ou edite o arquivo `local.properties` na raiz do projeto com sua chave gratuita do [TMDB](https://www.themoviedb.org/settings/api):
```properties
tmdb.api.key=SUA_TMDB_API_KEY_AQUI
tmdb.read.access.token=SEU_TMDB_READ_ACCESS_TOKEN_AQUI
```

### 3. Comandos Úteis do Gradle

```bash
# Executar análise estática com Detekt (todas as camadas, zero code smells)
./gradlew detekt

# Executar todas as suítes de testes unitários e de integração
./gradlew testDebugUnitTest

# Gerar relatórios de cobertura de código agregados com KotlinX Kover (XML e HTML)
./gradlew koverXmlReport koverHtmlReport

# Compilar módulo de Macrobenchmark
./gradlew :benchmark:assembleBenchmark

# Executar verificação de lint
./gradlew lintDebug

# Compilar APK de Debug
./gradlew assembleDebug

# Compilar APK e Bundle de Release minificados com R8
./gradlew assembleRelease bundleRelease

# Compilar APKs de Testes Instrumentados para Test Lab
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```

---

## 📄 Licença

Distribuído sob a licença Apache 2.0. Consulte [`LICENSE`](LICENSE) para mais detalhes.
