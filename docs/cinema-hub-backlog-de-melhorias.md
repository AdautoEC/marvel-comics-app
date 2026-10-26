# Cinema Hub — Backlog de melhorias (Compose, arquitetura, testes, build)

> **Base da análise:** repositório `AdautoEC/marvel-comics-app`, HEAD `19cbb2c` (25/10/2026).
> **Método:** leitura estática do código. Nada foi compilado nem executado; os itens marcados com 🔎 são inferidos da leitura e merecem uma confirmação rápida no emulador/IDE antes de virarem tarefa.
> **Legenda de esforço:** ⚡ ≤ 1 h · M ≈ meio dia · G ≈ 1 dia ou mais.

---

## Já resolvido (não refazer)

Actions fixadas por SHA e `permissions` mínimas · `DELETE /authentication/session` · token OAuth pendente persistido · pull sob o mutex (com teste) · `ExistingWorkPolicy.APPEND_OR_REPLACE` · falhas de sync visíveis na UI · `baseline-prof.txt` presente · badge de Detekt honesto ("0 active, 98 baselined") · PK composta, migrations 2→3, 3→4 e 4→5 testadas · busca sem coleta dupla de `PagingData`.

---

## Ordem de execução sugerida

| Fase | Itens | Por quê |
|---|---|---|
| 1 | B1, B2, B3, B4, B5 | Mudam o contrato `ViewModel` ↔ `Screen` e destravam previews e testes |
| 2 | B6, C3 | Textos e nomes: mudanças mecânicas, melhor fazer antes de escrever mais testes |
| 3 | D1–D8 | Reforma dos testes, já sobre o contrato novo |
| 4 | B7–B14, C1, C2 | Tema, grade adaptativa, performance, acessibilidade e camadas |
| 5 | E1–E4 | Dependências, CI e documentação |

---

## B. Compose e UI

### B1 — Tirar o `Flow<PagingData>` do `UiState` · M
**Problema.** `MovieListUiState` carrega `movies: Flow<PagingData<MovieSummary>>` (default `emptyFlow()`) e está anotado `@Immutable`, mas um `Flow` não é imutável. A `Screen` ainda coleta esse flow (`uiState.movies.collectAsLazyPagingItems()`), então não é realmente stateless.

**Como.**
```kotlin
// MovieListContract.kt — sem o Flow, sem @Immutable enganoso
data class MovieListUiState(
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedCategory: MovieCategory = MovieCategory.POPULAR,
    val sessionState: SessionState = SessionState.Guest,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val userMessage: UiText? = null,
)

// MovieListViewModel.kt
val movies: Flow<PagingData<MovieSummary>> = moviesFlow.cachedIn(viewModelScope)

// MovieListRoute.kt
val movies = viewModel.movies.collectAsLazyPagingItems()
MovieListScreen(uiState = uiState, movies = movies, onAction = viewModel::onAction)

// MovieListScreen.kt — recebe LazyPagingItems, não coleta nada
@Composable
fun MovieListScreen(
    uiState: MovieListUiState,
    movies: LazyPagingItems<MovieSummary>,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier,
)
```
A constante `attribution` pode sair do estado e virar um default do footer (é estática).

**Aceite.** `MovieListUiState` não referencia `Flow`/`PagingData`; `MovieListScreen` não chama `collectAs…`; testes de tela passam `LazyPagingItems` direto.

### B2 — Previews reais · M
**Problema.** `MovieListContentPreview` desenha só `Text("Cinema Hub Preview (...)")`. Os 15 estados do `PreviewParameter` previsualizam um placeholder e ainda são "testados" por `MovieListPreviewProviderTest`.

**Como.** Depois do B1, o preview constrói os itens de verdade:
```kotlin
@Composable
private fun MovieListScreenPreview(@PreviewParameter(Provider::class) p: MovieListPreviewState) {
    MarvelTheme {
        val items = flowOf(PagingData.from(p.movies)).collectAsLazyPagingItems()
        MovieListContent(uiState = p.uiState, movies = items, onAction = {})
    }
}
```
Para loading/erro, use `PagingData.from(emptyList(), sourceLoadStates = LoadStates(...))`.

**Aceite.** Cada estado do provider renderiza grid, shimmer, erro ou vazio de verdade no Android Studio.

### B3 — Uma única fonte de verdade no ViewModel · M
**Problema.** `_selectedCategory`, `_searchQuery` e `_isSearchActive` espelham campos de `_uiState` e são atualizados em dupla; podem divergir.

