# Marvel Comics Catalog — Plano técnico completo

App Android nativo (Kotlin + Jetpack Compose) que lista quadrinhos da Marvel na tela inicial, com paginação infinita, e mostra o detalhe de cada HQ. Este documento substitui o `shortenlinks-analise-e-plano-novo-app.md` e incorpora as decisões tomadas na conversa.

> **Escopo deliberado:** sem backend próprio. O foco é o app. Onde a segurança tem limite por causa disso, o limite está declarado (seção 4 e ADR-001).

---

## 1. Decisões já tomadas

| # | Decisão | Motivo |
|---|---------|--------|
| D1 | **Sem backend/proxy.** A chave Marvel vive no app (caminho "B"). | Foco no app. Risco aceito e registrado no ADR-001. |
| D2 | **Paging 3 + Room + `RemoteMediator`.** | A API pagina por `offset`/`limit`, e a cota diária torna o cache obrigatório. |
| D3 | **Firebase:** Remote Config, Crashlytics, Performance Monitoring. | O Remote Config é a **origem da chave** (primeira execução e rotação) e leva flags; Crashlytics e Performance dão telemetria. |
| D4 | **Keystore (AES-GCM) para guardar, em repouso, a chave baixada do Remote Config.** | Depois do primeiro fetch, a chave fica cifrada no aparelho e o app funciona offline. Eleva o custo de extração em repouso. Não é cofre. |
| D5 | **`google-services.json` em secret do GitHub (base64).** | Fora do repositório. |
| D6 | **Pipeline PR + release** com testes automatizados antes de publicar. | Ver seção 9. |
| D7 | **KDoc obrigatório e imposto por ferramenta.** | No shortenLinks boa parte do KDoc só repetia o nome. |
| D8 | **Compose otimizado para recomposição**, medido em build release. | Seção 8. |
| D9 | **Android-only** (Hilt, Room, Retrofit; sem KMP). | Foco no app; KMP só se surgir gatilho real. |
| D10 | **UDF com contrato por tela** (`UiState`/`UiAction`/`UiEffect`), `Route` × `Screen`, efeitos sem perda. | Seção 6.4. |
| D11 | **Release correto desde o commit 1** (R8, assinatura, manifesto, timeouts). | Seção 10.5. |
| D12 | **Design system enxuto com tokens completos desde o dia 1; componentes só com reuso real ou centralização** (imagem, estados, atribuição). | Evita duplicação sem repetir o erro dos 13 módulos. Seção 6.6. |

---

## 2. Correções ao plano original

1. **Link errado.** `marvelapp.com` é a ferramenta de prototipagem Marvel, não a API de quadrinhos. A API oficial é a do **developer.marvel.com**.
2. **Remote Config não é cofre.** A documentação do Firebase avisa que o usuário final consegue acessar qualquer valor baixado ou padrão. Aqui ele é a **origem da chave** (primeira execução e rotação) e o **kill-switch**, não um cofre.
3. **Keystore protege em repouso, não em uso.** A chave chega em texto claro ao app e precisa ser usada para assinar. Root, Frida ou proxy de inspeção a alcançam. Veja a seção 4.
4. **HTTPS.** Os exemplos da documentação usam `http://gateway.marvel.com`. O Android bloqueia cleartext por padrão, então use `https://gateway.marvel.com`.
5. **Nunca logar a URL completa.** `apikey` e `hash` vão na query e precisam ser redigidos.

---

## 3. Visão geral do produto

### 3.1 Funcionalidades (v1)

- **Lista** de HQs em grid, com scroll infinito (Paging 3), pull-to-refresh e banner offline.
- **Detalhe** do quadrinho: capa, título, número da edição, descrição, criadores, personagens, páginas, formato, data de venda, preço e link para a página oficial.
- **Offline-first:** o Room é a fonte de verdade; a UI só lê do banco.
- **Atribuição Marvel** visível (texto `attributionText` devolvido pela API).
- **Tema claro/escuro, edge-to-edge, pt-BR e en.**

### 3.2 Fora do escopo da v1 (roadmap)

Busca por título (`titleStartsWith`), filtros (formato, ano), favoritos, widgets, personagens/séries como telas próprias.

### 3.3 Termos da Marvel (confirme no portal antes do release)

- Exibir a atribuição que a API devolve.
- Não usar o logo da Marvel no ícone nem no nome do app, para não sugerir afiliação.
- Conferir se há limite de tempo de cache ou de armazenamento de dados.
- Cota diária de chamadas: artigos de terceiros citam **3.000/dia**; confirme no portal.

---

## 4. Segurança da chave (caminho B, sem backend)

### 4.1 Premissa honesta

Sem servidor, **nada impede a extração da chave**. Qualquer segredo que o app consegue usar, quem controla o aparelho também consegue. O objetivo é **elevar o custo do ataque, limitar o estrago e poder reagir**.

O attestation (App Check, Play Integrity) também perde a utilidade como barreira: a API da Marvel é de terceiros e não valida esses tokens. Play Integrity chamado só no cliente é **telemetria**, não defesa.

### 4.2 Defesa em camadas

| Camada | O que fazer | Limite |
|--------|-------------|--------|
| Chave dedicada | Uma chave só deste app. Pior caso: perder a cota. | — |
| Fora do repo e do APK | Nada de chave em `strings.xml`, `BuildConfig` versionado ou defaults do Remote Config. | O Remote Config pode ser consultado por quem tem o `apiKey`/`appId` do `google-services.json`. |
| Remote Config | **Origem da chave:** entrega o par de chaves na primeira execução e as rotaciona depois, em um parâmetro JSON (`marvel_keys`, com `keyVersion`). | Não é segredo. |
| Keystore em repouso | `KeystoreCipher` com AES-GCM, chave não exportável (StrongBox quando houver); resultado no DataStore. | Protege o arquivo em disco, não a memória. |
| Janela curta em memória | O interceptor decifra, calcula o hash e descarta o valor. | O hash sai pela rede e um proxy o vê. |
| R8 | Minify e shrink de recursos ligados no release. | Ofuscação atrasa, não impede. |
| Sinais de ambiente hostil | Root, debugger e emulador no release, ou Play Integrity pelo cliente. | **Só telemetria:** o atacante controla o veredito local. |
| TLS pinning (opcional) | Somente com *backup pins* e kill-switch no Remote Config. | Pinning mal planejado derruba o app na renovação do certificado. Decida no ADR. |
| Rotação e versão mínima | Remote Config entrega chave nova e `min_supported_version` força atualização. | Depende de a versão vazada estar identificável. |
| Menos chamadas | Cache agressivo e `initialize()` do mediator que evita refresh desnecessário. | — |
| Monitoramento | Acompanhe o consumo no portal Marvel; alerta se destoar do tráfego esperado. | Manual. |

### 4.3 Fluxo de chaves

O **Remote Config é a única origem** da chave. O DataStore (cifrado) é só o cache local dela, para que o app funcione offline depois do primeiro fetch.

```
                    ┌──────────── 1ª execução (sem chave salva) ───────────┐
Remote Config ──fetch──▶ MarvelKeyProvider ──encrypt(AES-GCM/Keystore)──▶ DataStore
      ▲                         ▲                                              │
      │ rotação (background,    └────────── decrypt (só na hora de assinar) ◀──┘
      └─ keyVersion nova)                       execuções seguintes: sem rede
```

**Primeira execução (sem chave salva)**
1. O interceptor pede a chave ao `MarvelKeyProvider`.
2. O provider faz `fetchAndActivate` no Remote Config e **suspende até terminar**, com timeout (sugestão: 10 s). A primeira chamada à Marvel **espera** a chave; não dá para assinar sem ela.
3. Ele lê `marvel_keys`, cifra com o `KeystoreCipher` e grava no DataStore.
4. O interceptor assina a requisição e a chamada segue.
5. Falha de rede ou timeout: o provider devolve `null`, o interceptor lança `MarvelKeysUnavailableException`, e a UI mostra o estado próprio "Não foi possível iniciar o catálogo. Conecte-se à internet." com **retry**.

