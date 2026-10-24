# Design: In-Tab Movie Search (Filtro de Pesquisa na Aba Ativa)

## 1. Intent & Why
Permitir que o usuário filtre rapidamente os filmes da aba ativa no momento (*Populares*, *Mais Votados*, *Favoritos* ou *Quero Assistir*), tornando a navegação mais ágil sem sair do contexto de sua seleção atual.

## 2. Goals, Non-Goals & Anti-Goals
- **Goals**:
  - Campo de busca responsivo e integrado ao design escuro (DarkBackground / MarvelRed / TextPrimary).
  - Filtro reativo caractere a caractere com latência imperceptível (0ms) e sem novas chamadas de rede desnecessárias.
  - Funcionamento 100% offline.
  - Botão de limpar busca (ícone de 'X') para retornar instantaneamente à lista completa.
- **Non-Goals**:
  - Busca global em todo o catálogo do TMDB (não pretendemos substituir a navegação por abas nem fazer chamadas adicionais ao endpoint `/search/movie`).
- **Anti-Goals**:
  - Evitar perda do estado de rolagem da lista ou quebra da paginação ao abrir/fechar a busca.

## 3. Constraints
- Kotlin 2.0 / Jetpack Compose Material 3.
- Arquitetura UDF (Unidirectional Data Flow) com StateFlow e Paging 3.
- Cobertura completa de testes unitários no ViewModel e Detekt sem code smells.

## 4. Approaches Considered
1. **Abordagem 1 (Escolhida)**: Filtro reativo em memória via `PagingData.filter` / StateFlow no `MovieListViewModel`. Instantâneo, zero I/O, zero chamadas de rede e sem migrações de banco.
2. **Abordagem 2 (Descartada)**: Consultas `LIKE` no SQLite/Room. Descartada para evitar I/O de disco a cada caractere e complexidade desnecessária nos DAOs locais.

## 5. Specification & Implementation Details
- **`MovieListContract.kt`**:
  - Em `MovieListUiState`: adicionar `val searchQuery: String = ""` e `val isSearchActive: Boolean = false`.
  - Em `MovieListAction`: adicionar `data class SearchQueryChanged(val query: String) : MovieListAction` e `data object ToggleSearchClicked : MovieListAction`.
- **`MovieListViewModel.kt`**:
  - `_searchQuery = MutableStateFlow("")`.
  - Combina o fluxo de `movies` com `_searchQuery`: se `query.isBlank()`, mantém a lista original; se preenchida, aplica `pagingData.filter { it.title.contains(query, ignoreCase = true) }`.
  - Quando o usuário troca de aba (`CategorySelected`), limpa a busca automaticamente para evitar filtros órfãos.
- **`MovieListScreen.kt`**:
  - No `TopAppBar`, adicionar ícone de pesquisa (Lupa). Ao clicar, expande uma barra de busca elegante com campo de texto e botão para limpar/fechar.
  - Se a busca não encontrar resultados, exibe mensagem contextual: *"Nenhum filme encontrado para '<query>' nesta aba"*.
