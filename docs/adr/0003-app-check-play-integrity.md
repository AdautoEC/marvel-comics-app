# ADR 0003: Proteção de Endpoints e Recursos via Firebase App Check e Play Integrity

## Status
Aceito

## Contexto
Aplicativos que consomem serviços de nuvem e APIs externas de terceiros enfrentam riscos de tráfego abusivo gerado por bots, emuladores não autorizados, scrapers e clientes modificados que tentam esgotar cotas de chamadas ou extrair dados em massa.

## Decisão
1. **Ativação do Firebase App Check:** Integrar o SDK do Firebase App Check para atestar a autenticidade do aplicativo e do ambiente operacional perante os serviços de retaguarda.
2. **Provedor de Produção (Play Integrity):** Em builds de `release`, utilizar o `PlayIntegrityAppCheckProviderFactory`, que interroga os serviços do Google Play para comprovar que o binário instalado é legítimo, não foi adulterado e foi distribuído oficialmente pela Google Play Store.
3. **Provedor de Desenvolvimento e CI (Debug):** Em compilações de `debug` e testes automatizados, utilizar o `DebugAppCheckProviderFactory` com tokens efêmeros de depuração cadastrados no Firebase Console.
4. **Degradação Graciosa:** Na ausência temporária de sinal ou falha transitória do provedor, o aplicativo não bloqueia a navegação de dados já persistidos localmente no Room SQLite.

## Consequências
- **Positivas:**
  - Bloqueio eficaz de clientes clonados, ferramentas de automação maliciosas e scripts automatizados de raspagem.
  - Mitigação de negação de serviço e controle de custo sobre cotas de API.
- **Negativas / Atenções:**
  - Dispositivos sem Google Play Services (ex: dispositivos na China ou emuladores AOSP puros) requerem estratégias de atenuação ou tokens de depuração específicos para testes.