**Execuções seguintes (chave já salva)**
1. O provider lê do DataStore e decifra só na hora de assinar. **Nada bloqueia e nada depende de rede.**
2. Em segundo plano, o app consulta o Remote Config respeitando o intervalo mínimo de fetch. Se vier `keyVersion` maior que a salva, a chave é substituída (cifrar, gravar, trocar). É assim que a rotação acontece sem lançar versão.

**Chave inválida em uso (401 da Marvel)**
Um `Authenticator` do OkHttp força **um** `fetchAndActivate` imediato (ignorando o intervalo mínimo), salva a chave nova se houver e repete a chamada **uma** vez. Se falhar de novo, vira `DomainError.InvalidKey`.

**Chave do Keystore invalidada** (ex.: restauração de backup ou mudança de lock screen)
A decifragem lança `GeneralSecurityException`. O provider apaga o blob salvo e trata como "sem chave salva": volta ao fluxo da primeira execução.

**Regras**
- **Sem default no XML do Remote Config** para `marvel_keys`; o valor nunca vai no APK.
- Intervalo mínimo de fetch: 0 em debug, algo como 12 h em release. A primeira busca sem valor em cache sempre vai à rede.
- **`androidx.security:security-crypto`:** confira o status da biblioteca antes de adotar (há histórico de depreciação). O plano usa `javax.crypto` + `AndroidKeyStore` diretamente, sem depender dela.

```kotlin
/**
 * Fornece as chaves da Marvel, vindas do Remote Config e guardadas cifradas no aparelho.
 *
 * [current] devolve a chave salva sem tocar na rede. Se não houver chave, busca no Remote Config
 * e **suspende** até concluir ou estourar [fetchTimeout]; nesse caso devolve `null`.
 * Nunca chamar na thread principal sem suspender: é seguro em corrotinas e no interceptor do OkHttp.
 */
public interface MarvelKeyProvider {
    /** Chave atual, ou `null` se não há chave salva e o fetch falhou. */
    public suspend fun current(): MarvelKeys?

    /** Força um fetch imediato (usado pelo `Authenticator` após 401). Devolve `true` se a chave mudou. */
    public suspend fun forceRefresh(): Boolean

    /** Aplica em segundo plano uma `keyVersion` mais nova, se houver. Falhas são silenciosas. */
    public suspend fun refreshIfNewer()
}
```

### 4.4 `KeystoreCipher` (esqueleto)

```kotlin
/**
 * Cifra e decifra blobs com AES-256-GCM usando uma chave não exportável do AndroidKeyStore.
 *
 * O IV (12 bytes) é gerado a cada [encrypt] e anexado ao início do resultado.
 * Thread-safe: cada chamada cria seu próprio [Cipher].
 *
 * @throws GeneralSecurityException se a chave foi invalidada (ex.: mudança de biometria/lock screen);
 * o chamador deve tratar como "sem chave salva" e buscar de novo no Remote Config.
 */
internal class KeystoreCipher(private val alias: String = "marvel_keys_v1") {

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .build()
        return try {
            gen.init(spec(strongBox = true)); gen.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            gen.init(spec(strongBox = false)); gen.generateKey()
        }
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        }
        return cipher.doFinal(blob, 12, blob.size - 12)
    }
}
```

### 4.5 Assinatura (`ts`, `apikey`, `hash`)

`hash = md5(ts + privateKey + publicKey)`, enviado como query junto com `ts` e `apikey`.

```kotlin
/** Calcula o hash exigido pela API Marvel: md5(ts + privateKey + publicKey), em hex minúsculo. */
internal fun marvelHash(ts: String, privateKey: String, publicKey: String): String =
    MessageDigest.getInstance("MD5")
        .digest("$ts$privateKey$publicKey".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

internal class MarvelAuthInterceptor(
    private val keys: MarvelKeyProvider,
    private val clock: Clock,           // injetável: testes usam relógio fixo
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Roda na thread do OkHttp (nunca na main), então runBlocking é aceitável aqui.
        val k = runBlocking { keys.current() } ?: throw MarvelKeysUnavailableException()
        val ts = clock.millis().toString()
        val url = chain.request().url.newBuilder()
            .addQueryParameter("ts", ts)
            .addQueryParameter("apikey", k.publicKey)
            .addQueryParameter("hash", marvelHash(ts, k.privateKey, k.publicKey))
            .build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}
```

**Teste obrigatório** com o exemplo da própria documentação (chave pública `1234`, privada `abcd`, `ts=1`):

```kotlin
@Test fun `hash bate com o exemplo da documentacao`() {
    assertEquals("ffd275c5130566a2916217b101f26150", marvelHash("1", "abcd", "1234"))
}
```

### 4.6 Logs e telemetria

- `HttpLoggingInterceptor` só em debug, com `redactQueryParams("apikey", "hash", "ts")` (ou interceptor próprio).
- **Performance Monitoring:** verifique se `apikey` e `hash` aparecem nas URLs das métricas. Se aparecerem, desative a coleta automática para o host `gateway.marvel.com` ou descarte a query.
- **Crashlytics:** nunca registrar chaves; use `setCustomKey` só com valores não sensíveis.

### 4.7 ADR-001 (a registrar em `docs/adr/0001-chave-marvel-no-app.md`)

> **Contexto:** sem backend, a chave privada precisa estar no app para assinar. **Decisão:** aceitar o risco, com chave dedicada, entrega e rotação pelo Remote Config, Keystore em repouso, monitoramento de cota e kill-switch. Quem consegue o `apiKey` e o `appId` do `google-services.json` também consegue consultar o Remote Config, então a chave não é protegida contra extração. **Consequência:** a chave é extraível por design; em caso de abuso, revogar e rotacionar. **Saída futura:** um proxy mínimo (Cloud Functions + Secret Manager + App Check) é o único caminho para uma barreira real. O `Repository` já isola a origem dos dados, então a troca não afeta a UI.

---

## 5. API Marvel — o que importa para o app

- **Base:** `https://gateway.marvel.com/v1/public/`
- **Lista:** `GET comics?offset={n}&limit={n}&orderBy=...&noVariants=true`
  - `limit` máximo: 100 (pedir mais gera 409). Sugestão: `limit = 30`.
  - `orderBy`: `focDate`, `onsaleDate`, `title`, `issueNumber`, `modified` (prefixo `-` para decrescente).
- **Detalhe:** `GET comics/{comicId}`
- **Envelope:** `code`, `status`, `copyright`, `attributionText`, `attributionHTML`, `etag`, `data { offset, limit, total, count, results[] }`.
- **Imagem:** `thumbnail { path, extension }`; monte `"$path/{variant}.$extension"` e **force HTTPS** (o `path` pode vir em `http`). Variante por tamanho de tela (ex.: `portrait_uncanny` 300×450 para cards; `detail` para a tela de detalhe). Imagens sem capa vêm como `image_not_available`.
- **Erros esperados:** 401 (chave/hash inválido), 403, 409 (parâmetro inválido), 429 (cota). Confirme a lista no portal.
- **Campos nulos ou vazios** são a regra, não a exceção: descrição, imagem, preço (`0.0`), datas (`-0001-11-30T00:00:00-0500` aparece como data ausente), criadores.
- **Cache HTTP:** habilite o cache do OkHttp (disco) e confirme se a API envia `ETag`/`Cache-Control`.

---

## 6. Arquitetura

### 6.1 Camadas

```
UI (Compose) ──▶ ViewModel (UiState/Events) ──▶ Repository (interface em domain/)
                                                    │
                              ┌─────────────────────┴────────────────────┐
                        Room (fonte de verdade)                 Retrofit (Marvel API)
                              ▲                                           │
                              └────────── RemoteMediator ◀────────────────┘
```

- **Unidirecional:** `UiState` imutável para baixo, eventos para cima.
- **DI:** Hilt (ou Koin). Interfaces em fronteiras (`MarvelKeyProvider`, `Clock`, `ComicsRepository`) para fakes nos testes.
- **Erros:** tipos, não strings nem exceções vazadas. `DomainError` e `Outcome` estão na seção 6.5; a conversão `Throwable → DomainError` acontece só na camada de dados.

### 6.2 Módulos — por gatilho, não por moda

