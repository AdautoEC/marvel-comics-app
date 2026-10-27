# Cinema Hub — Plano definitivo de melhoria

> **Base:** repositório `AdautoEC/marvel-comics-app`, HEAD `a52870e` (26/10/2026).
> **Como foi feita a validação:** leitura completa do código, Detekt (CLI 1.23.7) rodado contra os baselines de cada módulo (0 achados novos; 88 IDs congelados), parse de sintaxe dos workflows e consulta às documentações oficiais de compatibilidade (Kotlin, Gradle, AGP).
> **O que NÃO foi feito:** o projeto não foi compilado e os testes não foram executados (não há Android SDK no ambiente de análise). Itens marcados com 🔎 vêm da leitura do código e precisam de confirmação rápida antes de virarem tarefa.
> **Esforço (estimativa para 1 pessoa, sem contar revisão):** ⚡ ≤ 1 h · **M** ≈ meio dia · **G** ≈ 1 dia ou mais.

---

## 1. Resumo executivo

O projeto evoluiu muito: arquitetura modular, `Route`/`Screen`, Paging 3 + Room com chaves remotas, outbox com WorkManager, segurança em camadas, 293 testes unitários (incluindo Compose na JVM), Detekt com baseline e CI com gates reais. O backlog anterior foi executado quase por inteiro.

O que resta se divide em quatro grupos:

1. **Um risco crítico de build:** o wrapper foi para Gradle 9.8.2, mas AGP (8.7.3) e Kotlin (2.0.21) estão fora das faixas documentadas para esse Gradle.
2. **Testes do código mais crítico ainda por interação:** o worker de sincronização e a autenticação são testados com DAOs mockados, o contrário do que já foi feito no repositório.
3. **Credibilidade e gates:** Kover sem limite mínimo, release notes com números fixos e README sem medições.
4. **Higiene e modernização:** sobras de nomes (`Marvel*`, `Watched*`), dependências de dez/2024 e um toolchain a atualizar de forma coordenada.

### Pacote mínimo (se o tempo for curto)
**Fase 0 + itens 1.1 a 1.3 + Fase 3.** Isso elimina o risco de build, fecha a lacuna de testes mais perigosa e deixa o repositório honesto e verificável.

---

## 2. Princípios do plano

1. **Um PR por tarefa, CI verde antes de mesclar.** Nada de misturar upgrade de dependência com mudança de comportamento.
2. **Primeiro a rede de segurança, depois a refatoração.** Testes (Fase 1) antes de renomear (Fase 2) e de atualizar o toolchain (Fase 5).
3. **Toda afirmação do README tem de ser reproduzível** (número gerado pelo CI, comando ou arquivo no repositório).
4. **Toolchain se atualiza em degraus coordenados**, nunca peça por peça.
5. **Decisão arquitetural registrada em ADR** (`docs/adr/`), curta: contexto, decisão, consequências.

---

## 3. Mapa das fases

| Fase | Objetivo | Depende de | Esforço total |
|---|---|---|---|
| **0** | Destravar o build | — | ⚡–M |
| **1** | Testes que pegam bugs de verdade | 0 | G |
| **2** | Higiene de nomes e código morto | 1 | M |
| **3** | Credibilidade, gates e README | 0 | M |
| **4** | Performance medida e prontidão de release | 0, 3 | M–G |
| **5** | Modernização coordenada do toolchain | 1, 3 | G |
| **6** | Evolução de produto (opcional) | — | variável |

Ordem recomendada: **0 → (1 ∥ 3) → 2 → 4 → 5 → 6**. As fases 1 e 3 podem andar em paralelo.

---

## Fase 0 — Destravar o build (crítico)

