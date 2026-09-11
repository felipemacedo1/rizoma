# Decisoes arquiteturais

## 2026-09-10 - Nome Rizoma

O nome representa uma estrutura viva que parece simples na superficie e forma
conexoes complexas em profundidade.

## 2026-09-10 - Linguagem Java

Java e a linguagem definida para o produto. Na criacao do repositorio, a versao
ainda nao estava fechada e Java 21 ou superior era apenas a hipotese inicial.
Essa indefinicao foi substituida pela decisao de baseline registrada abaixo.

## 2026-09-10 - Autonomia confinada ao workspace

O Codex opera sem prompts rotineiros, mas permanece limitado ao workspace.
Decisoes irreversiveis, segredos e dados externos continuam protegidos.

## 2026-09-10 - Java 21 como baseline, CI 21 e 25

O projeto compilara com `--release 21`. Java 21 oferece o melhor equilibrio
entre recursos modernos finalizados, compatibilidade corporativa, ecossistema e
facilidade de contribuicao. Java 25 e o LTS mais novo e sera testado em CI. Uma
elevacao do baseline exige ADR e beneficio mensurado ou versao major.

## 2026-09-10 - Maven 3.9.x via Maven Wrapper

Maven foi escolhido por convencao, familiaridade corporativa e publicacao no
Maven Central. Maven 4 ainda nao e GA; o Wrapper fixara uma versao 3.9.x estavel.

## 2026-09-10 - Monolito modular com core independente

O produto nasce como biblioteca modular, nao como servico. O core nao depende
de Spring, POI, parser CSV, banco ou rede. CSV, Excel, locale pt-BR e CLI sao
adaptadores/modulos ao redor do core. Novos modulos so nascem com codigo util e
testes.

## 2026-09-10 - Dataset streaming e profiling limitado

`Dataset` sera cursor lazy e de passagem unica. Perfis usarao estruturas
limitadas e cada medida declarara se e exata, estimada, amostrada ou indisponivel.
O motor nao materializara datasets inteiros por conveniencia.

## 2026-09-10 - Score nao e probabilidade

Score, cobertura, margem, contradicoes e calibracao sao dimensoes separadas.
Antes de corpus rotulado, o valor sera chamado de confidence index e auto-map
ficara desligado por padrao.

## 2026-09-10 - Incrementos 0.1a e 0.1b

0.1a entrega um fluxo CSV completo e explicavel. 0.1b adiciona XLS/XLSX pelo
mesmo contrato; apenas a soma sera publicada como release 0.1. Isso concilia o
suporte minimo de formatos com uma primeira entrega vertical pequena.

## 2026-09-10 - Apache License 2.0 recomendada

Apache-2.0 foi escolhida por permissividade e concessao explicita de patentes.
A LICENSE existente ainda deve ser substituida antes do primeiro codigo.