> A primeira versão deste plano listava 13 módulos desde o início. Isso contradizia o princípio "código sem uso não entra" e o gatilho de modularização da análise do shortenLinks, então foi substituído.

**Início: `:app` único + `:benchmark`.** O Macrobenchmark exige módulo próprio; é o único módulo extra desde o dia 1. Dentro do `:app`, a lista e o detalhe compartilham o mesmo agregado e a mesma tabela, então `domain/` e `data/` são comuns e só a apresentação é por feature:

```
:app  (br.com.…marvelcomics)
 ├─ core/       common (Clock, dispatchers) · designsystem (tokens + componentes genéricos)
 │              · ui (componentes que conhecem o domínio) · network · database · keys
 ├─ domain/     modelos, DomainError, Outcome, interfaces de repositório   ← Kotlin puro
 ├─ data/       ComicsRepositoryImpl, ComicsRemoteMediator, DTOs, mappers
 └─ feature/    comics/ (Contract, Route, Screen, ViewModel) · detail/ (idem)
:benchmark      Macrobenchmark + Baseline Profile
```

| Gatilho | Evolução |
|---------|----------|
| Build lento ou necessidade de impor fronteiras por Gradle | Extrair `:core:network`, `:core:database`, `:core:designsystem`, `:core:testing`, `:feature:*` |
| Mais de uma pessoa ou várias features | `build-logic/` com convention plugins; `api`/`impl` por feature |

Desde o início: Version Catalog (`libs.versions.toml`), nenhuma dependência declarada sem uso (o shortenLinks declarava `logging-interceptor` sem usar) e `explicitApi()` assim que houver módulo de biblioteca.

### 6.3 Navegação

Navigation Compose com **rotas tipadas** (`@Serializable data object ComicList`, `@Serializable data class ComicDetail(val id: Int)`). O `ViewModel` de detalhe lê o `id` do `SavedStateHandle`.

### 6.4 Convenções de apresentação (UDF)

Vocabulário fixo, **um `XContract.kt` por tela**. Nada de quatro tipos diferentes chamados "Event".

| Termo | Papel |
|-------|-------|
| `UiState` | Estado imutável e único da tela |
| `UiAction` | Intenção do usuário (o que a UI envia ao ViewModel) |
| `UiEffect` | Efeito one-shot (mensagem, navegação) |
| `LoadState` (Paging) | Status assíncrono da lista; não é reinventado |

```kotlin
// ComicListContract.kt
@Immutable
data class ComicListUiState(val attribution: String? = null, val isOffline: Boolean = false)

sealed interface ComicListAction {
    data object RefreshRequested : ComicListAction
    data class ComicClicked(val id: Int) : ComicListAction
}

sealed interface ComicListEffect {
    data class ShowMessage(val message: UiText) : ComicListEffect
    data class OpenDetail(val id: Int) : ComicListEffect
}

// ComicListViewModel.kt (trecho)
val uiState: StateFlow<ComicListUiState>
val comics: Flow<PagingData<ComicUi>> =
    repository.comics().map { page -> page.map(ComicSummary::toUi) }.cachedIn(viewModelScope)
private val _effects = Channel<ComicListEffect>(Channel.BUFFERED)
val effects: Flow<ComicListEffect> = _effects.receiveAsFlow()   // não perde evento sem coletor
```

```kotlin
@Composable
fun ComicListRoute(viewModel: ComicListViewModel = hiltViewModel(), onOpenDetail: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val comics = viewModel.comics.collectAsLazyPagingItems()
    ObserveEffects(viewModel.effects) { effect -> /* repeatOnLifecycle(STARTED) */ }
    ComicListScreen(state = state, comics = comics, onAction = viewModel::onAction)
}

@Composable
fun ComicListScreen(
    state: ComicListUiState,
    comics: LazyPagingItems<ComicUi>,
    onAction: (ComicListAction) -> Unit,
    modifier: Modifier = Modifier,   // obrigatórios primeiro, modifier como 1º opcional
) { /* puro: sem ViewModel */ }
```

`PagingData` fica fora do `UiState` (não é um valor imutável comparável). Em previews e testes de tela use `flowOf(PagingData.from(lista)).collectAsLazyPagingItems()`.

**Regras**
1. A UI só chama `onAction`; o ViewModel é o único que escreve no estado.
2. Sem `!!`; sem `Throwable` no `UiState`; `UiState` pequeno e achatado.
3. O ViewModel **não produz texto final**: usa `UiText` (`StringResource | Dynamic`) e `strings.xml` desde a primeira tela.
4. Efeitos em `Channel(BUFFERED).receiveAsFlow()`, coletados com `repeatOnLifecycle(STARTED)`. **Efeito nunca nasce de `LaunchedEffect(state)`**, senão reexecuta após rotação (foi o que causou o copy duplicado no shortenLinks).
5. Uma ação assíncrona por vez: `Job` cancelável (`job?.cancel()`) ou `flatMapLatest`; nunca `launch { collect }` solto.
6. **Nenhuma regra de negócio no ViewModel:** decisões ficam no domínio/repositório; o ViewModel orquestra e mapeia para `UiState`.
7. Previews e testes de tela usam o `Screen` com estado fixo; **sem ViewModel real e sem Fake no source set `main`**.
8. Estado que precisa sobreviver à morte do processo usa `SavedStateHandle` (id do detalhe; texto de busca quando existir; a busca limpa o erro ao digitar).
9. Se surgir "copiar link", use um `ClipboardHandler` abstrato e copie exatamente o valor que a mensagem anuncia.

### 6.5 Domínio e erros

- **Domínio é Kotlin puro:** sem `android.*`, `retrofit2`, Compose nem classes de `presentation`. O contrato do repositório pode expor `PagingData` (`paging-common`, sem dependência de Android).
- **Modelos com nomes de negócio**, não com o formato do JSON: `ComicSummary(id, title, issueNumber, coverUrl)`, `ComicDetail(...)`.
- **Use case só existe se agrega regra** (validação, composição de repositórios, decisão). Pass-through não vira classe: o ViewModel chama o repositório. Na v1 não há use case.
- **Sem `Flow<LoadingEvent>` no domínio.** Operações únicas são `suspend fun` que devolvem `Outcome`; o estado de loading pertence ao ViewModel.

```kotlin
sealed interface DomainError {
    data object NoConnection : DomainError
    data object Timeout : DomainError
    data object NotFound : DomainError            // 404
    data object RateLimited : DomainError         // 429: cota diária
    data object InvalidKey : DomainError          // 401 mesmo após o refresh de chave
    data object KeysUnavailable : DomainError     // 1ª execução sem rede
    data class Server(val code: Int) : DomainError
    data class Unknown(val cause: Throwable? = null) : DomainError
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: DomainError) : Outcome<Nothing>
}

/** Transporta um [DomainError] por canais que só aceitam Throwable (ex.: `MediatorResult.Error`). */
class DomainErrorException(val error: DomainError) : Exception()
```

```kotlin
/** Converte qualquer Throwable em [DomainError]. Só a camada de dados chama isto. */
internal fun Throwable.toDomainError(): DomainError = when (this) {
    is MarvelKeysUnavailableException -> DomainError.KeysUnavailable   // antes de IOException
    is HttpException -> when (code()) {
        401 -> DomainError.InvalidKey
        404 -> DomainError.NotFound
        429 -> DomainError.RateLimited
        else -> DomainError.Server(code())
    }
    is SocketTimeoutException -> DomainError.Timeout
    is IOException -> DomainError.NoConnection                          // inclui UnknownHostException
    else -> DomainError.Unknown(this)
}

internal suspend inline fun <T> safeApiCall(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (e: CancellationException) {
    throw e                                   // nunca engolir cancelamento
} catch (e: Exception) {
    Outcome.Failure(e.toDomainError())
}
```

