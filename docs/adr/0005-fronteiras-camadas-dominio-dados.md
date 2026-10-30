# ADR 0005: Desacoplamento da Apresentação com Casos de Uso (Domain UseCases)

## Status
Aceito

## Contexto
Na arquitetura inicial, a camada de apresentação (`:feature:movies` e `:feature:detail`) dependia diretamente das interfaces de repositório em `:core:data`. Isso levava a regras de negócio repetidas nos ViewModels (como a verificação de sessão, conversão de modelos `MovieSummary` $\leftrightarrow$ `MovieDetail` e disparo de reconciliação de favoritos e watchlist).

## Decisão
1. **Introdução de Casos de Uso Focados:** Criar classes de casos de uso com responsabilidade única (`ToggleFavoriteUseCase` e `ToggleWatchlistUseCase`) no pacote `com.adauto.cinemahub.core.data.usecase`.
2. **Operadores de Invocação Polimórficos:** Os casos de uso expõem `operator fun invoke` aceitando tanto `MovieSummary` quanto `MovieDetail`, centralizando o mapeamento de entidades de apresentação e coordenando a persistência com o repositório.
3. **Injeção de Dependências:** Os ViewModels passam a injetar estritamente os casos de uso para mutações de regras de negócio, eliminando a dependência direta de escrita sobre o repositório geral.
4. **Fronteira Arquitetural Documentada:** Manter temporariamente os Casos de Uso no módulo `:core:data` para evitar a proliferação excessiva de submódulos Gradle pequenos neste estágio do projeto, formalizando que o acoplamento é restrito a contratos de domínio claros e testáveis.

5. **Critério para criar um Caso de Uso:** Criar um caso de uso apenas quando houver lógica além de repassar a chamada (conversão de modelos, composição de fontes, regras de negócio) ou reuso entre telas. Exemplos: `ToggleFavoriteUseCase`, `ToggleWatchlistUseCase` e `GetMoviesUseCase` (escolha da fonte por categoria/busca e aplicação das flags de favorito e watchlist).
6. **Repositório direto no ViewModel:** Operações que apenas repassam a chamada (`getMovieDetail`, `isFavorite`, `isInWatchlist`, `syncCloudWithLocal` e o `AuthRepository`) podem ser consumidas diretamente do repositório, sem caso de uso.
7. **Paging:** o `Pager`, o `RemoteMediator` e o cache em disco ficam no repositório; o `cachedIn(viewModelScope)` fica no ViewModel, pois depende do ciclo de vida da tela.

## Consequências
- **Positivas:**
  - ViewModels mais enxutos, focados unicamente em transformação de estado de tela (UDF) e manipulação de eventos de UI.
  - Testabilidade aprimorada: casos de uso são testados unitariamente de forma rápida e determinística sem dependências de ciclo de vida Android.
  - Reutilização consistente da mesma regra de favoritar/desfavoritar em múltiplos fluxos e telas.
- **Negativas:**
  - Introdução de uma camada intermediária adicional para operações de negócio simples.