**Como.** Derivar do estado:
```kotlin
private val category = _uiState.map { it.selectedCategory }.distinctUntilChanged()
private val query = _uiState.map { it.searchQuery.trim() }.distinctUntilChanged()
```
e alimentar o `combine(category, query)` existente. As ações só chamam `_uiState.update { … }`.

**Aceite.** Existe um único `MutableStateFlow` de estado de tela; nenhum `_xxx.value = …` duplicado nas ações.

### B4 — Debounce da busca · ⚡
**Problema.** Cada tecla recria o Pager via `flatMapLatest`.

**Como.** `@OptIn(FlowPreview::class)` e, no fluxo da query, `.debounce { if (it.isEmpty()) 0L else 300L }` — só no fluxo que alimenta o Pager; o texto do campo continua atualizando imediatamente.

**Aceite.** Teste com `runTest`/`advanceTimeBy`: 5 teclas em 100 ms geram 1 consulta.

### B5 — Um único canal de mensagens · ⚡
**Problema.** A mesma mensagem vai por `userMessage` (estado) e por `MovieListEffect.ShowMessage` (efeito); a `Route` ignora o efeito (`ShowMessage -> Unit`), que é código morto.

**Como.** Escolha um. Recomendo o efeito `Channel` para mensagens transitórias (snackbar), removendo `userMessage` e `UserMessageDismissed`; a `Route` repassa ao `SnackbarHostState`.

**Aceite.** Sem `ShowMessage -> Unit`; cada mensagem tem exatamente um caminho.

### B6 — Textos fixos para `strings.xml` · M
**Problema.** ~25 textos em português no código de produção: `UserSessionPill`, `GuestSyncBanner`, `MovieListTopBar` ("CINEMA HUB", `contentDescription`), `MovieDetailScreen` ("Sinopse", "Gêneros", "Elenco Principal", "Voltar", "Quero Assistir"), `ErrorState` (default "Ocorreu um erro"), `formatPagingError` e o mapeamento de erros dos ViewModels (`UiText.Dynamic("...")`).

**Como.**
- Mova tudo para `strings.xml` (e `values-en` se quiser i18n).
- Nos ViewModels, troque `UiText.Dynamic("…")` por `UiText.Resource(R.string.…)`; textos dinâmicos de verdade (nome do usuário) ficam com `Dynamic`.
- Para não regredir: um passo de CI que falhe se encontrar `Text("` ou `contentDescription = "` literal em `src/main` (grep simples ou regra custom do Detekt).

**Aceite.** `grep -rn 'Text("' */src/main` retorna vazio; o app troca de idioma sem texto fixo.

### B7 — Usar o tema · M
**Problema.** Os componentes usam `MarvelColors.*` direto e só existe `darkColorScheme`; `LocalSpacing` é provido, mas o grid usa `16.dp` literais.

**Como.**
- Troque `MarvelColors.DarkBackground` → `MaterialTheme.colorScheme.background`, `TextPrimary` → `onBackground`, etc.
- Troque `16.dp` → `MarvelTheme.spacing.md` (tokens já existem).
- Opcional: `lightColorScheme` e cores dinâmicas.

**Aceite.** `grep MarvelColors */src/main` só encontra o arquivo do tema.

### B8 — Grade adaptativa · ⚡
**Problema.** `GridCells.Fixed(2)` em `MovieListGrid` e no shimmer grid: tablet e paisagem ficam com 2 colunas enormes.

**Como.** `GridCells.Adaptive(minSize = 160.dp)` nos dois grids.

**Aceite.** Preview `@Preview(device = Devices.TABLET)` mostra mais colunas.

### B9 — Callbacks em efeitos e defaults perigosos · ⚡
**Problema.** `onOpenDetail`/`onBackClick` são capturados num `LaunchedEffect(viewModel.effects, lifecycleOwner)`; se o lambda mudar entre recomposições, o efeito usa o antigo. No detalhe, `onBackClick: () -> Unit = {}` esconde erro de ligação.

**Como.**
```kotlin
val currentOnOpenDetail by rememberUpdatedState(onOpenDetail)
LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.effects.collect { if (it is OpenDetail) currentOnOpenDetail(it.id) }
    }
}
```
E remova o `= {}` do `MovieDetailRoute`.

**Aceite.** O compilador força quem chama a passar `onBackClick`.