- **Paging só transporta `Throwable`:** o mediator devolve `MediatorResult.Error(DomainErrorException(error))` e a UI converte com `DomainError.toUiText()` (`strings.xml`). Nenhum `HttpException` ou `java.net.*` passa da camada de dados.
- **`MarvelKeysUnavailableException` estende `IOException`**, para o OkHttp tratá-la como falha de rede e não derrubar a thread do dispatcher.
- **Dispatchers injetados** (`@IoDispatcher`), **base URL por `BuildConfig`/qualifier** (uma única fonte) e **timeouts explícitos** (seção 10.5).
- **Mensagem por caminho:** cada `DomainError` tem texto próprio (sem internet, tempo esgotado, cota diária atingida, chave inválida, primeiro uso offline, erro do servidor). Texto técnico (`HTTP 500…`) nunca chega ao usuário.

### 6.6 Design system, tokens e imagens

**Posição:** vale ter, dimensionado ao app. Um design system "completo" de partida repetiria o erro dos 13 módulos: abstração sem uso. A regra é **tokens completos desde o dia 1** (custam pouco e evitam retrabalho) e **componentes só quando há reuso real ou ganho de centralização** (imagem, estados de tela, atribuição). O catálogo cresce por uso.

#### Tokens em três níveis

| Nível | O que é | Exemplo |
|-------|---------|---------|
| Primitivo | Valores brutos; **nunca usados direto nas telas** | `Palette.Ink900`, escala de espaço 4/8/12/16/24/32 dp |
| Semântico | Papel na UI; é o que as telas usam | `colorScheme.surface`, `MarvelTheme.spacing.medium`, `shapes.card` |
| De componente | Decisões de um componente | `ComicCardTokens`: proporção da capa 2:3, elevação, raio |

`MarvelTheme { }` fornece `ColorScheme`, `Typography` e `Shapes` (Material 3) e acrescenta `Spacing`, `Elevation` e `Motion` (durações) por `staticCompositionLocalOf`, lidos como `MarvelTheme.spacing.medium`. A fonte da verdade é o código Kotlin na v1; exportar tokens de um Figma (Tokens Studio) é opcional.

Decisões de marca a registrar: usar dynamic color ou paleta própria, e **evitar o vermelho/logotipo da Marvel** na identidade, para não sugerir afiliação (confirme nos termos).

#### Catálogo de componentes da v1

| Componente | Camada | Por que existe |
|------------|--------|----------------|
| `MarvelTheme` e tokens | `core/designsystem` | Fonte única de cor, espaço, forma, tipografia e movimento |
| `Shimmer` (`Modifier.shimmer()` + `ProvideShimmer`) | `core/designsystem` | **Uma** transição compartilhada, lida só no draw (a regra de 8.3 deixa de depender de disciplina) |
| `ComicCover` | `core/ui` | **Único ponto de carga de imagem** (abaixo) |
| `ComicCard` | `core/ui` | Usado na lista e em qualquer tela futura (favoritos, busca) |
| `LoadingSkeleton`, `EmptyState`, `ErrorState(message, onRetry)`, `OfflineBanner` | `core/ui` | Estados idênticos em todas as telas; `ErrorState` recebe o `UiText` vindo do `DomainError` |
| `AttributionFooter(text)` | `core/ui` | **Único lugar** que exibe o `attributionText` da Marvel |
| `InfoRow` (rótulo + valor) | `core/designsystem` | Detalhe: formato, páginas, data, preço (só se houver 2+ usos) |

`designsystem` não conhece o domínio; `ui` conhece `ComicUi`, `UiText` e `DomainError`. **Regra:** envolva um componente do Material só quando houver consistência, semântica ou token a acrescentar. Um `MarvelButton` que apenas repassa parâmetros não entra.

#### Pipeline de imagens (centralizado)

```
Room (thumbnailPath, thumbnailExt)
   └─► domain: CoverImage(path, extension)?          // null quando a API devolve image_not_available
         └─► CoverImage.url(variant)                 // Kotlin puro: força https, monta "$path/${variant}.$ext"
               └─► ComicCover(cover, title, size)    // escolhe a variante pela largura real em px
                     └─► ImageLoader único (DI)      // cache, OkHttp próprio, crossfade
```

- **`ImageVariant.forWidthPx(px)`** (Kotlin puro, testável): escolhe a menor variante `portrait_*` cuja largura seja ≥ `px` (`portrait_small` 50, `medium` 100, `xlarge` 150, `fantastic` 168, `incredible` 216, `uncanny` 300; `detail` para a tela de detalhe). Confirme as dimensões no portal.
- **`ComicCover(cover, title, size, modifier)`:** `CoverSize` (`Card`, `Detail`) define a largura pelo token; proporção fixa **2:3** (`aspectRatio`) para não haver salto de layout; pede a imagem com **tamanho explícito**; placeholder com cor de token; `fallback`/`error` com ícone para capa ausente; `contentDescription = title`. Recebe só valores estáveis, então é *skippable*.
- **`ImageLoader` único**, criado na DI: cache de memória (ponto de partida: 25% do heap disponível) e de disco (50 a 100 MB em `cacheDir`; confirme os termos da Marvel sobre armazenamento), `crossfade`, respeito aos cabeçalhos de cache (confirme o que a CDN envia). Confirme também a versão do Coil escolhida, porque a API do singleton difere entre 2.x e 3.x.
- **`OkHttpClient` próprio das imagens, sem `MarvelAuthInterceptor`.** As imagens vêm de outro host e **não podem carregar `apikey`/`hash`**. Como defesa em profundidade, o `MarvelAuthInterceptor` só assina requisições ao host da API e deixa as demais passarem intactas.
- Pré-carga da próxima página (`imageLoader.enqueue`): só depois de medir.
- As imagens vêm de outro host (CDN), então provavelmente não contam na cota de chamadas da API; confirme.

#### Governança (como impedir a duplicação)

1. **Imposição por ferramenta:** `ForbiddenImport` do detekt proíbe `coil*.compose.AsyncImage`, `rememberAsyncImagePainter` e similares **fora de `core/ui`**. Cor e `dp` literais fora do tema são barrados na revisão e, opcionalmente, por regra de lint customizada.
2. **Critério para um componente novo:** 2+ usos **ou** centralizar uma responsabilidade (imagem, atribuição, estado); API estável (parâmetros primitivos ou `@Immutable`); KDoc com dono do estado e o que o preview cobre; preview e teste.
3. **Galeria:** `@Preview` de cada componente nos estados relevantes (carregando, erro, sem capa, texto longo, fonte grande, escuro, tablet) e **screenshot tests** (Roborazzi ou Paparazzi). Mudar um token em um lugar mostra o impacto em todos.
4. **Testes específicos:** `ImageVariant.forWidthPx`; `CoverImage.url` (`http→https`, extensão, `image_not_available`); um teste com `MockWebServer` garantindo que **a requisição de imagem não leva `apikey` nem `hash`**.
5. **Extração:** vira o módulo `:core:designsystem` quando o gatilho da seção 6.2 for atingido.

---

## 7. Dados e offline

### 7.1 Room

- `comics(id PK, position, title, issueNumber, description, thumbnailPath, thumbnailExt, onsaleDate, priceCents, pageCount, format, creatorsJson, charactersJson, detailUrl, fetchedAt)`
- `remote_keys(comicId PK, nextOffset)`
- **`position`** = `offset + índice na página`. A lista ordena por `position`, não por data, o que evita saltos e duplicatas quando a Marvel reordena itens entre páginas (offset sobre dados mutáveis).
- Insert com `REPLACE` (upsert). Refresh limpa e insere dentro de **uma transação**.

### 7.2 `RemoteMediator` (esqueleto)

