# ADR 0002: Sincronização Confiável Offline-First via Outbox Pattern e WorkManager

## Status
Aceito

## Contexto
O usuário pode favoritar ou adicionar filmes à lista de interesse (Watchlist) a qualquer momento, inclusive quando o dispositivo estiver sem conexão à internet ou com conectividade instável. Chamadas diretas à API HTTP TMDB a partir de ViewModels resultariam em perda de operações do usuário e inconsistência visual.

## Decisão
1. **Padrão Outbox:** Adotar o padrão transacional de Outbox (`SyncOutboxEntity`). Cada ação do usuário (adicionar/remover favorito ou watchlist) é gravada atomicamente na mesma transação SQLite do Room que atualiza o estado local (`FavoriteMovieEntity` / `WatchlistMovieEntity`).
2. **Ordenação FIFO Monotônica:** As operações gravadas na outbox possuem IDs auto-incrementais e carimbos de data/hora imutáveis, garantindo que o despachador execute as mutações na mesma ordem temporal pretendida pelo usuário.
3. **Agendamento com WorkManager:** O processamento em segundo plano é delegado ao `SyncOutboxWorker` via `WorkManager`, configurado com restrição `NetworkType.CONNECTED` e política de retentativa exponencial (`BackoffPolicy.EXPONENTIAL`).
4. **Reconciliação Aditiva:** Ao restabelecer a conectividade com uma conta logada, o sistema realiza a união aditiva ($Local \cup Remote$), sincronizando pendências locais com o TMDB sem sobrescrever alterações concorrentes.

## Consequências
- **Positivas:**
  - Resposta imediata na UI com latência percebida de 0ms (UI otimista sustentada por banco local).
  - Resiliência total a interrupções de processo, fechamento forçado do app ou perda temporária de rede.
  - Ausência de duplicidade ou inversão de ordens de mutação graças ao FIFO.
- **Negativas / Atenções:**
  - Exige monitoramento e expurgo periódico de itens com status `PROCESSED` na tabela outbox para evitar crescimento desnecessário do banco SQLite.
