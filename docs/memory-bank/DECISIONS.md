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
A decisao foi executada em 2026-09-11 com o texto oficial e `NOTICE` proprio.

## 2026-09-11 - Contratos corretivos do 0.1a

CNPJ pode conter letras significativas e nao sera normalizado como somente
digitos. AnalysisResult registra fingerprints de origem, schema e configuracao
para permitir invalidar planos futuros. Jaro/Jaro-Winkler permanece planejado e
tera complexidade documentada conforme a implementacao real, incluindo pior
caso da busca em janela.

## 2026-09-11 - Contratos implementados no 0.1a

O schema JSON e o AnalysisResult usam `formatVersion` 1.0 e rejeitam
propriedades desconhecidas. Colunas usam identidade posicional `cN`. Um campo
destino aceita conjunto de tipos semanticos. Obrigatoriedade e preservada, mas
nao validada; exclusividade participa apenas da deteccao de conflitos.

Detectores recebem o valor bruto somente durante acumulacao e publicam apenas
agregados. As amostras sao sempre protegidas, independentemente do acerto do
detector. O fingerprint da origem e comparado antes e depois da analise.

Maven Wrapper foi fixado em 3.9.16. Dependencias diretas do 0.1a: Commons CSV
1.14.1, Commons IO 2.22.0, Jackson 2.21.6, Picocli 4.7.7 e JUnit 5.14.4. JaCoCo
0.8.15 aplica gates de 85% de linhas e 80% de branches no core.

Dice e Levenshtein sao um unico subscore lexical correlacionado. A
confiabilidade de evidencia de conteudo e `min(1, N/minEvidence) *
(1-ambiguous/N)`. Margem usa todos os candidatos elegiveis antes do top-K;
ausencia de evidencia causa abstencao; `AUTO_MAP` e falso no default do core.

## 2026-09-11 - Estrategia Excel do 0.1b

`rizoma-format-excel` usa Apache POI 5.5.1 sem acoplar o core ao POI. XLSX e
lido por StAX sobre a worksheet e passa antes por preflight ZIP com Commons
Compress 1.28.0. Fontes locais usam acesso aleatorio read-only; fontes nao
locais usam spool temporario limitado e removido. A tabela de shared strings do
POI ainda e materializada e esse risco e limitado por tamanho expandido.

XLS legado usa o user model HSSF com teto proprio default de 20 MiB. Adotar o
event model HSSF exigiria produtor concorrente ou spool intermediario para
oferecer o cursor sincrono do core; essa complexidade nao foi introduzida sem
evidencia de demanda. Formulas nunca sao avaliadas e usam politica `cached`,
`expression` ou `reject`.

O `AnalysisResult` passou a 1.1 para incluir atributos seguros de estrutura,
como indice da planilha e linha de inicio. O fingerprint de configuracao agora
inclui reader options e seed. `explain` continua lendo 1.0 e 1.1.