```kotlin
@OptIn(ExperimentalPagingApi::class)
internal class ComicsRemoteMediator(
    private val api: MarvelApi,
    private val db: AppDatabase,
    private val clock: Clock,
    private val ttl: Duration = 6.hours,   // ajuste conforme os termos da Marvel
) : RemoteMediator<Int, ComicEntity>() {

    /** Evita refresh (e gasto de cota) quando o cache ainda é recente. */
    override suspend fun initialize(): InitializeAction {
        val last = db.comicDao().lastFetchedAt() ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        return if (clock.millis() - last < ttl.inWholeMilliseconds)
            InitializeAction.SKIP_INITIAL_REFRESH else InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, ComicEntity>): MediatorResult {
        val offset = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> db.remoteKeysDao().lastNextOffset()
                ?: return MediatorResult.Success(endOfPaginationReached = true)
        }
        return try {
            val page = api.comics(offset = offset, limit = state.config.pageSize).data
            val items = page.results.mapIndexed { i, dto -> dto.toEntity(position = offset + i, now = clock.millis()) }
            val end = offset + page.count >= page.total
            db.withTransaction {
                if (loadType == LoadType.REFRESH) { db.comicDao().clear(); db.remoteKeysDao().clear() }
                db.comicDao().upsert(items)
                db.remoteKeysDao().upsert(items.map { RemoteKeyEntity(it.id, if (end) null else offset + page.count) })
            }
            MediatorResult.Success(endOfPaginationReached = end)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { MediatorResult.Error(DomainErrorException(e.toDomainError())) }
    }
}
```

`PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30, enablePlaceholders = false)`. Use `initialLoadSize = pageSize` para não gastar chamadas.

### 7.3 DTOs e mappers tolerantes

- Todos os campos opcionais com default (`val description: String? = null`, listas com `emptyList()`); `kotlinx.serialization` com `ignoreUnknownKeys = true` e `coerceInputValues = true`.
- O mapper converte: `http→https`, `image_not_available` → `null` (a UI mostra placeholder), data sentinela → `null`, preço `0.0` → `null`, HTML da descrição → texto limpo (em Kotlin puro, sem `android.text.Html`, para o mapper rodar em teste de JVM).
- **Modelo de UI separado:** `ComicUi` (`@Immutable`) com preço e data **já formatados** no mapper, não na composição.

### 7.4 Detalhe

Lê do Room por `id` primeiro (resposta imediata); se faltarem campos (a lista pode trazer menos que o detalhe) ou o item não existir, busca `comics/{id}` e atualiza o banco. Erro de rede com item em cache = mostra o cache e um aviso discreto.

---

## 8. Performance de Compose

### 8.1 Estabilidade e modelos

- UI recebe `ComicUi` (`@Immutable`); coleções como `ImmutableList` (kotlinx.collections.immutable) ou `@Immutable` wrapper.
- **Strong Skipping** já é padrão no compilador Compose do Kotlin 2.0.20+. Para tipos de outros módulos, use um **arquivo de stability config** (`stability_config.conf`) em vez de anotar tudo.
- Lambdas: o Strong Skipping memoriza lambdas, mas prefira passar **referências a funções do ViewModel** e eventos tipados.

### 8.2 Listas e grids

```kotlin
LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 150.dp), state = gridState) {
    items(
        count = comics.itemCount,
        key = comics.itemKey { it.id },
        contentType = comics.itemContentType { "comic" },
    ) { index ->
        comics[index]?.let { ComicCard(it, onClick = { onOpen(it.id) }) }
    }
}
```

- `key` estável e `contentType` garantem reuso correto e menos trabalho.
- Estado de `append` (loading/erro) como item final com `contentType` próprio.

### 8.3 Leitura de estado e escopo de recomposição

- **Adie a leitura de estado** para layout/draw: `Modifier.graphicsLayer { alpha = anim.value }`, `Modifier.offset { IntOffset(...) }`, `drawBehind {}`.
- `derivedStateOf` para estados derivados de scroll (botão "voltar ao topo": `firstVisibleItemIndex > 3`).
- **Passe só os campos necessários** para cada composable, não o `uiState` inteiro.
- Evite criar objetos na composição sem `remember`; evite `Modifier.composed` (use `Modifier.Node`/modifiers padrão).
- **Shimmer:** uma única `InfiniteTransition` compartilhada; leia o valor só no draw (`drawBehind`/`graphicsLayer`), não na composição.

### 8.4 Imagens

Toda carga de imagem passa pelo `ComicCover` e por um `ImageLoader` único (seção 6.6): tamanho explícito, variante `portrait_*` escolhida pela largura em px, proporção fixa 2:3, `crossfade` e caches de memória/disco configurados em um só lugar.

### 8.5 Medição (sempre em **release com R8**)

- Compose Compiler Reports/Metrics (`-P ...reportsDestination`) para ver o que é *skippable*.
- Contador de recomposição do **Layout Inspector** nas telas principais.
- **Macrobenchmark** (`FrameTimingMetric`, `StartupTimingMetric`) rolando a lista.
- **Baseline Profile** gerado e commitado; a startup e o scroll inicial melhoram.
- Debug distorce os resultados do Compose; não conclua nada dele.

**Meta inicial sugerida (ajuste após medir):** sem jank perceptível no scroll da lista em dispositivo de entrada; a primeira composição da lista não depende de rede quando há cache.

### 8.6 Regras adicionais de Compose

- **`collectAsStateWithLifecycle()`** em toda coleta de `StateFlow` (dependência `lifecycle-runtime-compose`); `collectAsLazyPagingItems()` para a lista. O `collectAsState()` comum continua coletando em segundo plano.
- **Tokens e componentes únicos:** tokens em três níveis, `ComicCover` como único ponto de carga de imagem e shimmer compartilhado (seção 6.6). Sem `dp` ou cor literal fora do tema.
- **Shimmer com cores do tema** (`MaterialTheme.colorScheme`), nunca `Color.LightGray`, para funcionar no escuro e com dynamic color.
- **Skeleton só quando há carga da lista** (refresh com lista vazia). Não use para outras ações.
- **Semântica:** card clicável com `role = Role.Button` e `onClickLabel`; títulos com `semantics { heading() }`; alvo de toque ≥ 48 dp.
- **Ícones:** evite `material-icons-extended` (artefato enorme); use vetores locais ou só os ícones do core.
- **Previews:** `@PreviewLightDark`, `@PreviewFontScale` e `@PreviewScreenSizes` nos estados relevantes (vazio, loading, erro, conteúdo, offline).
- **Ordem de parâmetros:** obrigatórios primeiro, depois `modifier`, depois os opcionais.

---

## 9. Firebase

| Produto | Configuração |
|---------|-------------|
| **Remote Config** | `marvel_keys` (JSON com `publicKey`, `privateKey`, `keyVersion`; **sem default no XML**), `min_supported_version`, `pinning_enabled`, `page_size`, `cache_ttl_hours`. **Primeira execução:** o fetch da chave é **bloqueante** para a primeira chamada à Marvel (com timeout e estado de erro com retry). **Demais execuções:** a chave vem do DataStore e o fetch roda em segundo plano, sem bloquear a UI. Intervalo mínimo: 0 em debug, ~12 h em release. |
| **Crashlytics** | Coleta **desligada em debug** (`setCrashlyticsCollectionEnabled`). Sem dados pessoais. `DomainError.Unknown` enviado como **não-fatal**. Upload do **mapping do R8** pelo plugin. |
| **Performance Monitoring** | Verifique `apikey`/`hash` nas URLs; desative a coleta para o host da Marvel se vazarem. Custom traces para "primeira página carregada" e "abrir detalhe". |
| **Ambientes** | `applicationIdSuffix ".debug"` (ou flavors `staging`/`prod`) com **apps Firebase separados** e um `google-services.json` por variante. |
| **App Check** | **Não** protege a API Marvel. Pode proteger o Remote Config se o console suportar; confirme na lista de produtos suportados antes de depender disso. |

**LGPD e Google Play:** declare a coleta (Crashlytics, Performance, identificadores) em **Data safety**, ofereça **opt-out** nas configurações do app e mantenha a política de privacidade pública.

---

## 10. Pipeline (GitHub Actions)

### 10.1 PR (`.github/workflows/pr.yml`)

Gates: Spotless/ktlint + detekt, lint, testes unitários, Kover com gate, `assembleDebug`, Dokka. Com cache do Gradle e `concurrency` cancelando execuções antigas.

```yaml
name: PR
on: { pull_request: { branches: [main] } }
concurrency: { group: pr-${{ github.ref }}, cancel-in-progress: true }
permissions: { contents: read }
jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@<SHA>          # fixe todas as actions por SHA
      - uses: actions/setup-java@<SHA>
        with: { distribution: temurin, java-version: 17 }
      - uses: gradle/actions/setup-gradle@<SHA>
      - name: google-services.json de PR (fictício)
        run: cp ci/google-services.fake.json app/google-services.json   # forks não recebem secrets
      - run: ./gradlew spotlessCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug dokkaHtml
```

