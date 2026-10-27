# ADR 0007: Matriz de Compatibilidade do Toolchain e Atualizações Coordenadas

## Status
Aceito

## Contexto
O ecossistema Android moderno possui uma forte interdependência entre suas ferramentas centrais de compilação: o Gradle Wrapper, o Android Gradle Plugin (AGP), o compilador Kotlin (KGP), o processador de anotações KSP, o plugin do Compose Compiler e o Dagger Hilt.

Atualizações automatizadas e pontuais dessas ferramentas — como a promoção do Gradle Wrapper de 8.11.1 para 9.8.2 de forma isolada pelo Dependabot — introduzem riscos operacionais críticos:
1. **Incompatibilidade Oficial entre Versões:** O AGP 8.7.x e o Kotlin 2.0.21 foram desenvolvidos e homologados para a linha Gradle 8.x (máximo documentado 8.11/8.12).
2. **Avisos de Depreciação e Risco de Quebra:** A execução sob o Gradle 9 emite avisos de descontinuação de APIs legadas que são removidas no Gradle 10, além de comportamentos de cache e plugins de terceiros que podem falhar de maneira silenciosa.
3. **Dependência Binária de Metadados de Kotlin:** Ferramentas como o KSP e extensões como Hilt Work compiladas com versões superiores de Kotlin (ex: Kotlin 2.2) não podem ser consumidas por projetos utilizando Kotlin 2.0.

## Decisão
1. **Manutenção de Combinação Homologada:** O Gradle Wrapper permanece estritamente na linha **8.11.1**, que oferece compatibilidade completa com o AGP 8.7.3 e Kotlin 2.0.21.
2. **Atualização Coordenada em Degraus:** Atualizações de toolchain nunca são feitas de forma isolada. Qualquer salto de versão maior deve ser realizado em conjunto (Kotlin + KSP + AGP + Gradle Wrapper).
3. **Bloqueio de Saltos Semver-Major no Dependabot:** Configurar o `.github/dependabot.yml` para ignorar saltos `semver-major` no `gradle-wrapper`.
4. **Matriz Vigente Homologada:**

| Componente | Versão Vigente | Referência / Documentação Oficial |
|---|---|---|
| **JDK** | 17 (Eclipse Temurin) | Baseline do AGP 8.x e Java bytecode 17 |
| **Gradle Wrapper** | 8.11.1 | [Gradle Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html) |
| **Android Gradle Plugin (AGP)** | 8.7.3 | [AGP Release Notes & Gradle Requirements](https://developer.android.com/build/releases/gradle-plugin#updating-gradle) |
| **Kotlin** | 2.0.21 | [Kotlin Gradle Plugin Compatibility](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin) |
| **Kotlin Compose Plugin** | 2.0.21 | [Compose to Kotlin Compatibility Map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) |
| **KSP** | 2.0.21-1.0.28 | [KSP Releases & Kotlin Mapping](https://github.com/google/ksp/releases) |
| **Dagger Hilt** | 2.52 | Compilador compatível com KSP 2.0.x |
| **Hilt Work** | 1.3.0 | Compilado contra Kotlin 2.0.x e WorkManager 2.9.x |
| **Android SDK** | Compile 35 / Min 26 | Limite superior suportado com estabilidade no AGP 8.7.x |

## Consequências
- **Positivas:**
  - Build reproduzível, previsível e sem avisos de depreciação de Gradle 10.
  - Dependabot impedido de abrir PRs arriscados de major versions do Gradle sem atualização correspondente do AGP.
  - Documentação transparente servindo de guia de conformidade para novos upgrades.
- **Negativas / Limitações:**
  - Requer intervenção manual planejada para futuras migrações para a API 36 (Fase 5 do plano).
