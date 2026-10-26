# ADR 0006: Estratégia de Testes por Estado e Execução de UI na JVM

## Status
Aceito

## Contexto
A estratégia anterior de testes apresentava três problemas críticos:
1. **Acoplamento a Implementações:** Testes de repositório continham dezenas de `verify { dao.upsert(...) }` com mocks em cascata e `withTransaction` simulado, testando chamadas a métodos em vez do resultado persistido no banco de dados. Qualquer alteração em queries quebrava os testes sem que houvesse bug real.
2. **Lentidão e Bloqueio no CI:** Todos os testes de componentes Compose eram executados como testes instrumentados em emuladores remotos do Firebase Test Lab, estourando rapidamente as cotas gratuitas e bloqueando o fluxo de integração contínua (CD).
3. **Testes Tautológicos:** Testes que apenas comparavam constantes e tokens de cores consigo mesmos inflavam métricas de cobertura sem detectar regressões de negócio.

## Decisão
1. **Eliminação de Testes Tautológicos:** Remoção de testes vazios de constantes e annotations que geravam falsa sensação de segurança.
2. **Testes de Repositório Baseados em Estado:** Reescrever a suíte de persistência utilizando instâncias de Room SQLite em memória gerenciadas pelo Robolectric (`Room.inMemoryDatabaseBuilder`). Validar o estado final das tabelas após operações concorrentes, reconciliação e sincronização de outbox, eliminando mocks frágeis de DAOs.
3. **Módulo Central de Fakes (`:core:testing`):** Centralizar implementações de fakes previsíveis (`FakeMoviesRepository`, `FakeAuthRepository`, `FakeNetworkMonitor`, `MainDispatcherRule`) evitando duplicações com `mockk(relaxed = true)`.
4. **Testes de UI Compose na JVM com Robolectric:** Migrar os testes de tela e de componentes Compose para `src/test` nos módulos de feature, executando-os na JVM via Robolectric em segundos a cada Pull Request.
5. **Reserva do Firebase Test Lab:** Restringir a execução de testes em dispositivos físicos no Firebase Test Lab exclusivamente a pipelines de release ou acionamento manual controlado, garantindo que o estouro de cota não paralise o desenvolvimento contínuo.

## Consequências
- **Positivas:**
  - Redução drástica no tempo de execução do CI de Pull Requests (de ~20 minutos para < 3 minutos).
  - Testes de repositório robustos a refatorações internas de código SQL e DAOs.
  - Testes de acessibilidade, clique e estados de tela verificados automaticamente a cada commit antes do merge.
  - Preservação da cota mensal do Firebase Test Lab para validação final de release em hardware real.