> PRs de forks não recebem secrets. O job de PR usa um JSON fictício versionado em `ci/`, e **nunca** o real.

### 10.2 Release (tag `v*`, `.github/workflows/release.yml`)

```
tag vX.Y.Z
  ├─ test-unit            (mesmos gates do PR)
  ├─ test-instrumented    (android-emulator-runner ou Firebase Test Lab; com MockWebServer/fakes, NUNCA a Marvel real)
  └─ publish  needs: [test-unit, test-instrumented]   environment: production (aprovação manual)
        ├─ decodifica google-services.json (base64) e o keystore de upload
        ├─ bundleRelease  (AAB assinado, versionCode = github.run_number)
        ├─ upload do mapping R8 (Crashlytics)
        ├─ Firebase App Distribution (grupo de testers)
        └─ Google Play (trilha interna)
```

```yaml
permissions: { contents: read, id-token: write }   # id-token para OIDC / Workload Identity Federation
...
      - uses: google-github-actions/auth@<SHA>
        with:
          workload_identity_provider: ${{ secrets.WIF_PROVIDER }}
          service_account: ${{ secrets.WIF_SERVICE_ACCOUNT }}
      - name: Decodificar secrets
        run: |
          echo "${{ secrets.GOOGLE_SERVICES_JSON_B64 }}" | base64 -d > app/google-services.json
          echo "${{ secrets.UPLOAD_KEYSTORE_B64 }}"     | base64 -d > "$RUNNER_TEMP/upload.jks"
```

### 10.3 Lista de secrets

| Secret | Uso |
|--------|-----|
| `GOOGLE_SERVICES_JSON_B64` | `google-services.json` do app de release (base64). Não é segredo forte, mas deve ficar fora do repo. |
| `GOOGLE_SERVICES_JSON_STAGING_B64` | Idem, para staging/debug (se usar apps Firebase separados). |
| `UPLOAD_KEYSTORE_B64` | Keystore de upload do AAB. |
| `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` | Assinatura. |
| `WIF_PROVIDER`, `WIF_SERVICE_ACCOUNT` | **Workload Identity Federation (OIDC).** Prefira isto a uma chave JSON de service account de longa duração. |
| `FIREBASE_APP_ID` | Destino do App Distribution. |

A chave da Marvel **não entra no CI nem no repositório**. Ela vive só no Remote Config (um projeto/app Firebase por ambiente) e chega ao app pelo fluxo da seção 4.3, também em debug.

### 10.4 Segurança do workflow

- Actions fixadas por **SHA**; `permissions:` mínimas por job.
- Secrets de release protegidos por **environment** com aprovação.
- **Secret scanning** do GitHub ligado, ou gitleaks no PR.
- `google-services.json` real nunca é impresso em log (cuidado com `set -x`).
- Dependabot/Renovate para dependências e actions.

### 10.5 Build, release e manifesto (desde o commit 1)

O shortenLinks assinava o release com a keystore de debug, sem R8 e com logs em release. Aqui o release é cidadão de primeira classe:

- **Assinatura:** keystore de upload **fora do repositório** (variáveis de ambiente, `gradle.properties` do usuário ou secrets do CI). Nenhum release assinado com a keystore de debug.
- **R8 + `isShrinkResources`** ligados no release desde o início. O `assembleRelease` roda no CI de PR para pegar quebra de R8 cedo.
- **Regras de ofuscação:** `kotlinx.serialization`, Retrofit, Room e Coil trazem consumer rules; confirme se os DTOs precisam de `-keep` extra e **valide JSON → DTO no build minificado** (teste instrumentado na variante release/benchmark com `MockWebServer`). No shortenLinks, ligar o R8 quebraria os DTOs sem `@SerializedName`.
- **Manifesto:**
  - `android:allowBackup="false"` (ou `dataExtractionRules` excluindo o DataStore). O blob cifrado não é restaurável em outro aparelho, porque a chave do Keystore não migra; o fluxo de invalidação da seção 4.3 cobre o caso, mas evitar o ciclo é melhor.
  - `networkSecurityConfig` com `cleartextTrafficPermitted="false"`.
- **Rede:** `connectTimeout`, `readTimeout` e `callTimeout` explícitos (ponto de partida: 10 s, 20 s e 30 s; ajuste com medição); base URL só em `BuildConfig`/qualifier; logging só em debug com redação de `apikey`/`hash`/`ts`. Sem interceptor que reconstrói a request à toa.
- **Higiene Gradle:** sem blocos duplicados, `compilerOptions` em vez de `kotlinOptions`, `allWarningsAsErrors` no Kotlin e `lint { abortOnError = true; checkReleaseBuilds = true }`.
- **Ferramentas de debug:** StrictMode e LeakCanary (`debugImplementation`), Compose Compiler Reports.
- **Repositório:** `.gitignore` completo (`.idea/` fora, salvo o compartilhável), sem PDFs ou arquivos de IA na raiz, README com comandos reais e **sem afirmação que não se sustenta com número** (nada de "cobertura robusta" sem o valor do Kover).

---

## 11. KDoc

**Regra:** documente toda API pública escrevendo o **porquê**, o **contrato**, a **thread**, os **erros** e as **nulidades**; repetir o nome da função não conta. No shortenLinks boa parte do KDoc só restava o nome.

```kotlin
/**
 * Fluxo paginado de quadrinhos para a tela inicial.
 *
 * O banco local é a fonte de verdade: a coleta emite imediatamente o que está em cache e o
 * [ComicsRemoteMediator] completa/atualiza em segundo plano. Isso mantém a lista usável offline
 * e protege a cota diária da API.
 *
 * Deve ser coletado em `viewModelScope` com `cachedIn(viewModelScope)`; coletar duas vezes
 * sem `cachedIn` refaz a carga.
 *
 * Erros de carga chegam em `LoadState` (nunca como exceção na coleta) já mapeados para [DomainError].
 */
public fun comicsPager(): Flow<PagingData<ComicSummary>>
```

Em **composables**, documente `@param`, **quem é o dono do estado** (hoisting) e **o que o preview cobre**.

**Imposição:**
- `detekt` com `UndocumentedPublicClass`, `UndocumentedPublicFunction`, `UndocumentedPublicProperty`.
- `explicitApi()` nos módulos de biblioteca.
- Dokka gerado e validado no CI (falha em link quebrado).

---

## 12. UX e acessibilidade

- **Estados:** loading (shimmer), vazio, erro com **retry**, banner offline, pull-to-refresh, e estado próprio para "chave indisponível no primeiro uso".
- **Adaptação:** `WindowSizeClass` e `GridCells.Adaptive` para tablets; layout de detalhe em duas colunas em telas largas.
- **A11y:** `contentDescription` nas capas (título da HQ), alvos de toque ≥ 48dp, contraste verificado, suporte a fonte grande.
- **Tema escuro, edge-to-edge, splash screen** (API oficial).
- **i18n pt-BR/en:** textos do app traduzidos; as descrições da Marvel vêm só em inglês (não traduzir automaticamente; avise isso na UI se necessário).
- **Atribuição Marvel** no rodapé da lista e do detalhe.

---

## 13. Testes

| Tipo | O que cobrir |
|------|-------------|
| **Unitário** | `marvelHash` (vetor da documentação), mappers (nulos, data sentinela, `image_not_available`, `http→https`), `KeystoreCipher` round-trip (instrumentado), relógio injetável. **`MarvelKeyProvider`** com Remote Config fake: 1ª execução (fetch bloqueante e gravação cifrada), execução com chave salva (sem rede), timeout/offline (devolve `null`), `keyVersion` nova (rotação), `forceRefresh` após 401, e blob inválido (apaga e refaz o fetch). |
| **Contrato** | `MockWebServer` com **fixtures de JSON reais** da Marvel (casos nulos incluídos); 401 → refresh de chave → retry único; 429 e 409. |
| **Paging** | `asSnapshot` para a lista; `RemoteMediator` com Room em memória (`REFRESH`, `APPEND`, fim da lista, erro de IO, `initialize()` com cache fresco/velho). |
| **UI** | Compose UI tests com `testTag` estável; **screenshot tests** (claro/escuro, fonte grande, tablet). |
| **Navegação** | Detalhe com `SavedStateHandle`. |
| **Performance** | Macrobenchmark em CI manual/noturno; regressão de Baseline Profile. |
| **Instrumentados no CI** | Sem rede real; o `MarvelKeyProvider` é substituído por fake. |

