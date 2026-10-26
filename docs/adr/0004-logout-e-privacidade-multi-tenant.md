# ADR 0004: Isolamento de Tenants e Expurgo de Dados no Logout

## Status
Aceito

## Contexto
Dispositivos móveis são frequentemente compartilhados entre membros de uma família ou emprestados a terceiros. Quando um usuário autenticado realiza logout no Cinema Hub, manter favoritos, listas privadas ou registros pendentes no banco de dados local SQLite expõe dados sensíveis ao próximo usuário ou ao modo visitante (Guest).

## Decisão
1. **Revogação de Sessão em Nuvem:** Ao acionar `logout()`, o aplicativo primeiro solicita à API do TMDB a revogação do token de sessão ativo via chamada remota `deleteSession()`.
2. **Expurgo Criptográfico:** A chave e o token de sessão armazenados sob criptografia AES-256-GCM via Keystore e DataStore são apagados imediatamente.
3. **Limpeza Completa do Banco Local:** Em uma transação atômica do Room, são truncadas as tabelas de dados privados do usuário: `favorite_movies`, `watched_movies` (watchlist) e `sync_outbox`.
4. **Isolamento de Estado:** O estado de sessão reativo é imediatamente redefinido para `SessionState.Guest`, forçando a recomposição instantânea de todos os seletores visuais e barras de usuário.

## Consequências
- **Positivas:**
  - Garantia estrita de privacidade multi-usuário (zero vazamento de histórico, favoritos ou pendências entre contas no mesmo dispositivo).
  - Conformidade com diretrizes de privacidade e proteção de dados (LGPD / GDPR).
- **Compensações / Trade-offs:**
  - Se o usuário deslogar enquanto ainda houver itens não sincronizados na outbox e sem conexão à rede, essas pendências não sincronizadas são descartadas para proteger a privacidade do tenant. O usuário é alertado caso tente deslogar offline.