### B10 — Quebrar o `MovieDetailContent` (~250 linhas) · M
**Problema.** Uma composable gigante (entrada no baseline do Detekt).

**Como.** Extraia `DetailHeader`, `DetailActions`, `DetailOverview`, `GenreChips`, `CastSection` (já existe `CreatorsSection`). Cada uma recebe só o que usa. Remova a entrada correspondente do `detekt-baseline.xml`.

**Aceite.** Nenhuma composable passa de ~80 linhas; baseline diminui.

### B11 — Estabilidade do Compose em todos os módulos · M
**Problema.** O `stabilityConfigurationFile` só está em `app/build.gradle.kts`. `core:ui`, `core:designsystem` e `feature:*` aplicam o plugin do Compose e compilam separado, sem a config. Listas no estado (gêneros, elenco) são `List`.

**Como.**
- Aplique `stabilityConfigurationFile` e os relatórios em cada módulo com Compose (de preferência numa convention plugin), no mesmo estilo já usado no `:app`:
  ```kotlin
  composeCompiler {
      stabilityConfigurationFile = rootProject.layout.projectDirectory.file("app/stability_config.conf")
      reportsDestination = layout.buildDirectory.dir("compose_compiler")
      metricsDestination = layout.buildDirectory.dir("compose_compiler")
  }
  ```
- Use `ImmutableList` (kotlinx.collections.immutable) nas listas dos estados; se a dependência está declarada sem uso, use ou remova.
- Rode os relatórios e revise classes `unstable` e composables não `skippable`.

**Aceite.** Relatório sem composables de tela `restartable but not skippable` por causa de tipos do projeto.

### B12 — Shimmer na fase de desenho 🔎 · M
**Problema.** Se `Shimmer.kt` ainda lê o valor animado na composição, cada card tem sua transição infinita e recompõe a cada quadro.

**Como.** Uma `rememberInfiniteTransition` no host da grade, passada por `CompositionLocal` como `State<Float>`; o modifier lê `progress.value` **dentro** de `drawWithCache { onDrawBehind { … } }`.

**Aceite.** Layout Inspector: contagem de recomposição do shimmer ≈ 0 durante a animação.

### B13 — Spinner do pull-to-refresh 🔎 · ⚡
**Problema.** `isRefreshing = uiState.isRefreshing || (refresh is Loading && itemCount > 0)` também acende quando o Pager é recriado (trocar categoria/busca).

**Como.** Derive o indicador só do refresh pedido pelo usuário (flag do ViewModel), não de qualquer `LoadState.refresh`.

**Aceite.** Digitar na busca não mostra o spinner de pull-to-refresh.

### B14 — Acessibilidade · M
**Como.**
- Itens clicáveis com `Modifier.semantics { role = Role.Button; onClickLabel = … }` (ou `clickable(onClickLabel, role)`).
- `contentDescription` de ícones vindo de `stringResource`.
- Um teste de tela com `enableAccessibilityChecks()`.
- Verifique com TalkBack e fonte em 200%.

**Aceite.** Teste de acessibilidade passando; nenhum alvo de toque < 48 dp.

---

## C. Arquitetura

### C1 — URL de autenticação no repositório · ⚡
**Problema.** O `MovieListViewModel` monta a URL (`TMDB_AUTH_BASE_URL/…?redirect_to=…`) por concatenação e sem encode.

**Como.** `AuthRepository.buildAuthorizationUrl(): Outcome<String>`, usando `HttpUrl.Builder().addPathSegments(...).addQueryParameter("redirect_to", REDIRECT)` (encoda corretamente). O ViewModel só dispara o efeito `LaunchTmdbAuth(url)`.

**Aceite.** O ViewModel não importa constantes do TMDB; teste do repositório valida a URL gerada.

### C2 — Fronteira entre domínio e dados · M
**Problema.** Interface e implementação do repositório vivem em `core:data`; a camada de apresentação depende do módulo de implementação.

**Como (escolha uma e registre no ADR, E4).**
- (a) Mover interfaces para `:core:domain` (módulo Kotlin puro) e fazer `feature:*` dependerem só dele; o Hilt liga a implementação em `:app`.
- (b) Manter como está, documentando a troca. Crie UseCases só onde houver regra (ex.: "alternar favorito + decidir sync").

**Aceite.** Decisão escrita e consistente com as dependências do Gradle.

