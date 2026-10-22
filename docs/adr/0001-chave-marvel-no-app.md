# 0001. Chave Marvel armazenada e utilizada no app (sem backend próprio)

## Contexto
O aplicativo precisa consumir a API oficial da Marvel (developer.marvel.com), que exige autenticação por query parameters contendo `ts`, `apikey` e um hash MD5 derivado da chave privada. O escopo deliberado do projeto é um aplicativo client-only sem backend próprio.

## Decisão
Aceitar o risco inerente da chave viver no aplicativo (caminho B), estabelecendo defesa em profundidade:
1. Chave dedicada exclusivamente para este aplicativo com monitoramento de cota (limite 3.000 req/dia).
2. Entrega e rotação dinâmica via Firebase Remote Config protegido por Firebase App Check.
3. Cifragem em repouso no aparelho via `AndroidKeyStore` (AES-256-GCM com StrongBox e fallback).
4. Janela curta em memória: o interceptor decifra, computa o MD5 e descarta os bytes.
5. Minificação R8 e ofuscação habilitadas desde o primeiro commit.
6. Detecção de ambiente hostil e Play Integrity atuando como telemetria.
7. Kill-switch e política de versão mínima (`min_supported_version`).

## Consequências
A chave é tecnicamente extraível por um atacante com acesso root/Frida. Em caso de abuso ou vazamento de cota, a rotação é efetuada no Firebase Remote Config sem necessidade de lançar nova versão do app. Se no futuro for necessária proteção absoluta, um proxy mínimo (Cloud Functions + Secret Manager + App Check) substituirá a origem de dados sem alterar a UI.
