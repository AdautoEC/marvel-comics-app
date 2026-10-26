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

## Consequências
- **Positivas:**
  - ViewModels mais enxutos, focados unicamente em transformação de estado de tela (UDF) e manipulação de eventos de UI.
  - Testabilidade aprimorada: casos de uso são testados unitariamente de forma rápida e determinística sem dependências de ciclo de vida Android.
  - Reutilização consistente da mesma regra de favoritar/desfavoritar em múltiplos fluxos e telas.
- **Negativas:**
  - Introdução de uma camada intermediária adicional para operações de negócio simples.
