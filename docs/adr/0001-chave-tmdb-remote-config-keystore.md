# ADR 0001: Armazenamento e Distribuição da Chave TMDB via Remote Config e Keystore

## Status
Aceito

## Contexto
O aplicativo precisa se comunicar com a API do TMDB (The Movie Database) para carregar filmes, categorias e metadados. Incorporar chaves de API (`API_KEY` e `READ_ACCESS_TOKEN`) diretamente no código-fonte, strings de recursos ou constantes do BuildConfig expõe as credenciais a engenharia reversa trivial (`strings classes.dex` ou descompilação via Jadx).

## Decisão
1. **Distribuição Dinâmica:** Distribuir as credenciais criptografadas via Firebase Remote Config com parâmetros atualizáveis remotamente sem necessidade de novo deploy na loja.
2. **Criptografia em Repouso no Cliente:** Utilizar o Android Keystore Provider gerando uma chave criptográfica AES-256-GCM (`KeyGenParameterSpec`) armazenada no enclave de hardware (`TEE` ou `StrongBox`).
3. **Persistência Segura:** O payload criptografado é salvo localmente em `EncryptedKeyStorage` via DataStore, garantindo funcionamento contínuo mesmo quando o dispositivo estiver sem conexão à internet.
4. **Fallback Local:** Em ambiente de desenvolvimento local e CI, chaves injetadas via `local.properties` atuam como provedores de fallback sem commitar segredos ao controle de versão.

## Consequências
- **Positivas:**
  - Extração estática de strings do APK não revela as chaves.
  - Rotação rápida de credenciais na nuvem em caso de comprometimento.
  - Segurança em repouso garantida pelo hardware de segurança do dispositivo.
- **Limites e Mitigações:**
  - *Limite intrínseco do cliente móvel:* Qualquer aplicativo executando em um sistema Android com privilégios de `root` e depurador de memória acoplado (ex: Frida/Xposed) pode eventualmente inspecionar as chaves em tempo de execução na memória RAM.
  - Para mitigar esse vetor, complementa-se a estratégia com Firebase App Check e Play Integrity (ADR 0003).
