# ADR 0006: Estratégia de Testes por Estado e Execução de UI na JVM

## Status
Aceito

## Contexto
A estratégia anterior de testes apresentava três problemas críticos:
1. **Acoplamento a Implementações:** Testes de repositório e workers continham dezenas de `verify { dao.upsert(...) }` com mocks em cascata (`mockk(relaxed = true)`) e `withTransaction` simulado, testando chamadas a métodos em vez do resultado persistido no banco de dados. Qualquer alteração em queries quebrava os testes sem que houvesse bug real, ou pior: bugs de lógica passavam despercebidos porque mocks relaxados retornavam valores padrão silenciosamente.
2. **Lentidão e Bloqueio no CI:** Todos os testes de componentes Compose eram executados como testes instrumentados em emuladores remotos do Firebase Test Lab, estourando rapidamente as cotas gratuitas e bloqueando o fluxo de integração contínua (CD).
3. **Testes Tautológicos:** Testes que apenas comparavam constantes e tokens de cores consigo mesmos inflavam métricas de cobertura sem detectar regressões de negócio.

## Decisão
1. **Eliminação de Testes Tautológicos:** Remoção de testes vazios de constantes e annotations que geravam falsa sensação de segurança.
2. **Testes de Repositório e Worker Baseados em Estado:** Reescrever a suíte de persistência e sincronização (`MoviesRepositoryImplTest`, `MoviesRepositoryReactiveTest`, `MoviesRepositorySearchTest`, `AuthRepositoryTest`, `AuthRepositoryLogoutTest`, `SyncOutboxWorkerTest`) utilizando instâncias reais de Room SQLite em memória gerenciadas pelo Robolectric (`Room.inMemoryDatabaseBuilder`) + `TestListenableWorkerBuilder` / `WorkManagerTestInitHelper` + `FakeTmdbApi`. Validar o estado final das tabelas e do servidor fake após operações concorrentes, reconciliação e sincronização de outbox, eliminando mocks frágeis de DAOs.
3. **Módulo Central de Fakes (`:core:testing`):** Centralizar implementações de fakes previsíveis (`FakeMoviesRepository`, `FakeAuthRepository`, `FakeNetworkMonitor`, `FakeSyncFailureStorage`, `MainDispatcherRule`), reduzindo drasticamente o uso de `mockk(relaxed = true)` em mais de 75% no repositório (de 63 ocorrências para 15, restritas a integrações de plataforma como `ConnectivityManager`, `CustomTabsIntent` e `FirebaseApp`). Zero testes de `ViewModel` ou `UseCase` utilizam mocks de repositórios.
4. **Testes de UI Compose na JVM com Robolectric e Verificação Semântica/Acessibilidade:** Migrar e expandir os testes de tela e de componentes Compose (`MovieListScreenRobolectricTest`, `MovieDetailScreenRobolectricTest`) em `src/test` nos módulos de feature, cobrindo estados de erro de paginação (`append` / `refresh`), busca, pull-to-refresh, snackbar de falha de sincronização (`SyncEventBus`), preservação de identidade de nós (`itemKey`) e auditoria automática de acessibilidade (roles, labels/contentDescriptions e touch targets mínimos de `48.dp`).
5. **Contrato de Grafo de Navegação (`NavigationTest`):** Exercitar diretamente o `CinemaHubNavHost` real, validando a serialização/desserialização type-safe de `MovieDetailRoute(movieId)` no `SavedStateHandle` e a preservação da pilha de navegação.
6. **Reserva do Firebase Test Lab:** Restringir a execução de testes em dispositivos físicos no Firebase Test Lab exclusivamente a pipelines de release ou acionamento manual controlado.

## Prova de Mutação Manual (5/5 Mutações Detectadas)
Para comprovar empiricamente que a suíte baseada em estado detecta regressões reais de negócio, 5 mutações cirúrgicas foram introduzidas no código de produção e verificadas contra a suíte:

| # | Alvo da Mutação | Mutação Introduzida | Teste que Detectou (Falhou) | Resultado |
|---|---|---|---|---|
| **1** | `MoviesRepositoryImpl.reconcileFavorites` | Remover a guarda `localFav.movieId !in pendingFavs` ao deletar favoritos locais ausentes no remoto | `MoviesRepositoryImplTest.pull_preservesLocalFavorite_withPendingOutboxMutation` | **Detectado** (`AssertionError` na linha 252) |
| **2** | `SyncOutboxWorker.executeApiCall` | Inverter `favorite = item.desired` para `favorite = !item.desired` no envio ao TMDB | `SyncOutboxWorkerTest.whenSyncFavoriteSucceeds_updatesRemoteAndDeletesOutboxRow` e `whenNewerMutationArrivesDuringPush_preservesNewerOutboxRowAndSyncsDesiredState` | **Detectado** (`AssertionError` nas linhas 139 e 178) |
| **3** | `MoviesRepositoryImpl.movies` | Remover `.trim()` da query antes de consultar `movieDao().searchPagingSource` | `MoviesRepositorySearchTest.movies_withSearchQuery_filtersByTitleCaseInsensitiveAndTrimmedInRoom` | **Detectado** (`AssertionError` na linha 74) |
| **4** | `AuthRepositoryImpl.logout` | Remover `database.syncOutboxDao().clearAll()` da transação de limpeza no logout | `AuthRepositoryLogoutTest.logout_cancelsWorker_clearsOutboxAndLocalTables_andClearsSessionAndAccountId` | **Detectado** (`AssertionError` na linha 91) |
| **5** | `MovieListGrid` | Remover `key = movies.itemKey { it.id }` do bloco `items(...)` do `LazyVerticalGrid` | `MovieListScreenRobolectricTest.movieListGrid_preservesItemNodeIdentityAcrossPrependWhenKeyProvided` | **Detectado** (`AssertionError` na linha 417) |

## Consequências
- **Positivas:**
  - Redução drástica no tempo de execução do CI de Pull Requests (de ~20 minutos para < 3 minutos).
  - Testes de repositório e worker 100% baseados em estado real do SQLite, imunes a refatorações internas e comprovados contra 5/5 mutações de lógica.
  - Testes de acessibilidade, clique, busca, paginação e estados de tela verificados automaticamente na JVM a cada commit antes do merge.
  - Preservação da cota mensal do Firebase Test Lab para validação final de release em hardware real.