### C3 — Nomes · M
**Problema.** `Watched` vs `Watchlist` convivem (`WatchedToggled`, `toggleWatched`, `observeWatched()` serve `MovieCategory.WATCHLIST`); `Marvel*` (`MarvelTheme`, `MarvelApp`, `MarvelColors`, namespace `com.adauto.marvel`) numa app que se chama Cinema Hub.

**Como.** Renomeie com o IDE (Rename Refactor): `Watched*` → `Watchlist*`; `Marvel*` → `CinemaHub*`; namespace para `com.adauto.cinemahub`. Faça em um commit só, sem mudar comportamento. Migrações do Room continuam válidas (nomes de tabela não mudam sem `@Entity(tableName)`).

**Aceite.** `grep -ri marvel` só acha o histórico e o texto de atribuição, se houver.

---

## D. Testes

### D1 — Apagar testes que não pegam regressão · ⚡
**Problema.** Eles inflam a contagem e a cobertura: `ThemeTokensTest` (31 asserções de que cores são iguais a elas mesmas), `MovieListPreviewProviderTest`, `MovieDetailPreviewProviderTest`, `PreviewAnnotationsTest`, `UiPreviewProvidersTest`, `CoroutinesModuleTest`, `RoutesTest`, `MovieCardModelTest`, e o `assertNotNull(json)` do `NetworkModuleTest`.

**Como.** Delete. Do `NetworkModuleTest`, mantenha só as asserções de configuração que importam (timeouts, presença do `Authenticator`).

**Aceite.** Contagem de testes cai e nenhum comportamento deixa de ser coberto.

### D2 — Testes de repositório por estado, não por interação · G
**Problema.** `MoviesRepositoryImplTest` tem ~34 `verify`, DAOs `relaxed` e `withTransaction` mockado só para executar o bloco. Ele comprova "chamou `upsert`/`delete`", não que o banco terminou certo; refatorar a escrita para batch quebra testes sem mudar comportamento.

**Como.** Room em memória (Robolectric, já usado nos DAOs) + um `FakeTmdbApi` (ou `MockWebServer`); assertar o estado final:
```kotlin
@Test fun `pull não apaga favorito com mutação pendente`() = runTest {
    repo.toggleFavorite(movie)                       // cria linha local + outbox
    api.favorites = listOf()                         // remoto ainda sem o filme
    repo.syncCloudWithLocal()
    assertThat(db.favoriteDao().getAllFavorites().first().map { it.movieId }).contains(movie.id)
}
```
Cenários mínimos: toggle autenticado vs visitante, reconciliação (insere remoto, remove obsoleto, preserva pendente), falha de API não altera o banco, logout limpa outbox.

**Aceite.** Nenhum `mockk` de DAO nos testes do repositório; refatorar a escrita não quebra testes.

### D3 — Testes de Compose na JVM (PR) · M
**Problema.** Todo teste de Compose é instrumentado e só roda no Test Lab do CD (o PR não os executa).

**Como.** Em cada módulo de UI:
```kotlin
android { testOptions { unitTests { isIncludeAndroidResources = true } } }
dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```
Mova para `src/test` os testes de componente e de tela (`createComposeRule()` + `@RunWith(AndroidJUnit4::class)` + Robolectric). Deixe 3–5 testes no device (fluxo real, Keystore, Custom Tab).

**Aceite.** `./gradlew testDebugUnitTest` executa os testes de UI; o PR passa a rodá-los.

### D4 — "E2E" que de fato integre · M
**Problema.** `MarvelAppNavigationE2ETest` monta um `NavHost` próprio com lambdas escritas à mão e estado fixo: sem `MarvelNavHost`, sem ViewModel e sem Hilt. Não pega erro de ligação.

**Como.** Use `createAndroidComposeRule<MainActivity>()` com `@HiltAndroidTest` e `@TestInstallIn` trocando os repositórios por fakes; navegue lista → detalhe → voltar pelo `MarvelNavHost` real. Se não quiser fazer isso, renomeie para `NavigationTest`.

**Aceite.** Quebrar a rota ou o argumento `movieId` no `MarvelNavHost` faz o teste falhar.

### D5 — Teste de restauração que teste restauração · ⚡
**Problema.** `stateRestoration_preserves…` (lista e detalhe) passa o conteúdo por argumento; passariam mesmo sem nenhum estado salvo.

**Como.** Apague, ou teste algo restaurável de verdade (posição de scroll do `rememberLazyGridState`, texto digitado com `rememberSaveable`), conferindo o valor *depois* de `emulateSavedInstanceStateRestore()`.