Gate de cobertura (Kover) começando em um patamar realista e subindo; foque em `data` e `feature`.

### 13.1 Regras de higiene dos testes

Vêm direto das falhas do shortenLinks:

1. **Todo teste tem asserção** (`assertEquals`, `verify`, Turbine) e espera a coroutine terminar (`advanceUntilIdle`). Um teste sem asserção sempre passa e o Kover não detecta; a barreira é a revisão.
2. **Uma única lib de mock: MockK.** Fake antes de mock.
3. **Sem teste comentado.** Apague ou use `@Ignore("motivo + issue")`.
4. **Sem código morto, nem de teste:** nada de módulo Hilt em `src/test` que ninguém usa, dependência de teste sem consumidor ou teste de classe que não está em produção.
5. **Teste de efeitos:** o ViewModel emite o `UiEffect` certo (Turbine em `effects`) e chama as dependências esperadas (`coVerify`).
6. **Cada erro tem caminho próprio:** teste de tela com mensagem diferente para `NoConnection`, `Timeout`, `RateLimited`, `InvalidKey`, `KeysUnavailable` e `Server`. Textos de erro compartilhados escondem o caminho real.
7. **Rotação com estado alterado e process death** (`ActivityScenario.recreate()` e `StateRestorationTester`): role a lista, abra um detalhe, recrie e confira o que foi preservado.
8. **Acessibilidade:** `enableAccessibilityChecks()` nos testes de tela.
9. **Seletores estáveis:** `testTag` e `stringResource`, nunca texto literal.
10. **Mappers e funções puras** (hash, HTML → texto, escolha da variante de imagem) em teste de JVM, sem Robolectric: por isso nada de `android.*` neles.
11. **Fakes num só lugar** (`testFixtures` ou `:core:testing`), sem duplicata em `main` e `androidTest`.
12. **Kover** com meta mínima por camada e o **número real** citado no README.

---

## 14. Ambientes e distribuição

- **Emuladores** não passam no Play Integrity (que aqui é só telemetria). Não bloqueie nada por isso.
- **App Distribution:** builds de lá provavelmente não são reconhecidos pelo Play (confirme); trate o sinal como informativo.
- **Debug:** a chave também vem do Remote Config, do app Firebase de staging (pode ser uma chave Marvel separada, de baixo valor).
- **Cotas:** confira as cotas do Remote Config/Firebase antes do release.
- **Versão mínima:** `min_supported_version` no Remote Config + tela de "atualize o app".

---

## 15. Rastreabilidade: shortenLinks → este plano

Fonte: `shortenlinks-analise-e-plano-novo-app.md` (análise do repositório `AdautoEC/shortenLinks`). **Ressalvas da própria análise:** o projeto não foi compilado nem testado, e os itens marcados 🔎 (por exemplo, o bug do clipboard E-6/E-7) são inferidos da leitura do código e **não foram confirmados**.

**Legenda:** ✅ coberto no plano · 🟡 coberto sem garantia automática (depende de revisão) · ⚪ não se aplica à v1, regra mantida para quando surgir. **Nada disto está implementado ainda**; "coberto" significa que há decisão, regra ou teste planejado.

### Arquitetura, domínio e erros (gravidade da análise: 🔴 Fraco / 🟡 Médio)

| Achado | Onde no plano | Status |
|--------|---------------|--------|
| Domínio importa `core.presentation` (A-2) | 6.5: domínio Kotlin puro | ✅ |
| Exceções Retrofit/rede chegam ao ViewModel (A-1) | 6.5: `DomainError`, `toDomainError()`, `safeApiCall` | ✅ |
| Mensagens e texto de UI no ViewModel (A-9) | 6.4 regra 3: `UiText` + `strings.xml` | ✅ |
| `GetOriginalUseCase` engolia `CancellationException` | 6.5: `safeApiCall` repropaga | ✅ |
| Use cases devolvendo `Flow<LoadingEvent>` | 6.5: `suspend` + `Outcome` | ✅ |
| Modelo de domínio com o formato da API; dois `Links` | 6.5 e 7.3: modelos de negócio + mappers | ✅ |
| Regra de negócio dentro do ViewModel (A-3) | 6.4 regra 6 e DoD | ✅ (o caso "encurtar × expandir" ⚪) |
| `Submittable` e três tipos de estado assíncrono (A-8) | 6.4: só `LoadState` e `UiState`; DoD sem código morto | ✅ |
| Sem dispatchers injetados | 6.5 | ✅ |

### Estado e efeitos

| Achado | Onde no plano | Status |
|--------|---------------|--------|
| `networkError` duplica `Error` (E-1) | 6.4 regra 2: estado único e achatado | ✅ |
| Quatro "Event" diferentes (E-10, A-6) | 6.4: `UiState`/`UiAction`/`UiEffect` | ✅ |
| Efeitos perdidos em `SharedFlow` sem buffer (E-5, A-7) | 6.4 regra 4: `Channel(BUFFERED)` | ✅ |
| Efeito disparado por `LaunchedEffect(state)` (E-6) | 6.4 regra 4 | ✅ |
| Clipboard copia valor diferente do anunciado (E-7) 🔎 | 6.4 regra 9 | ⚪ (sem clipboard na v1) |
| Sem controle de `Job` (E-4, A-10) | 6.4 regra 5; Paging cancela sozinho | ✅ |
| `OnUrlChange` não limpa o erro (E-2); sem `trim()` (E-8) | 6.4 regra 8 | ⚪ (sem busca na v1) |
| Duplicação entre os dois fluxos do ViewModel (E-3) | Lista e detalhe têm ViewModels separados | ⚪ |
| Input perdido em process death (E-9) | 6.4 regra 8; testes 13.1 item 7 | ✅ |

### UI Compose

| Achado | Onde no plano | Status |
|--------|---------------|--------|
| Sem `Route` × `Screen` (A-4) | 6.4 | ✅ |
| `collectAsState` em vez de lifecycle-aware (A-5, P-5) | 8.6 | ✅ |
| Sem `stringResource` / i18n | 6.4 regra 3; seção 12 | ✅ |
| Ordem de parâmetros, `!!` | 6.4 e 8.6 | ✅ |
| `dp` e cores literais; shimmer com `Color.LightGray` | 8.6 e 6.6: tokens em 3 níveis, shimmer do tema | 🟡 (literais só por revisão ou lint customizado; imports barrados pelo detekt) |
| Card clicável sem `role`/`onClickLabel` | 8.6 | ✅ |
| `ResultSection` sem uso; previews com ViewModel real em `main` | 6.4 regra 7; DoD | ✅ |
| Clipboard duplicado, sem abstração (A-13) | 6.4 regra 9 | ⚪ |
| Lógica de UI repetida em componentes (`ClipboardManager` por item, P-7); falta de catálogo | 6.6: componentes únicos, governança e galeria | ✅ |
| Comentários invertidos em `Color.kt` | Revisão de PR | 🟡 |

### Testes

| Achado | Onde no plano | Status |
|--------|---------------|--------|
| Teste sem asserção que sempre passa (T-1) | 13.1 item 1 | 🟡 (barreira é a revisão) |
| Sem teste de efeitos nem `verify` (T-2) | 13.1 item 5 | ✅ |
| Sem teste de mapper, use case e validador (T-3) | 13 e 13.1 item 10 | ✅ |
| `android.util.Patterns` impede teste em JVM (T-4) | 13.1 item 10; 7.3 (HTML em Kotlin puro) | ✅ |
| Duas libs de mock (T-5) | 13.1 item 2 | ✅ |
| Testes de código sem uso (T-6, T-10) | 13.1 item 4 | ✅ |
| Erro de validação × erro de API indistinguíveis (T-7) | 13.1 item 6 | ✅ |
| Rotação sem digitar nada (T-8); sem process death | 13.1 item 7 | ✅ |
| Testes comentados (T-9) | 13.1 item 3 | ✅ |
| Seletores por texto literal (T-11) | 13.1 item 9 | ✅ |
| Sem a11y, screenshot, process death (T-12) | 13 e 13.1 | ✅ |
| Sem medição de cobertura (T-13); README sem número (T-14) | 10.1 (Kover), 13.1 item 12, 10.5 | ✅ |

