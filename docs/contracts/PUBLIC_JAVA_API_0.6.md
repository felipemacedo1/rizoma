# Public Java API 0.6

Status: **EXPERIMENTAL antes de 1.0**. O pacote de adocao e seus contratos
podem evoluir em releases 0.x, sempre com changelog e testes de migracao. Nao ha
promessa de compatibilidade binaria antes de 1.0.

A raiz publica e `io.github.felipemacedo1.rizoma`. O namespace curto
`io.github.rizoma` nao e suportado: ele foi removido antes da primeira release
para alinhar packages ao `groupId` controlado.

## Artefato agregador

`io.github.felipemacedo1:rizoma:0.6.0-SNAPSHOT` depende dos modulos core, CSV,
Excel e pt-BR. Ele e o unico artefato que o caso comum precisa conhecer. Ainda
nao existe publicacao em Maven Central ou outro registry; o exemplo de
dependencia pressupoe `./mvnw install` local.

Os modulos especializados continuam disponiveis para composicao minima. O
modulo `rizoma-adoption-tests` nao e publicavel e compila quatro exemplos como
se fosse um projeto externo, dependendo diretamente apenas de `rizoma`.

## Tres niveis

### 1. Simple API

Pacote `io.github.felipemacedo1.rizoma.api`:

- `Rizoma.create()` fornece readers CSV/XLS/XLSX, detectores core/pt-BR,
  normalizador pt-BR, transformers/validators existentes, limites seguros,
  knowledge NoOp e layout registry NoOp;
- `ProcessRequest` e a entrada imutavel;
- `ProcessResult` resume status, rota, mapping, profiling, linhas e issues e
  oferece detalhes via `Optional`;
- `Sources` cria fontes reabriveis a partir de `Path`, `byte[]` ou
  `InputStream`.

`process()` nunca confirma sugestoes. Sem plano/template confirmado, executa
analise e retorna `REVIEW_REQUIRED`. Com `MappingPlan`, valida fingerprints e
vai diretamente ao dry run. Com template/registry, reconhece o layout e usa
FAST/ADAPTIVE somente quando as guardas 0.5 permitem; fallback FULL volta para
analise e review.

### 2. Workflow API

A fachada delega aos contratos detalhados sem criar outro pipeline:

- `analyze(AnalysisRequest)`;
- `plan(...)` e `planProjected(...)`;
- `configurePlan(...)`;
- `createLayoutTemplate(...)`;
- `recognizeLayout(LayoutRecognitionRequest)`;
- `dryRun(DryRunRequest)`.

`AnalysisResult`, `MappingPlan`, `LayoutTemplate` e `DryRunResult` permanecem
contratos experimentais versionados. JSON 1.0--1.3 continua sob responsabilidade
do adaptador CLI; a fachada nao depende de Jackson.

### 3. Extension API

SPIs publicas deliberadas no core:

- `DataReader`/`Dataset`;
- `SemanticDetector`;
- `SimilarityMetric`;
- `ValueTransformer`;
- `Validator`;
- `MappingKnowledgeBase`;
- `LayoutRegistry`;
- `TabularSource`.

Extensions sao injetadas pelo builder. O consumidor simples nao as instancia.
Acumuladores de profiling, sketches, feature extraction, pipelines, avaliadores
de corpus, implementacoes de score e detalhes POI/CSV sao implementacao e nao
fazem parte da candidata a API estavel, mesmo quando alguma classe 0.x ainda e
publica por retrocompatibilidade.

## Status

| Status | Significado |
|---|---|
| `SUCCESS` | dry run confirmado, sem warnings ou erros de dado |
| `SUCCESS_WITH_WARNINGS` | dry run confirmado, sem invalidos, com warnings |
| `REVIEW_REQUIRED` | ha sugestoes/no-match, mas nenhuma confirmacao implicita |
| `INVALID` | plano executado; uma ou mais linhas/campos sao invalidos |
| `FAILED` | falha tecnica impediu o resultado |

`FAILED` contem somente codigo e mensagem segura. A Workflow API traduz
`EngineException` para a hierarquia pequena `RizomaException`:
`InvalidSourceException`, `InvalidSchemaException`,
`IncompatiblePlanException` e `ProcessingException`. Erros normais de dados
continuam no `ProcessResult`/`DryRunResult`, nao em excecoes.

## Fontes e memoria

- `Sources.from(Path)` nao copia o arquivo e e a opcao recomendada para volume;
- `Sources.from(byte[])` faz uma copia defensiva e aplica limite;
- `Sources.from(InputStream)` precisa materializar o stream em memoria porque o
  motor calcula fingerprint e reabre a origem. O limite default e 100 MiB, pode
  ser reduzido, e o caller continua responsavel por fechar o stream;
- nenhuma factory cria temporario oculto. O adaptador Excel pode usar seu spool
  limitado ja documentado para fontes nao-Path.

## Imutabilidade, reutilizacao e concorrencia

`ProcessRequest` e `ProcessResult` sao imutaveis. `Rizoma` nao guarda estado de
execucao e pode ser reutilizado sequencialmente. A versao 0.6 nao promete uso
concorrente geral: readers, detectors, transformers, validators, observers e
stores customizados podem ser stateful. Se um integrador compartilhar a mesma
fachada entre threads, todas as extensions injetadas precisam fornecer sua
propria garantia de thread-safety. `InMemoryMappingKnowledgeBase` e
`InMemoryLayoutRegistry` sincronizam suas operacoes, mas um workflow composto
nao e uma transacao.

`ProcessObserver` e sincrono, opcional e recebe apenas estagio, codigo e
metadados seguros; nao existe dependencia de SLF4J. Observer que lanca excecao
produz `OBSERVER_FAILED` sem expor a excecao do integrador.

## Ergonomia medida

O quickstart minimo usa cinco tipos Rizoma mais `java.nio.file.Path`, nenhuma
configuracao de registry/metric/scorer/profiler e uma chamada `process()`. A
declaracao de um schema de um campo e o processamento ocupam sete linhas Java
formatadas; o switch de tratamento e opcional e explicito. Isso privilegia
clareza, nao code golf.

## Java 8 legado

A biblioteca requer Java 21. Um sistema Java 8 pode executar a CLI como processo
externo, fornecer arquivo/schema e consumir JSON versionado. Isso nao exige REST
nem reduz o baseline do motor.