**Aceite.** Remover o `rememberSaveable` correspondente faz o teste falhar.

### D6 — Cobrir o que falta · M
**Cenários sem teste:** erro de refresh com retry; vazio por categoria (favoritos, watchlist, busca sem resultado); favoritar pelo card; pull-to-refresh; erro no append com retry; `formatPagingError` (401, 429, outros); tela de detalhe com erro e retry.

**Aceite.** Cada cenário tem um teste que falha se o comportamento for quebrado.

### D7 — Módulo `:core:testing` com fakes · M
**Como.** Crie `FakeMoviesRepository`, `FakeAuthRepository`, `FakeNetworkMonitor`, `MainDispatcherRule` e fábricas de `MovieSummary`/`MovieDetail`. Use nos testes de ViewModel, de UI e do E2E (D4).

**Aceite.** Testes de ViewModel e de UI não usam `mockk(relaxed = true)`.

### D8 — Validar que os testes pegam bugs (mutação manual) · M
**Como.** Quebre de propósito 5 comportamentos e confirme que ao menos um teste falha em cada caso, por exemplo:
1. Inverter a condição de "preservar item pendente" na reconciliação.
2. Trocar `desired` por `!desired` no worker.
3. Remover o `trim()` ou o filtro case-insensitive da busca.
4. Não limpar o outbox no logout.
5. Remover o `key` do `items(...)` do grid.

Opcional: Pitest/Arcmutate na JVM.

**Aceite.** 5/5 mutações detectadas; as que escaparem viram testes novos.

---

## E. Build, CI, dependências e documentação

### E1 — Atualizar dependências em blocos · G
**Problema.** Tudo de out/2024: AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01, Hilt 2.52, Room 2.6.1, Paging 3.3.2, Coil 2.7.0, Firebase BoM 33.5.1. Não há Renovate nem Dependabot.

**Como.**
- Adicione `.github/dependabot.yml` (ecossistema `gradle`, agrupando por família; confirme suporte ao version catalog) ou `renovate.json`.
- Atualize em PRs separados, cada um com CI verde:
  1. AGP + Kotlin + KSP + Compose BOM + Hilt + Room + Paging
  2. Firebase BoM
  3. Coil 3 (muda o pacote de import)
- Se o Compose compiler plugin mudar de API, ajuste o B11.

**Aceite.** Versões atuais, um PR por bloco, sem regressão de testes.

### E2 — Test Lab só em release · M
**Problema.** Cota gratuita pequena; o CD bloqueia quando estoura (`override_test_lab`).

**Como.** Com o D3, o PR roda UI na JVM. Mantenha o Test Lab para candidatos a release (ou noturno) e o `workflow_dispatch` com override auditado.

**Aceite.** Estourar a cota do Test Lab não bloqueia merges normais.

### E3 — Benchmark com número medido · M
**Como.** Rode o Macrobenchmark (`StartupTimingMetric`, `FrameTimingMetric`) num aparelho real com o build `benchmark`, gere/atualize o `baseline-prof.txt` e publique no README só o que foi medido (aparelho, versão, mediana e p95).

**Aceite.** README com tabela de medições reproduzível; sem metas aspiracionais.

### E4 — ADRs e documentação de decisões · ⚡
**Como.** Recrie `docs/adr/` (o `docs/` já não é ignorado) com: chave TMDB via Remote Config + Keystore e seus limites (extraível por quem controla o aparelho), outbox + WorkManager e política de falha, App Check, perda de dados no logout, decisão do C2 (camadas) e a política de testes (D2/D3). Um arquivo curto por decisão: contexto, decisão, consequências.

**Aceite.** Cada decisão acima tem um ADR; o README aponta para eles.

---

## Definição de pronto (para cada PR deste backlog)

- [ ] Detekt sem achados novos (`maxIssues: 0` com baseline por módulo)
- [ ] Testes novos assertam **estado/comportamento**, não só chamadas
- [ ] Nenhum texto fixo novo em `src/main`
- [ ] Nenhuma cor/espaçamento literal fora do tema
- [ ] README e ADRs atualizados quando a decisão mudar
- [ ] Sem código morto (efeitos sem consumidor, parâmetros sem uso)

---

*Documento gerado a partir da avaliação do HEAD `19cbb2c`. As seções de credibilidade (README/CI/Kover) foram removidas a pedido.*