### Performance

| Achado | Onde no plano | Status |
|--------|---------------|--------|
| Shimmer relê a animação na composição; 10 transições (P-1) | 8.3: uma transição, leitura no draw | ✅ |
| `Modifier.composed` (P-2) | 8.3 | ✅ |
| R8 desligado; `icons-extended` (P-3) | 10.5; 8.6 | ✅ |
| Sem Baseline Profile, Macrobenchmark, StrictMode, LeakCanary (P-4) | 8.5; 10.5 | ✅ |
| `LazyColumn` sem `contentType` (P-6) | 8.2 | ✅ |
| Operações O(n) em lista em memória (P-8) | Room + Paging (7) | ✅ |
| Shimmer com semântica errada (P-9) | 8.6 | ✅ |

### Build, release e segurança

| Achado | Onde no plano | Status |
|--------|---------------|--------|
| Release com keystore de debug (S-1) | 10.5; 10.3 | ✅ |
| Sem minify (S-2); sem regras para serialização (S-6) | 10.5 | ✅ |
| Log de request em qualquer build (S-3) | 4.6; 10.5 | ✅ |
| `allowBackup`, sem `networkSecurityConfig` (S-4) | 10.5 | ✅ |
| Blocos duplicados, `kotlinOptions` (S-5) | 10.5 | ✅ |
| Sem detekt/ktlint/Kover/CI (S-7) | 10.1 e 10.2 | ✅ |
| `.idea/`, PDF e `GEMINI.md` na raiz (S-8) | 10.5 | ✅ |
| Gson sem `@SerializedName` (A-11) | 7.3: kotlinx.serialization | ✅ |
| Sem timeouts; base URL duplicada (A-12) | 10.5; 6.5 | ✅ |
| `logging-interceptor` declarado sem uso | 6.2: nenhuma dependência sem uso | ✅ |

### O projeto melhorou como um todo?

**Sim, no plano, e em todas as dimensões que a análise marcou como fraca ou média** (erros, domínio, estado e efeitos, release, testes, performance). Há três ressalvas:

1. **É um plano, não código.** Nenhum desses pontos foi compilado, medido ou testado. A melhora só se confirma na implementação, principalmente os itens 🟡.
2. **Há mais partes móveis do que no shortenLinks:** Paging + `RemoteMediator`, Remote Config na primeira execução, Keystore e R8 com Room/serialização. Cada uma tem modos de falha que o shortenLinks não tinha, e por isso há testes e regras específicas para elas.
3. **Um ponto piora por natureza:** o shortenLinks não tinha segredo; este app tem uma chave extraível por design (ADR-001). O plano reduz o dano, mas não elimina o risco sem backend.


---

## 16. Roadmap sugerido

**Fase 0 — Fundação**
Projeto, Version Catalog, `:app` + `:benchmark`, detekt/Spotless, CI de PR com `assembleRelease` (R8 ligado), manifesto e timeouts (10.5), `DomainError`/`Outcome`/`safeApiCall` (6.5), ADR-001, `marvelHash` com teste do vetor.

**Fase 1 — Dados**
Retrofit + DTOs tolerantes, `KeystoreCipher`, `MarvelKeyProvider` (Remote Config → Keystore → DataStore, com o fluxo de primeira execução), Room, `RemoteMediator`, testes de contrato e mediator.

**Fase 2 — UI**
Tokens e tema, `ComicCover` + `ImageLoader` único, componentes de estado e atribuição, galeria e screenshot tests dos componentes (seção 6.6); depois lista em grid com Paging, detalhe e navegação tipada.

**Fase 3 — Qualidade**
Crashlytics, Performance Monitoring (checar vazamento de `apikey`/`hash`), screenshot tests, Macrobenchmark e Baseline Profile, a11y, i18n.

**Fase 4 — Release**
Pipeline de release (WIF, environment, App Distribution, trilha interna), Data safety e opt-out, ícone/nome sem logo Marvel, checklist final.

**Pós-v1:** busca, filtros, favoritos, personagens/séries, widget.

---

## 17. Checklist de pronto (Definition of Done)

- [ ] Teste do vetor `1234/abcd/ts=1 → ffd275c5130566a2916217b101f26150` verde
- [ ] Nenhuma chave em repo, APK em texto claro, defaults do Remote Config ou logs
- [ ] Primeira execução sem rede mostra o estado de erro com retry; com rede, a primeira chamada espera a chave e funciona
- [ ] Segunda execução em modo avião abre o catálogo do cache, sem consultar o Remote Config
- [ ] Rotação testada: `keyVersion` nova substitui a chave salva sem lançar versão
- [ ] `apikey`/`hash` ausentes das métricas do Performance Monitoring
- [ ] Atribuição Marvel exibida; sem logo Marvel no ícone/nome
- [ ] Lista funciona offline após a primeira carga; `initialize()` evita refresh desnecessário
- [ ] Todos os campos nulos da API tratados (fixtures reais)
- [ ] KDoc em 100% da API pública; detekt e Dokka passando
- [ ] Compose Reports revisados; benchmark em release sem regressão
- [ ] CI de PR verde com JSON fictício; release com aprovação e WIF
- [ ] Data safety preenchido e opt-out disponível
- [ ] Kill-switch/`min_supported_version` testado
- [ ] Domínio sem `android.*`/`retrofit2`; presentation sem `retrofit2`/`java.net`; ViewModel sem texto final nem `Throwable`
- [ ] Estado lido com `collectAsStateWithLifecycle`; efeitos em `Channel`, sem perda e sem nascer de `LaunchedEffect(state)`
- [ ] Release assinado com keystore de upload, R8 e `shrinkResources` ligados, `allowBackup=false`, timeouts explícitos
- [ ] JSON → DTO validado em build minificado
- [ ] Todo teste novo com asserção; sem teste comentado, sem código morto, uma só lib de mock
- [ ] Sem `dp`/cor literal fora do tema; cards clicáveis com `role`
- [ ] README sem afirmação sem número
- [ ] Nenhum `AsyncImage` fora de `ComicCover`; um único `ImageLoader`; requisição de imagem sem `apikey`/`hash` (teste)
- [ ] Componente novo com 2+ usos ou responsabilidade centralizada, KDoc, preview e screenshot test

---

## 18. Pontos a confirmar (não assumidos como fatos)

1. Cota diária atual e termos de cache/armazenamento no portal developer.marvel.com.
2. Se a API envia `ETag`/`Cache-Control` utilizáveis pelo cache do OkHttp.
3. Se o Remote Config pode exigir App Check no seu console.
4. Status atual de `androidx.security:security-crypto` (este plano não depende dele).
5. Se builds do App Distribution são reconhecidos pelo Play (afeta só telemetria).
6. Versões mais recentes de Kotlin, Compose BOM, Paging, Room, Coil e AGP no momento do início (use o Version Catalog e confirme as estáveis).
7. Política de pinning de certificado: adotar ou não (ADR).
8. `minSdk`: decidir com base nos aparelhos-alvo (AES-GCM no AndroidKeyStore exige API 23+; StrongBox só a partir da API 28, com fallback já previsto no `KeystoreCipher`).
9. Dimensões reais das variantes de imagem (`portrait_*`, `detail`), cabeçalhos de cache da CDN e termos da Marvel sobre armazenar imagens em disco.
10. Versão do Coil (2.x ou 3.x) e, se for o caso, a regra de lint customizada para `dp`/cor literal.
11. Os itens 🔎 da análise do shortenLinks (por exemplo, o bug do clipboard) não foram confirmados em execução; confirme antes de tratá-los como bug real.