### 0.1 Confirmar se o build funciona hoje · ⚡
**Por quê.** O wrapper está em `gradle-9.8.2` (PR do Dependabot #30), com AGP `8.7.3` e Kotlin `2.0.21`. A documentação do Kotlin lista, para o KGP 2.0.20–2.0.21, Gradle 6.8.3–8.8 e AGP 7.1.3–8.5; a do Gradle diz testar o AGP de 9.0 em diante na linha atual; e o AGP 9.0 exige Gradle 9.0.0. A combinação atual não está em nenhuma faixa documentada.

**Como.**
1. Veja o resultado do GitHub Actions no commit `a52870e`.
2. Localmente: `./gradlew clean testDebugUnitTest assembleDebug assembleRelease`.

**Aceite.** Resultado registrado (verde/vermelho e mensagem de erro, se houver).

### 0.2 Voltar a uma combinação suportada · ⚡
**Decisão recomendada (conservadora):** voltar o wrapper para **Gradle 8.11.1** (estado anterior, que já compilava com AGP 8.7.x). A migração completa para Gradle 9 fica na Fase 5.

```bash
./gradlew wrapper --gradle-version 8.11.1
git diff gradle/wrapper/gradle-wrapper.properties   # conferir distributionUrl
```

**Aceite.** CI verde com o wrapper revertido.

### 0.3 Impedir que o Dependabot repita o problema · ⚡
No `.github/dependabot.yml`, adicione um `ignore` para saltos de versão maior do wrapper:
```yaml
    ignore:
      - dependency-name: "gradle-wrapper"      # confirme o nome na mensagem do PR #30
        update-types: ["version-update:semver-major"]
```
E mantenha AGP, Kotlin, KSP, Hilt e Compose no mesmo grupo (já existe `core-frameworks`) para que subam juntos.

**Aceite.** O próximo ciclo do Dependabot não abre PR de Gradle 9.

### 0.4 Registrar a matriz de compatibilidade · ⚡
Crie `docs/adr/0007-matriz-de-compatibilidade-do-toolchain.md` com a tabela vigente (Gradle, AGP, Kotlin, KSP, Hilt, Compose compiler, JDK) e os links das tabelas oficiais. Essa tabela passa a ser a referência de qualquer PR de dependência.

**Aceite.** ADR existente e citado no `dependabot.yml` (comentário).

---

## Fase 1 — Testes que pegam bugs de verdade

**Situação.** 293 testes unitários e 3 instrumentados. Já estão bons: repositório com Room em memória (0 `verify`), 4 arquivos de Compose na JVM, restauração de scroll real, `FormatPagingErrorTest`, migrações e DAOs com Room. Os pontos fracos: o worker e a autenticação por interação, 63 `relaxed = true` e `Fake*` usado só 2 vezes.

### 1.1 Reescrever `SyncOutboxWorkerTest` sobre Room em memória · G
**Problema.** O teste mocka os três DAOs, o `TmdbApi` e usa `mockkStatic("androidx.room.RoomDatabaseKt")` para anular `withTransaction`. Ele prova "chamou X", não que o banco terminou certo. É o componente mais sujeito a corrida do projeto.

**Como.**
- `Room.inMemoryDatabaseBuilder(...)` (Robolectric) + um `FakeTmdbApi` programável (respostas por chamada: sucesso, `IOException`, 429, 5xx, 404, 401).
- `TestListenableWorkerBuilder<SyncOutboxWorker>(context).setWorkerFactory(...)` para executar o worker de verdade.
- Asserte o **estado final** do banco e o `Result`:
```kotlin
@Test fun `mutação mais nova durante o push não é apagada`() = runTest {
    db.syncOutboxDao().upsert(outbox(movieId = 1, desired = true, version = 1))
    api.onMarkAsFavorite { db.syncOutboxDao().upsert(outbox(1, desired = false, version = 2)) } // usuário alterou no meio
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
    assertThat(db.syncOutboxDao().getAllPending().single().desired).isFalse()   // linha nova preservada
}
```
- Cenários mínimos: sucesso apaga só a versão enviada; 404 descarta; 5xx/429/`IOException` incrementam `attempts` e retornam `retry`; estouro de tentativas reverte o estado local e grava o evento de falha; 401 limpa a sessão e cancela o trabalho; sessão visitante não processa; drenagem em lotes.

**Aceite.** Nenhum `mockk` de DAO nem `mockkStatic` do Room no arquivo; os testes de corrida falham se o `deleteVersion` for trocado por um `delete` incondicional.

### 1.2 Auth e logout por estado · M
**Problema.** `AuthRepositoryTest` (19 testes) e `AuthRepositoryLogoutTest` usam 7 mocks `relaxed` cada.

**Como.** Reaproveite o Room em memória e um `FakeSessionStorage`/`FakeTmdbApi` para: token pendente persistido e consumido só em match; callback forjado não invalida o token legítimo; expiração de 10 min; login de conta diferente limpa dados locais; logout cancela o trabalho, tenta `DELETE session` e limpa sessão + outbox + tabelas.

**Aceite.** Os testes dizem "depois do logout, o banco está vazio e a sessão ausente", não "o DAO foi chamado".

### 1.3 Usar os fakes de `:core:testing` e reduzir `relaxed` · M
**Como.** Troque os mocks de repositório nos testes de ViewModel (`MovieListViewModel*`, `MovieDetailViewModel*`) por `FakeMoviesRepository`/`FakeAuthRepository`; troque `mockk<Context>(relaxed = true)` por `ApplicationProvider.getApplicationContext()` onde houver Robolectric.

**Aceite.** `grep -rc "relaxed = true" */src/test` cai pelo menos pela metade (meta a calibrar), e nenhum teste de ViewModel mocka repositório.

### 1.4 Cenários de UI que faltam · M
**Como (Robolectric + `createComposeRule`).**
- Erro no *append* com botão de tentar de novo.
- Digitar na busca dispara `SearchQueryChanged` e mostra o estado vazio de busca.
- Pull-to-refresh dispara a ação de refresh.
- Falha de sincronização aparece como snackbar (efeito do ViewModel).

**Aceite.** Cada cenário falha se o comportamento correspondente for removido.

### 1.5 Verificações de acessibilidade · ⚡
`composeRule.enableAccessibilityChecks()` em pelo menos o teste da lista e o do detalhe (tamanho de toque, contraste, `contentDescription`).

**Aceite.** Os dois testes passam com as verificações ligadas; violações viram tarefa.

### 1.6 `NavigationTest`: virar E2E ou manter o nome honesto · M (opcional)
Hoje usa um `NavHost` montado à mão, sem Hilt nem ViewModel. Opção A: manter e documentar como teste de navegação. Opção B: `@HiltAndroidTest` + `createAndroidComposeRule<MainActivity>()` + `@TestInstallIn` com os fakes do `:core:testing`, navegando lista → detalhe → voltar pelo `NavHost` real.

**Aceite.** Quebrar o argumento `movieId` no `NavHost` real faz o teste da opção B falhar.

### 1.7 Prova de utilidade por mutação manual · M
Quebre de propósito e confirme que **algum** teste falha em cada caso:
1. Inverter a condição "preservar item com mutação pendente" na reconciliação.
2. Trocar `desired` por `!desired` no worker.
3. Remover o `trim()` ou o filtro case-insensitive da busca.
4. Não limpar o outbox no logout.
5. Remover o `key` do `items(...)` do grid.

**Aceite.** 5/5 detectadas; as que escaparem viram testes novos. Registrar o resultado no ADR 0006.

---

## Fase 2 — Higiene de nomes e código morto

### 2.1 Sobras de `Marvel*` · M
**Problema.** Restam ocorrências em `TmdbApi.getMarvelMovies`, `Color.MarvelRed`, e nos nomes do Keystore (`KeystoreCipher`, `EncryptedKeyStorage`).

**Como.**
- Renomeie com o IDE: `getMarvelMovies` → `getStudioMovies`; `MarvelRed` → um nome de papel (`BrandRed`).
- ⚠️ **Não renomeie o alias do Keystore nem as chaves de armazenamento** se existir qualquer base instalada: isso invalidaria a chave criptografada e a sessão. Como o `applicationId` mudou para `com.adauto.cinemahub`, só faça se tiver certeza de que não há instalações anteriores; caso contrário, mantenha com um comentário explicando o porquê.

**Aceite.** `grep -ri marvel */src/main` só encontra o alias do Keystore (comentado) e, se for o caso, o texto de atribuição.

### 2.2 `Watched` → `Watchlist` · M
**Problema.** 24 ocorrências em DAOs, entidade, `MovieCard`, `AuthRepositoryImpl` e `strings.xml`.

**Como.** Rename em um commit só, sem mudar comportamento; se algum nome de **tabela** mudar, escreva a migration (v5→v6) e o schema `6.json` com teste.

**Aceite.** `grep -ri watched */src/main` vazio (exceto migração, se houver).

### 2.3 Código morto · ⚡
- A migração `marvel.db` → `cinemahub.db` em `DatabaseModule` provavelmente não tem público (o `applicationId` mudou). Remova, ou mantenha só se houver base instalada; em qualquer caso, não use `runCatching` silencioso.
- Rode o Detekt com `UnusedPrivateMember`/`UnusedImports` e apague o que aparecer.

**Aceite.** Sem classes, parâmetros ou efeitos sem consumidor.

---

## Fase 3 — Credibilidade, gates e README

> Esta fase tinha sido retirada do backlog anterior a seu pedido. Ela volta aqui porque são os pontos mais visíveis para um avaliador e custam pouco.

### 3.1 Kover como gate · ⚡–M
No `build.gradle.kts` raiz, dentro de `kover { reports { … } }`, adicione a regra de verificação com o valor **medido** (rode `koverXmlReport` uma vez e use o número real; confira a DSL da versão 0.9.x):
```kotlin
verify {
    rule { minBound(/* valor medido, ex.: 78 */) }   // linhas, em %
}
```
No PR, troque `koverXmlReport koverHtmlReport` por `koverVerify koverXmlReport koverHtmlReport`.

**Aceite.** Remover um teste relevante derruba o `koverVerify` no CI.

### 3.2 Release notes geradas, não digitadas · M
**Problema.** O `main-cd.yml` ainda escreve "218 tests / 0 code smells / 2.5 MB" no corpo da release.

**Como.** Um passo que calcula os valores a partir dos artefatos:
```bash
python3 - <<'EOF' > build/release-metrics.txt
import glob, xml.etree.ElementTree as ET, os
t = f = s = 0
for p in glob.glob('**/build/test-results/test*UnitTest/*.xml', recursive=True):
    r = ET.parse(p).getroot()
    t += int(r.get('tests')); f += int(r.get('failures')) + int(r.get('errors')); s += int(r.get('skipped'))
print(f"Testes unitários: {t} (falhas: {f}, ignorados: {s})")
r = ET.parse('build/reports/kover/report.xml').getroot()
c = next(x for x in r.findall('counter') if x.get('type') == 'LINE')
cov = 100 * int(c.get('covered')) / (int(c.get('covered')) + int(c.get('missed')))
print(f"Cobertura de linhas (Kover): {cov:.1f}%")
EOF
echo "APK: $(stat -c %s app/build/outputs/apk/release/*.apk | numfmt --to=iec)" >> build/release-metrics.txt
```
Use `body_path: build/release-metrics.txt` na publicação da release.

**Aceite.** Nenhum número fixo no workflow (`grep -E '218|0 code smells' .github/workflows` vazio).

### 3.3 README verificável · M
- Cada número tem fonte: cobertura (Kover, com o escopo "linhas, testes unitários, excluindo composables e DI"), testes (contagem do CI), Detekt ("0 novos achados; N entradas em baseline").
- Medições de performance só depois da Fase 4.
- Links relativos (já corrigidos); versão do Compose/BOM conferida com o `libs.versions.toml`.
- Seção "Limitações conhecidas" curta e honesta (chave extraível por definição, busca local, etc.).

**Aceite.** Todo número do README é reproduzível por um comando ou por um relatório do CI.

### 3.4 Provar que os gates falham (canário) · ⚡
Abra um PR **descartável** com uma violação deliberada de Detekt, uma queda de cobertura e um texto fixo em `src/main`; confirme que o CI fica vermelho para cada um. Feche sem mesclar.

### 3.5 Branch protection · ⚡
Ative *required status checks* (`verify` do PR) e exija PR para `main`. Sem isso, os gates não bloqueiam nada.

**Aceite.** Um PR com CI vermelho não pode ser mesclado.

---

## Fase 4 — Performance medida e prontidão de release

### 4.1 Medições reais · M
**Como.** Em aparelho real, build `benchmark`:
- `StartupBenchmark` (cold e warm), `ScrollBenchmark` com `FrameTimingMetric`.
- Gere/atualize `app/src/main/baseline-prof.txt` com o `BaselineProfileGenerator`.
- Compare com e sem o profile; guarde aparelho, versão do Android, mediana e p95.

**Aceite.** Tabela no README com os números medidos e como reproduzir. Nada de metas aspiracionais.

### 4.2 Compose Compiler Reports · M
Gere os relatórios (já configurados no `build.gradle.kts` raiz) e revise: composables de tela `restartable but not skippable`, parâmetros `unstable`, listas sem `ImmutableList`.

**Aceite.** Nenhuma composable de tela não-skippable por causa de tipos do próprio projeto.

### 4.3 Release de verdade · M
- Build `release` assinado com a keystore de produção e **R8 ativo**; abra o APK e confira que `mapping.txt` está só como artefato privado do CI.
- `StrictMode` e LeakCanary apenas em `debug`.
- 🔎 Confirme a exigência atual de `targetSdk` do Google Play para novos apps/atualizações em 2026. Se for 36, o AGP 8.7.x não serve (suporta até a API 35) e isso antecipa a Fase 5.

**Aceite.** `./gradlew assembleRelease` sem fallback de assinatura; app abre e navega com R8 ligado.

---

## Fase 5 — Modernização coordenada do toolchain

**Regra.** Cada degrau é um PR com CI verde e um ADR curto. Consulte, em cada degrau, as tabelas oficiais (Kotlin: *Configure a Gradle project*; Gradle: *Compatibility Matrix*; AGP: *Release notes*). Não suba Gradle antes de AGP/Kotlin suportarem.

| Degrau | Escopo | Observação |
|---|---|---|
| 5.1 | Kotlin 2.1.x + KSP correspondente + Hilt/Room compatíveis, mantendo Gradle ≤ faixa suportada pelo Kotlin escolhido | Kotlin 2.1.21 tem máximo suportado de Gradle 8.12.1 na tabela oficial |
| 5.2 | AGP 8.9–8.13 + Gradle exigido pelo AGP escolhido (ex.: AGP 8.13 exige Gradle 8.13) | Habilita `compileSdk/targetSdk` 36 |
| 5.3 | Compose BOM, Navigation, Lifecycle, Paging, Activity | Revisar APIs depreciadas (ex.: `ScrollableTabRow`) |
| 5.4 | Firebase BoM e Play Integrity | Rodar o fluxo de App Check/Remote Config |
| 5.5 | Coil 3 | Muda o pacote de import (`coil3.*`) |
| 5.6 | Gradle 9 + AGP 9 | AGP 9.0 exige Gradle 9.0.0 e tem suporte embutido a Kotlin (dependência de runtime do KGP 2.2.10); seguir o guia oficial de migração e revisar o plugin `kotlin.android` |
| 5.7 | Detekt, Kover, Spotless | Conferir compatibilidade com o Gradle/AGP escolhidos; `spotless` está declarado sem uso: aplicar ou remover |

**Aceite (cada degrau).** CI verde; Fase 1 e 3 sem regressão; nenhum aviso novo de depreciação ignorado sem registro.

Também aqui: adicionar `org.gradle.configuration-cache=true`, `org.gradle.caching=true` e `org.gradle.parallel=true` ao `gradle.properties`, validando cada flag com o build completo.

---

## Fase 6 — Evolução de produto (opcional, após as anteriores)

| Item | Como | Esforço |
|---|---|---|
| Busca remota | `/search/movie` com chave remota própria por consulta (`label = "search:<query>"`) e `RemoteMediator` por query | G |
| App Links | Trocar o esquema `cinemahub://` por links `https` verificados (`assetlinks.json`) no callback OAuth | M |
| Chave fora do cliente | Proxy fino na frente do TMDB (Cloud Functions/Run) com App Check, se o escopo permitir | G |
| Atribuição TMDB | Conferir nos termos se o logo também é exigido e exibir na tela de créditos | ⚡ |
| Play Store | Política de privacidade, formulário de Data safety (Crashlytics/Performance), opt-out de coleta | M |
| Internacionalização | `values-en` completo; revisar formatação de datas/números por `Locale` | M |
| Testes visuais | Screenshot tests (Roborazzi/Paparazzi) para lista, detalhe e estados | M |

---

## Riscos e mitigação

| Risco | Probabilidade | Mitigação |
|---|---|---|
| Build quebrado pelo Gradle 9 | Alta (fora das faixas documentadas) | Fase 0, ainda antes de qualquer outra coisa |
| Testes do worker continuarem frágeis a corridas | Média | Fase 1.1 e a mutação manual da 1.7 |
| Renomear o alias do Keystore invalidar sessões | Média, se houver instalações | Não renomear sem migração (2.1) |
| Atualização de toolchain quebrar APIs | Média | Degraus pequenos, um PR cada, com ADR |
| Gates configurados e não obrigatórios | Média | Fase 3.5 (branch protection) |
| Cota do Test Lab bloquear releases | Média | `override_test_lab` auditado e testes de UI na JVM no PR |

---

## Definição de pronto (todo PR)

- [ ] CI verde: `checkNoHardcodedStrings`, Detekt (`maxIssues: 0`), Lint, testes unitários, Kover
- [ ] Testes novos assertam **estado/comportamento**, não só chamadas
- [ ] Sem texto fixo novo em `src/main`; sem cor/espaçamento literal fora do tema
- [ ] Sem código morto (efeitos sem consumidor, parâmetros sem uso)
- [ ] README e ADRs atualizados se a decisão mudou
- [ ] Nenhum número novo no README sem fonte reproduzível

## Métricas de sucesso (verificáveis)

| Métrica | Hoje | Meta |
|---|---|---|
| Build limpo (`assembleRelease`) em `main` | Não confirmado 🔎 | Verde |
| Novos achados do Detekt | 0 | 0 |
| `relaxed = true` em testes | 63 | ≤ metade |
| Testes do worker/auth com Room em memória | 0 de 2 arquivos | 2 de 2 |
| Cobertura (linhas, unit) com gate | sem gate | gate = valor medido |
| Release notes com número digitado | 1 | 0 |
| Medições de performance no README | nenhuma | tabela reproduzível |
| Versões de dependências | dez/2024 | degraus 5.1–5.7 concluídos |

---

## Rastreabilidade (backlog anterior → plano)

| Backlog anterior | Estado | No plano |
|---|---|---|
| B1–B13 (Compose/UI) | ✅ | — |
| B14 (acessibilidade) | 🟡 | 1.5 |
| C1, C2 | ✅ | — |
| C3 (nomes) | 🟡 | 2.1, 2.2 |
| D1, D3, D5, D6 | ✅ | 1.4 (cenários restantes) |
| D2 | 🟡 | 1.1, 1.2 |
| D4 | 🟡 | 1.6 |
| D7 | 🟡 | 1.3 |
| D8 | ⏳ | 1.7 |
| E1 | 🟡 | 0.2–0.4 e Fase 5 |
| E2, E4 | ✅ | — |
| E3 | 🟡 | 4.1 |
| Kover como gate, release notes, canário, branch protection | ❌ | 3.1–3.5 |

---

*Documento gerado a partir da validação do HEAD `a52870e`. Os trechos de código não foram compilados.*
